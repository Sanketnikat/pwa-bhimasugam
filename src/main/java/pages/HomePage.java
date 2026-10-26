package pages;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Mouse;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.BoundingBox;
import com.microsoft.playwright.options.Clip;
import utils.FlutterApp;
import utils.StepLog;

/**
 * Dashboard Home tab of a logged-in user ({@code #/dashboard}). Labels match the
 * native app's accessibility labels (the same Flutter app), e.g. "Quick Actions",
 * "Your Insurance Policies", "CONSUMER FIRST", "About Our Products", and the
 * bottom tabs Home / My Policies / Resources / My Account.
 *
 * Some elements have no label (avatar, bell, carousel arrows) and are found by
 * position relative to a labelled neighbour.
 */
public class HomePage {

    public static final String ROUTE = "dashboard";

    public static final String TAB_HOME = "Home";
    public static final String TAB_MY_POLICIES = "My Policies";
    public static final String TAB_RESOURCES = "Resources";
    /** The Profile tab is labelled "My Account". */
    public static final String TAB_PROFILE = "My Account";

    private static final Pattern GREETING = Pattern.compile("^\\s*(Namaste|Hello)\\b.*");
    /** Duration label on an educational video thumbnail, e.g. "2:45". */
    private static final Pattern VIDEO_DURATION = Pattern.compile("^\\s*\\d{1,2}:\\d{2}\\s*$");

    private final Page page;
    private final FlutterApp app;

    public HomePage(Page page) {
        this.page = page;
        this.app = new FlutterApp(page);
    }

    public HomePage open() {
        app.open(ROUTE);
        return waitUntilShown();
    }

    public HomePage waitUntilShown() {
        greeting().first().waitFor();
        return this;
    }

    public String currentRoute() {
        return app.currentRoute();
    }

    // ------------------------------------------------------------ generic label lookup

    /** Element whose visible text or accessible label is exactly {@code text} (ignoring surrounding spaces). */
    public Locator label(String text) {
        Pattern exact = Pattern.compile("^\\s*" + Pattern.quote(text) + "\\s*$");
        return page.getByText(exact).or(page.locator("flt-semantics[aria-label='" + text + "']"));
    }

    /** Element whose text or label starts with {@code text} (UAT decorates some titles, e.g. "Quick Actions test"). */
    public Locator labelStartingWith(String text) {
        Pattern start = Pattern.compile("^\\s*" + Pattern.quote(text));
        return page.getByText(start).or(page.locator("flt-semantics[aria-label^='" + text + "']"));
    }

    /** Element whose text or label contains {@code text}. */
    public Locator labelContaining(String text) {
        return page.getByText(Pattern.compile(Pattern.quote(text))).or(page.locator("flt-semantics[aria-label*='" + text + "']"));
    }

    /** Scrolls the Home tab until {@code target} is on screen (Flutter only builds what is near the viewport). */
    public Locator scrollTo(Locator target) {
        for (int i = 0; i < 25 && target.count() == 0; i++) {
            page.mouse().wheel(0, 500);
            page.waitForTimeout(400);
        }
        target.first().scrollIntoViewIfNeeded();
        return target.first();
    }

    /** Screenshot of the area {@code element} covers right now. */
    public byte[] areaScreenshot(Locator element) {
        BoundingBox box = element.boundingBox();
        return page.screenshot(new Page.ScreenshotOptions().setClip(new Clip(box.x, box.y, box.width, box.height)));
    }

    /** Drags across {@code area} from right to left, like a finger swiping a carousel. */
    public void swipeLeftAcross(BoundingBox area) {
        StepLog.step("Swipe the carousel left");
        double y = area.y + area.height / 2;
        page.mouse().move(area.x + area.width * 0.8, y);
        page.mouse().down();
        page.mouse().move(area.x + area.width * 0.5, y, new Mouse.MoveOptions().setSteps(5));
        page.mouse().move(area.x + area.width * 0.1, y, new Mouse.MoveOptions().setSteps(10));
        page.mouse().up();
        page.waitForTimeout(800);
    }

