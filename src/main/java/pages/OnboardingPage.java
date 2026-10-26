package pages;

import java.util.ArrayList;
import java.util.List;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.BoundingBox;
import com.microsoft.playwright.options.Clip;
import utils.FlutterApp;
import utils.StepLog;

/**
 * Onboarding ({@code #/onboarding}): photo grid, a carousel of four slides
 * ("Splash 1-4") that advances by itself about every 2 s and loops, and Get Started.
 */
public class OnboardingPage {

    public static final String ROUTE = "onboarding";

    /** Share of the screen height covered by the photo grid at the top. */
    private static final double GRID_HEIGHT_SHARE = 0.25;

    private final Page page;
    private final FlutterApp app;

    public OnboardingPage(Page page) {
        this.page = page;
        this.app = new FlutterApp(page);
    }

    public OnboardingPage open() {
        app.open(ROUTE);
        return this;
    }

    public Locator welcomeHeading() {
        return page.getByText("Welcome to Bima Sugam", new Page.GetByTextOptions().setExact(true));
    }

    public Locator getStartedButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Get Started"));
    }

    public LoginPage tapGetStarted() {
        StepLog.step("Tap Get Started");
        getStartedButton().click();
        return new LoginPage(page).waitUntilShown();
    }

    /** The slide whose caption is {@code caption} (each slide is an image labelled with its caption). */
    public Locator slide(String caption) {
        return page.getByRole(AriaRole.IMG, new Page.GetByRoleOptions().setName(caption).setExact(true));
    }

    /** Waits for the carousel to reach the slide with {@code caption}. */
    public OnboardingPage waitForSlide(String caption, double timeoutMs) {
        StepLog.step("Wait for slide \"" + caption + "\"");
        slide(caption).waitFor(new Locator.WaitForOptions().setTimeout(timeoutMs));
        return this;
    }

    /** Captions of the slides on screen right now (two while the carousel is moving). */
    public List<String> visibleSlideCaptions(List<String> knownCaptions) {
        List<String> visible = new ArrayList<>();
        for (String caption : knownCaptions) {
            if (slide(caption).count() > 0) {
                visible.add(caption);
            }
        }
        return visible;
    }

    /**
     * Watches the carousel for {@code durationMs} and returns the slides in the order
     * they appeared, one entry per slide change.
     */
    public List<String> recordSlideSequence(List<String> knownCaptions, int durationMs) {
        StepLog.step("Watch the carousel for " + durationMs / 1000 + " s");
        List<String> sequence = new ArrayList<>();
        long end = System.currentTimeMillis() + durationMs;
        while (System.currentTimeMillis() < end) {
            List<String> visible = visibleSlideCaptions(knownCaptions);
            if (visible.size() == 1 && (sequence.isEmpty() || !sequence.get(sequence.size() - 1).equals(visible.get(0)))) {
                sequence.add(visible.get(0));
            }
            page.waitForTimeout(250);
        }
        return sequence;
    }

    /** Screenshot of the photo grid at the top of the screen. */
    public byte[] gridScreenshot() {
        return topOfScreenScreenshot(page, GRID_HEIGHT_SHARE);
    }

    /** Taps Get Started {@code times} times as fast as possible, at the button's position on screen. */
    public void rapidTapGetStarted(int times) {
        BoundingBox box = getStartedButton().boundingBox();
        double x = box.x + box.width / 2;
        double y = box.y + box.height / 2;
        StepLog.step("Tap Get Started " + times + " times rapidly");
        for (int i = 0; i < times; i++) {
            page.mouse().click(x, y);
        }
    }

    static byte[] topOfScreenScreenshot(Page page, double heightShare) {
        var viewport = page.viewportSize();
        int width = viewport != null ? viewport.width : new FlutterApp(page).windowWidth();
        int height = viewport != null ? viewport.height : new FlutterApp(page).windowHeight();
        return page.screenshot(new Page.ScreenshotOptions().setClip(new Clip(0, 0, width, height * heightShare)));
    }
}
