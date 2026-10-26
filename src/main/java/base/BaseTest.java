package base;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.testng.ITestContext;
import org.testng.ITestResult;
import org.testng.Reporter;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import com.microsoft.playwright.Page;

import factory.BrowserSession;
import factory.LoginSession;
import factory.Platform;
import utils.FrameworkConfig;
import utils.PerformanceMetrics;
import utils.StepLog;

/**
 * Gives every test method a fresh browser for its platform, and on failure saves a
 * full-page screenshot plus a Playwright trace under {@code test-output/failures}.
 *
 * Platform comes from the {@code platform} parameter of the {@code <test>} in
 * the suite file (src/test/resources/suites), falling back to {@code platform} in config.properties (or -Dplatform).
 * Open a saved trace with:
 * {@code ./mvnw exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="show-trace <file>"}
 */
@Listeners(ReportListener.class)
public abstract class BaseTest {

    /** Group for test cases that cannot be automated; see {@link #nonAutomatable(String)}. */
    public static final String NON_AUTOMATABLE = "non-automatable";

    /** Group for test cases waiting for test data or information; see {@link #blocked(String)}. */
    public static final String BLOCKED = "blocked";

    /** Group for test cases that send a real OTP SMS to a test account; exclude with -DexcludedGroups=otp. */
    public static final String SENDS_OTP = "otp";

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    /*
     * Plain field, not a ThreadLocal: with a suite time-out TestNG runs each test
     * method on its own thread, separate from the @BeforeMethod thread. Tests in a
     * class run one at a time, so one field per test-class instance is safe.
     */
    private volatile BrowserSession session;
    private volatile String loginProblem;

    /** Result of the one-time pre-flight check: null when the site answers, otherwise why it does not. */
    private static String siteProblem;
    private static boolean siteChecked;