    // ------------------------------------------------------------ header

    public Locator greeting() {
        return page.getByText(GREETING).or(page.locator("flt-semantics[aria-label^='Namaste'], flt-semantics[aria-label^='Hello']"));
    }

    /** The greeting as shown, e.g. "Namaste ..."; contains the account holder's name, so do not log it. */
    public String greetingText() {
        return accessibleText(greeting().first()).trim();
    }

    /** Unlabelled image to the left of the greeting in the header. */
    public Locator avatar() {
        BoundingBox greet = greeting().first().boundingBox();
        return nearest(page.locator("flt-semantics[role=img]"), b -> b.y < greet.y + greet.height && b.x + b.width <= greet.x + 2);
    }

    /** Unlabelled button at the right of the header (notification bell). */
    public Locator notificationBell() {
        BoundingBox greet = greeting().first().boundingBox();
        int width = app.windowWidth();
        return nearest(page.locator("flt-semantics[role=button]"), b -> Math.abs(b.y - greet.y) < 60 && b.x > width * 0.7);
    }

    public Locator notificationsScreen() {
        return label("Your Notifications");
    }

    // ------------------------------------------------------------ sections

    public Locator heroBanner() {
        return labelStartingWith("Start your insurance journey").or(labelContaining("start from where you left"));
    }

    /** "Explore plans" call to action on the hero banner (either capitalisation). */
    public Locator heroExplorePlansButton() {
        return label("Explore plans").or(label("Explore Plans"));
    }

    public Locator quickActions() {
        return labelStartingWith("Quick Actions");
    }

    /** "View All" links in page order: Quick Actions first, then Your Insurance Policies. */
    public Locator viewAll() {
        return label("View All");
    }

    /** Quick action cards read e.g. "Under Process / Bima Vahan / Resume Application". */
    public Locator quickActionCards() {
        return labelContaining("Resume Application");
    }

    public Locator buyInsurance() {
        return label("Buy Insurance");
    }

    public Locator yourInsurancePolicies() {
        return label("Your Insurance Policies");
    }

    /** Policy product tab ("Motor", "Health", "Term Life"), labelled e.g. "Motor / Tab 1 of 3". */
    public Locator policyTab(String name) {
        return page.locator("flt-semantics[role=tab]").filter(new Locator.FilterOptions().setHasText(Pattern.compile("^\\s*" + Pattern.quote(name))))
                .or(page.locator("flt-semantics[aria-label^='" + name + "'][aria-label*='Tab ']"));
    }

    /** Policy cards read "Motor / Product Name / ... / Policy Number / ... / Policy End Date / ...". */
    public Locator policyCards() {
        return labelContaining("Policy Number");
    }

    /** Taps a policy product tab and gives the list time to update. */
    public void selectPolicyTab(String name) {
        policyTab(name).first().click();
        page.waitForTimeout(1000);
    }

    /** A policy card's text on one line, e.g. "Motor Product Name ... Policy Number ...". */
    public String policyCardText(Locator card) {
        return accessibleText(card).replaceAll("\\s+", " ").trim();
    }

    public Locator viewDetailsButton() {
        return label("View Details");
    }

    public Locator downloadPolicyButton() {
        return label("Download Policy");
    }

    public Locator emptyPolicies() {
        return labelStartingWith("You don");
    }

    public Locator brandBanner(String title) {
        return label(title);
    }

    /** "Know More" on a brand banner. */
    public Locator knowMoreButton() {
        return label("Know More");
    }

    public Locator aboutOurProducts() {
        return label("About Our Products");
    }

    public Locator aboutProductsNextArrow() {
        return aboutProductsArrow(false);
    }

    public Locator aboutProductsPrevArrow() {
        return aboutProductsArrow(true);
    }

