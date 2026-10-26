package pages;

import java.util.Map;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;
import utils.FrameworkConfig;
import utils.StepLog;

/**
 * What the user sees and installs before the Flutter app takes over: the HTML
 * splash screen and the web app manifest.
 */
public class LaunchPage {

    private final Page page;

    public LaunchPage(Page page) {
        this.page = page;
    }

    /** Navigates and returns as soon as the HTML arrives, while the splash screen is still showing. */
    public LaunchPage openBeforeAppStarts() {
        StepLog.step("Open app and catch the launch splash screen");
        page.navigate(FrameworkConfig.baseUrl(), new Page.NavigateOptions()
                .setWaitUntil(WaitUntilState.COMMIT)
                .setTimeout(FrameworkConfig.appStartTimeoutMs()));
        return this;
    }

    /** Browser tab / app-switcher title right now. */
    public String documentTitle() {
        return page.title();
    }

    public Locator splashLogo() {
        return page.locator("img.splash-logo");
    }

    /** Waits until the splash logo image has actually downloaded and decoded. */
    public void waitForSplashLogoLoaded() {
        page.waitForFunction("() => { const img = document.querySelector('img.splash-logo'); return img && img.complete && img.naturalWidth > 0; }");
    }

    /** The parsed web app manifest the page links to. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> manifest() {
        return (Map<String, Object>) page.evaluate(
                "async () => (await fetch(document.querySelector('link[rel=manifest]').href)).json()");
    }

    /** Content of {@code <meta name=...>}, or null. */
    public String metaContent(String name) {
        return (String) page.evaluate("name => document.querySelector(`meta[name=\"${name}\"]`)?.getAttribute('content') ?? null", name);
    }
}
