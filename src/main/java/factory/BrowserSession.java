package factory;

import java.nio.file.Path;
import java.util.Map;

import com.google.gson.JsonObject;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.CDPSession;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;
import com.microsoft.playwright.Video;
import utils.FrameworkConfig;

/**
 * One isolated browser for one test: its own Playwright, browser, context (fresh
 * cookies and storage) and page. Records a Playwright trace that is kept only
 * when the test fails. On an Android device the browser is the device's Chrome,
 * attached over the DevTools protocol (see {@link AndroidDevice}).
 */
public final class BrowserSession implements AutoCloseable {

    private final Platform platform;
    private final Playwright playwright;
    private final Browser browser;
    /** BrowserStack phone (the fork's AndroidDevice), or null on other platforms. */
    private final AutoCloseable device;
    private final BrowserContext context;
    private final Page page;

    public BrowserSession(Platform platform) {
        this(platform, null, FrameworkConfig.videoEnabled());
    }

    public BrowserSession(Platform platform, Path storageState, boolean recordVideo) {
        this(platform, storageState, recordVideo, platform.name());
    }

    /**
     * @param storageState saved logged-in state to start from (see {@link LoginSession}), or null for a fresh visitor
     * @param recordVideo  record this session (test sessions); helper sessions such as logging in are not recorded
     * @param sessionName  name shown on the BrowserStack dashboard (BrowserStack only)
     */
    public BrowserSession(Platform platform, Path storageState, boolean recordVideo, String sessionName) {
        this.platform = platform;
        if (platform.mode == Platform.Mode.BROWSERSTACK) {
            // Browsers run in BrowserStack's cloud: never download local browsers (with the fork's
            // Playwright version that would also delete the browsers cached for local runs)
            this.playwright = Playwright.create(new Playwright.CreateOptions().setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
        } else {
            this.playwright = Playwright.create();
        }
        if (platform.mode == Platform.Mode.BROWSERSTACK) {
            this.device = BrowserStack.connectAndroid(playwright, sessionName);
            this.browser = null;
            this.context = BrowserStack.launchChrome(device);
        } else if (platform.mode == Platform.Mode.ANDROID_DEVICE) {
            this.device = null;
            // Real Chrome on the device: attach to its single (default) profile and start each test with
            // the app's cookies and storage wiped, since a fresh context cannot be created there
            this.browser = playwright.chromium().connectOverCDP(AndroidDevice.chromeDevToolsUrl());
            this.context = browser.contexts().get(0);
            context.clearCookies();
        } else {
            this.device = null;
            this.browser = browserType().launch(new BrowserType.LaunchOptions().setHeadless(FrameworkConfig.headless()));
            Browser.NewContextOptions options = new Browser.NewContextOptions()
                    .setViewportSize(platform.viewportWidth, platform.viewportHeight)
                    .setDeviceScaleFactor(platform.deviceScaleFactor)
                    .setUserAgent(platform.userAgent)
                    .setIsMobile(true)
                    .setHasTouch(true)
                    .setLocale("en-IN")
                    .setTimezoneId("Asia/Kolkata");
            StaticFileCache.warmUp(browser, options, platform.name()); // before video is added: not part of any test
            if (storageState != null) {
                options.setStorageStatePath(storageState);
            }
            if (recordVideo) {
                // Recorded to a temporary name; close(videoFile) gives it the test's name
                options.setRecordVideoDir(FrameworkConfig.videoDir().resolve(".recording"))
                        .setRecordVideoSize(platform.viewportWidth, platform.viewportHeight);
            }
            this.context = browser.newContext(options);
        }
        context.setDefaultTimeout(FrameworkConfig.timeoutMs());
        context.tracing().start(new Tracing.StartOptions().setScreenshots(true).setSnapshots(true));
        if (platform.mode != Platform.Mode.BROWSERSTACK) {
            // A cloud phone downloads the app itself; routing its files through this machine would send them both ways
            StaticFileCache.install(context);
        }
        this.page = context.newPage();
        if (platform.mode == Platform.Mode.ANDROID_DEVICE) {
            clearAppStorage();
        }
    }

    /** Removes local storage, IndexedDB, cache storage and service workers the app left on the device. */
    private void clearAppStorage() {
        String origin = java.net.URI.create(FrameworkConfig.baseUrl()).resolve("/").toString().replaceAll("/$", "");
        CDPSession cdp = context.newCDPSession(page);
        JsonObject params = new JsonObject();
        params.addProperty("origin", origin);
        params.addProperty("storageTypes", "all");
        cdp.send("Storage.clearDataForOrigin", params);
        cdp.detach();
    }

    public Platform platform() {
        return platform;
    }

    public Page page() {
        return page;
    }

    public BrowserContext context() {
        return context;
    }

    public void screenshot(Path file) {
        page.screenshot(new Page.ScreenshotOptions().setPath(file).setFullPage(true));
    }

    /** Shows the test result on the BrowserStack dashboard; does nothing on other platforms. */
    public void markBrowserStackSession(boolean passed) {
        if (platform.mode == Platform.Mode.BROWSERSTACK) {
            BrowserStack.markSession(page, passed);
        }
    }

    /** Stops tracing; writes the trace to {@code traceFile} when non-null, discards it otherwise. */
    public void stopTrace(Path traceFile) {
        context.tracing().stop(traceFile == null ? null : new Tracing.StopOptions().setPath(traceFile));
    }

    @Override
    public void close() {
        close(null);
    }

    /**
     * Closes everything and, when the test was recorded, saves its video as
     * {@code videoFile} (the video is only complete once the page is closed).
     * Playwright itself is always closed, even if the browser is already gone.
     */
    public void close(Path videoFile) {
        Video video = platform.mode == Platform.Mode.EMULATED ? page.video() : null;
        try {
            if (platform.mode == Platform.Mode.ANDROID_DEVICE) {
                // Close only this test's tab and disconnect; Chrome keeps running for the next test
                page.close();
            } else {
                context.close();
            }
            if (video != null) {
                if (videoFile != null) {
                    video.saveAs(videoFile);
                }
                video.delete();
            }
            if (browser != null) {
                browser.close();
            }
            if (device != null) {
                device.close();
            }
        } catch (Exception e) {
            throw e instanceof RuntimeException runtime ? runtime : new IllegalStateException("Could not close the BrowserStack device", e);
        } finally {
            playwright.close();
        }
    }

    private BrowserType browserType() {
        return switch (platform.engine) {
            case CHROMIUM -> playwright.chromium();
            case WEBKIT -> playwright.webkit();
        };
    }
}