    /**
     * Which of {@code products} are inside the screen width right now.
     *
     * Known issue, deliberately not changed in the clean-up: {@code page.viewportSize()} is null on
     * ANDROID_CHROME_DEVICE (the device's Chrome has no fixed viewport), so this throws a
     * NullPointerException there. To be fixed in a separate change.
     */
    public Set<String> visibleAboutProducts(List<String> products) {
        Set<String> visible = new TreeSet<>();
        for (String product : products) {
            Locator item = labelStartingWith(product);
            for (int i = 0; i < item.count(); i++) {
                BoundingBox box = item.nth(i).boundingBox();
                if (box != null && box.x >= 0 && box.x < page.viewportSize().width) {
                    visible.add(product);
                }
            }
        }
        return visible;
    }

    /** Educational video duration labels, e.g. "2:45"; each sits just below and right of its thumbnail. */
    public Locator videoDuration() {
        return page.getByText(VIDEO_DURATION);
    }

    /** Taps the video thumbnail above {@code duration}. */
    public void tapVideoThumbnailAbove(Locator duration) {
        BoundingBox box = duration.boundingBox();
        page.mouse().click(box.x - 40, box.y - 40);
    }

    /** A playing video (HTML video or embedded player). */
    public Locator videoPlayer() {
        return page.locator("video, iframe");
    }

    /** "Service Request and Claims" module (shown as Coming Soon). */
    public Locator serviceRequestAndClaims() {
        return label("Service Request and Claims");
    }

    // ------------------------------------------------------------ bottom navigation

    /** Bottom navigation tab: "Home", "My Policies", "Resources", "My Account" (Profile). */
    public Locator bottomTab(String name) {
        int height = app.windowHeight();
        return nearest(label(name), b -> b.y > height * 0.85);
    }

    public void tapBottomTab(String name) {
        StepLog.step("Tap bottom tab \"" + name + "\"");
        bottomTab(name).click();
        page.waitForTimeout(1500);
    }

    /** Logs out from the Profile (My Account) tab, confirming when the app asks. */
    public void logOut() {
        tapBottomTab(TAB_PROFILE);
        scrollTo(label("Logout")).click();
        Locator confirm = label("Logout").or(label("Yes"));
        if (confirm.count() > 0) {
            confirm.first().click();
        }
    }

    // ------------------------------------------------------------ helpers

    /** Accessible label of a Flutter element, or its visible text when it has none. */
    private static String accessibleText(Locator element) {
        String label = element.getAttribute("aria-label");
        return label != null && !label.isBlank() ? label : element.textContent();
    }

    /** Unlabelled prev/next arrows on the "About Our Products" header row: prev is the left one. */
    private Locator aboutProductsArrow(boolean prev) {
        BoundingBox header = aboutOurProducts().first().boundingBox();
        Locator buttons = page.locator("flt-semantics[role=button]");
        Locator left = null;
        Locator right = null;
        double leftX = Double.MAX_VALUE;
        double rightX = -1;
        for (int i = 0; i < buttons.count(); i++) {
            BoundingBox box = buttons.nth(i).boundingBox();
            if (box != null && Math.abs(box.y - header.y) < 30 && box.x > header.x + header.width && buttons.nth(i).textContent().isBlank()) {
                if (box.x < leftX) {
                    leftX = box.x;
                    left = buttons.nth(i);
                }
                if (box.x > rightX) {
                    rightX = box.x;
                    right = buttons.nth(i);
                }
            }
        }
        Locator arrow = prev ? left : right;
        if (arrow == null) {
            throw new IllegalStateException("About Our Products arrows not found next to the header");
        }
        return arrow;
    }

    /** First element of {@code candidates} whose box matches {@code where}. */
    private static Locator nearest(Locator candidates, Predicate<BoundingBox> where) {
        List<String> seen = new ArrayList<>();
        for (int i = 0; i < candidates.count(); i++) {
            BoundingBox box = candidates.nth(i).boundingBox();
            if (box != null && where.test(box)) {
                return candidates.nth(i);
            }
            if (box != null) {
                seen.add(Math.round(box.x) + "," + Math.round(box.y));
            }
        }
        throw new IllegalStateException("Element not found at the expected position; candidates at " + seen);
    }
}