    /**
     * Requests the app once per run before the first browser test. If the site is
     * down or blocks this machine (the UAT firewall answers 403 to non-allowlisted
     * addresses), every test is skipped with that reason instead of each one waiting
     * for an app that cannot load, which would also keep hitting the firewall.
     */
    private static synchronized String siteProblem() {
        if (!siteChecked) {
            siteChecked = true;
            String url = FrameworkConfig.baseUrl();
            try {
                HttpResponse<Void> response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build()
                        .send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
                                .header("User-Agent", Platform.ANDROID_CHROME_EMULATION.userAgent).build(),
                                HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() >= 400) {
                    siteProblem = "Site not reachable from this machine: " + url + " answered HTTP " + response.statusCode()
                            + (response.statusCode() == 403 ? " (blocked by the site firewall - connect the VPN or get this IP allowlisted)" : "");
                }
            } catch (IOException e) {
                siteProblem = "Site not reachable from this machine: " + url + " (" + e + ")";
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                siteProblem = "Pre-flight check interrupted";
            }
        }
        return siteProblem;
    }

    @BeforeMethod(alwaysRun = true)
    public void startBrowser(ITestContext context, Method method) {
        Test test = method.getAnnotation(Test.class);
        if (test != null && List.of(test.groups()).contains(NON_AUTOMATABLE)) {
            return; // manual test case: no browser needed
        }
        String fromSuite = context.getCurrentXmlTest().getParameter("platform");
        Platform platform = fromSuite == null ? FrameworkConfig.defaultPlatform() : Platform.from(fromSuite);
        // A BrowserStack phone reaches the site over BrowserStack's network, so this machine's access says nothing about it
        if (platform.mode != Platform.Mode.BROWSERSTACK && siteProblem() != null) {
            return; // page() skips the test with the reason
        }
        PerformanceMetrics.reset();
        Path loggedInState = null;
        loginProblem = null;
        if (requiresLogin()) {
            try {
                loggedInState = LoginSession.storageState(platform);
            } catch (RuntimeException e) {
                loginProblem = "Could not log in for " + platform + ": " + firstLine(e);
                StepLog.step(loginProblem);
                return; // page() skips the test with the reason
            }
        }
        StepLog.step("Launching browser for " + platform + (FrameworkConfig.headless() ? " (headless)" : "")
                + (loggedInState != null ? " as a logged-in user" : ""));
        session = new BrowserSession(platform, loggedInState, FrameworkConfig.videoEnabled(),
                getClass().getSimpleName() + "." + method.getName());
        Reporter.log("Platform: " + platform);
    }

    /** Override to return true when every test in the class needs a logged-in user (one OTP login per platform). */
    protected boolean requiresLogin() {
        return false;
    }

    /**
     * Called by the report while this test's browser is still open (TestNG reports a
     * result before running @AfterMethod): adds a screenshot when the test failed and
     * the app's performance metrics.
     */
    void addResultDetailsToReport(ITestResult result) {
        BrowserSession current = session;
        if (current == null) {
            return;
        }
        if (result.getStatus() == ITestResult.FAILURE) {
            try {
                ReportListener.attachScreenshot(current.page().screenshot(new Page.ScreenshotOptions().setFullPage(true)), "Screen at failure");
            } catch (RuntimeException e) {
                StepLog.step("Could not take failure screenshot: " + e.getMessage().lines().findFirst().orElse(""));
            }
        }
        ReportListener.attachMetrics(PerformanceMetrics.collect(current.page()));
        if (FrameworkConfig.videoEnabled() && current.platform().mode == Platform.Mode.EMULATED) {
            ReportListener.attachVideo(videoFile(current, result));
        }
    }

    /** Where this test's video is saved: video dir / platform / TestClass.test.webm */
    private static Path videoFile(BrowserSession current, ITestResult result) {
        return FrameworkConfig.videoDir().resolve(current.platform().name())
                .resolve(result.getTestClass().getRealClass().getSimpleName() + "." + result.getMethod().getMethodName() + ".webm");
    }

    @AfterMethod(alwaysRun = true)
    public void stopBrowser(ITestResult result) {
        BrowserSession current = session;
        if (current == null) {
            return;
        }
        // Cleanup must never fail: a failed @AfterMethod makes TestNG skip the remaining tests.
        // The browser may already be gone (window closed by hand, crash), so each step is guarded.
        try {
            if (result.getStatus() != ITestResult.SKIP) {
                current.markBrowserStackSession(result.getStatus() == ITestResult.SUCCESS);
            }
        } catch (RuntimeException e) {
            StepLog.step("Cleanup: could not mark the BrowserStack session (" + firstLine(e) + ")");
        }
        try {
            if (result.getStatus() == ITestResult.FAILURE) {
                saveFailureArtifacts(current, result);
            } else {
                current.stopTrace(null);
            }
        } catch (RuntimeException e) {
            StepLog.step("Cleanup: could not save trace (" + firstLine(e) + ")");
        } finally {
            try {
                current.close(FrameworkConfig.videoEnabled() ? videoFile(current, result) : null);
            } catch (RuntimeException e) {
                StepLog.step("Cleanup: browser was already closed (" + firstLine(e) + ")");
            }
            session = null;
        }
    }

    /**
     * Marks a test case that cannot be automated: it is reported as SKIPPED with this
     * reason, so it stays visible next to its test case ID. Tag such tests with the
     * "non-automatable" group so a run can exclude them.
     */
    protected static void nonAutomatable(String reason) {
        throw new SkipException("NON-AUTOMATABLE (manual test): " + reason);
    }

    /**
     * Marks a test case that can be automated but is waiting for something (test
     * data, information from the team): reported as SKIPPED with this reason.
     */
    protected static void blocked(String reason) {
        throw new SkipException("BLOCKED: " + reason);
    }

    protected Page page() {
        BrowserSession current = session;
        if (current == null) {
            // loginProblem first: it is only ever set once the site check passed or was skipped (BrowserStack)
            String reason = loginProblem != null ? loginProblem : siteProblem() != null ? siteProblem() : "No browser session started for this test";
            throw new SkipException(reason);
        }
        return current.page();
    }

    protected Platform platform() {
        return session.platform();
    }

    private static String firstLine(Exception e) {
        return String.valueOf(e.getMessage()).lines().map(String::trim).filter(l -> !l.isEmpty() && !l.equals("Error {")).findFirst().orElse("");
    }

    private void saveFailureArtifacts(BrowserSession current, ITestResult result) {
        Path dir = FrameworkConfig.artifactsDir().resolve(current.platform().name());
        String name = result.getTestClass().getRealClass().getSimpleName() + "." + result.getMethod().getMethodName()
                + "-" + LocalDateTime.now().format(STAMP);
        Path screenshot = dir.resolve(name + ".png");
        Path trace = dir.resolve(name + "-trace.zip");
        try {
            Files.createDirectories(dir);
            current.screenshot(screenshot);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (RuntimeException e) {
            // The page may already be gone (e.g. browser crash); still try to keep the trace
            Reporter.log("Screenshot failed: " + e.getMessage());
        }
        current.stopTrace(trace);
        Reporter.log("Screenshot: <a href='" + screenshot.toAbsolutePath().toUri() + "'>" + screenshot + "</a>");
        Reporter.log("Trace: " + trace.toAbsolutePath());
    }
}
