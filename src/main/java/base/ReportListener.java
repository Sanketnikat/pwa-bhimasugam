package base;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;

import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.MediaEntityBuilder;
import com.aventstack.extentreports.markuputils.ExtentColor;
import com.aventstack.extentreports.markuputils.MarkupHelper;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;
import com.aventstack.extentreports.reporter.configuration.Theme;

import factory.Platform;
import utils.FrameworkConfig;
import utils.StepLog;

/**
 * Builds the HTML test report (test-output/report/PWA-Test-Report.html): one entry
 * per test case and platform with its steps, duration, result, failure screenshot
 * and the app's performance metrics, plus run details on the dashboard.
 * Registered on {@link BaseTest}, so it works from Maven and from Eclipse.
 */
public class ReportListener implements ITestListener {

    private static ExtentReports extent;
    private static Path reportDir;
    private static volatile ExtentTest current;
    /** True once a test runs on BrowserStack: the site is then not contacted from this machine. */
    private static volatile boolean browserStackRun;

    private static synchronized ExtentReports extent() {
        if (extent == null) {
            // One report per run (name + start time), so earlier reports are kept
            String configured = FrameworkConfig.get("report.file");
            Path file = Path.of(configured.replaceFirst("(\\.html)?$", "-" + FrameworkConfig.runStamp() + ".html"));
            reportDir = file.toAbsolutePath().getParent();
            ExtentSparkReporter spark = new ExtentSparkReporter(file.toString());
            spark.config().setDocumentTitle("Bima Sugam PWA - Test Report");
            spark.config().setReportName("Bima Sugam PWA automation");
            spark.config().setTheme(Theme.STANDARD);
            spark.config().setTimeStampFormat("dd MMM yyyy HH:mm:ss");
            extent = new ExtentReports();
            extent.attachReporter(spark);
            extent.setSystemInfo("Application URL", FrameworkConfig.baseUrl());
            extent.setSystemInfo("App version", browserStackRun ? "not checked from this machine (BrowserStack run)" : appVersion());
            extent.setSystemInfo("Headless browser", String.valueOf(FrameworkConfig.headless()));
            extent.setSystemInfo("Machine", System.getProperty("os.name") + " " + System.getProperty("os.version"));
            extent.setSystemInfo("Java", System.getProperty("java.version"));
            System.out.println("HTML report: " + file.toAbsolutePath());
        }
        return extent;
    }

    @Override
    public void onTestStart(ITestResult result) {
        String platform = platformOf(result);
        if (Platform.from(platform).mode == Platform.Mode.BROWSERSTACK) {
            browserStackRun = true; // before extent() is first created
        }
        String description = result.getMethod().getDescription();
        String name = description != null ? description : result.getMethod().getMethodName();
        ExtentTest test = extent().createTest(name + " [" + platform + "]");
        test.assignDevice(platform);
        test.assignCategory(Arrays.stream(result.getMethod().getGroups()).filter(g -> !g.equals(BaseTest.NON_AUTOMATABLE)).toArray(String[]::new));
        test.getModel().setStartTime(new Date(result.getStartMillis()));
        current = test;
        StepLog.startTest(platform + " " + result.getMethod().getMethodName(), test::info);
        StepLog.step("START " + name);
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        finish(result, () -> current.pass("Passed"));
    }

    @Override
    public void onTestFailure(ITestResult result) {
        finish(result, () -> current.fail(result.getThrowable()));
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        if (current == null) {
            onTestStart(result); // skipped before it started (e.g. setup could not run)
        }
        finish(result, () -> {
            String reason = result.getThrowable() != null ? result.getThrowable().getMessage() : "Skipped";
            current.skip(MarkupHelper.createLabel(reason, reason.startsWith("NON-AUTOMATABLE") ? ExtentColor.GREY : ExtentColor.ORANGE));
        });
    }

    @Override
    public void onFinish(ITestContext context) {
        extent().flush();
    }

    /** Adds the app's performance metrics to the current test's report entry. */
    static void attachMetrics(Map<String, Object> metrics) {
        ExtentTest test = current;
        if (test == null || metrics.isEmpty()) {
            return;
        }
        String[][] rows = metrics.entrySet().stream()
                .map(e -> new String[] { e.getKey(), e.getValue() == null ? "-" : String.valueOf(e.getValue()) })
                .toArray(String[][]::new);
        test.info(MarkupHelper.createLabel("Performance", ExtentColor.BLUE));
        test.info(MarkupHelper.createTable(rows));
        StepLog.step("Performance " + metrics);
    }

    /** Embeds a player for the test's video (saved when the test's browser closes) in the current report entry. */
    static void attachVideo(Path videoFile) {
        ExtentTest test = current;
        if (test == null || videoFile == null) {
            return;
        }
        String relative = reportDir.relativize(videoFile.toAbsolutePath()).toString().replace('\\', '/');
        test.info(MarkupHelper.createLabel("Video", ExtentColor.BLUE));
        test.info("<video src='" + relative + "' controls preload='none' width='320'></video><br><a href='" + relative + "'>" + videoFile + "</a>");
    }

    /** Embeds a screenshot (PNG bytes) in the current test's report entry. */
    static void attachScreenshot(byte[] png, String title) {
        ExtentTest test = current;
        if (test != null && png != null) {
            String base64 = java.util.Base64.getEncoder().encodeToString(png);
            test.info(title, MediaEntityBuilder.createScreenCaptureFromBase64String(base64, title).build());
        }
    }

    private void finish(ITestResult result, Runnable outcome) {
        if (current == null) {
            return;
        }
        if (result.getInstance() instanceof BaseTest test) {
            try {
                test.addResultDetailsToReport(result);
            } catch (RuntimeException e) {
                StepLog.step("Could not add result details: " + e.getMessage());
            }
        }
        outcome.run();
        current.getModel().setEndTime(new Date(result.getEndMillis()));
        StepLog.step("END " + statusName(result.getStatus()) + " in " + (result.getEndMillis() - result.getStartMillis()) / 1000.0 + " s");
        StepLog.endTest();
        current = null;
        extent().flush();
    }

    private static String statusName(int status) {
        return switch (status) {
            case ITestResult.SUCCESS -> "PASSED";
            case ITestResult.FAILURE -> "FAILED";
            case ITestResult.SKIP -> "SKIPPED";
            default -> "status " + status;
        };
    }

    private static String platformOf(ITestResult result) {
        String fromSuite = result.getTestContext().getCurrentXmlTest().getParameter("platform");
        return (fromSuite == null ? FrameworkConfig.defaultPlatform() : Platform.from(fromSuite)).name();
    }

    /** "2.0.3 (11)" from the app's version.json, or "unknown". */
    private static String appVersion() {
        try {
            String json = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
                    .send(HttpRequest.newBuilder(URI.create(FrameworkConfig.baseUrl() + "version.json")).timeout(Duration.ofSeconds(15))
                            .header("User-Agent", Platform.ANDROID_CHROME_EMULATION.userAgent).build(), HttpResponse.BodyHandlers.ofString())
                    .body();
            var m = java.util.regex.Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\".*\"build_number\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
            return m.find() ? m.group(1) + " (" + m.group(2) + ")" : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }
}
