package sprint1;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;
import static pages.HomePage.TAB_HOME;
import static pages.HomePage.TAB_MY_POLICIES;
import static pages.HomePage.TAB_PROFILE;
import static pages.HomePage.TAB_RESOURCES;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;

import com.microsoft.playwright.Download;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.BoundingBox;

import base.BaseTest;
import pages.HomePage;
import pages.LoginPage;
import pages.OnboardingPage;
import pages.OtpSheet;
import utils.FlutterApp;
import utils.ImageUtils;
import utils.StepLog;
import utils.TestAccounts;
import utils.TestAccounts.Account;

/**
 * BD2M-669 Dashboard - Home Tab - scenario SC_01.
 *
 * Every test starts as a logged-in user: one OTP login per platform per run (see
 * factory.LoginSession), then the saved session is reused. Tests that depend on
 * the account's data (policies, notifications) are skipped as BLOCKED when the
 * account does not have that data.
 */
@Test(groups = { "BD2M-669", "sprint1" })
public class HomeTabTests extends BaseTest {

    // ---------------------------------------------------------------- test data

    /** Greeting the test case specifies ("Namaste [Name]"); newer builds may say "Hello". */
    private static final String GREETING_PREFIX = "Namaste";

    private static final List<String> BRAND_BANNERS = List.of("CONSUMER FIRST", "NOT-FOR-PROFIT", "TRANSPARENT & NEUTRAL");
    private static final String BRAND_INFO_PREFIX = "Designed around your needs";
    private static final List<String> ABOUT_PRODUCTS = List.of("Bima Jeevan", "Bima Swasthya", "Bima Vahan");
    private static final List<String> POLICY_TABS = List.of("Motor", "Health", "Term Life");
    private static final List<String> POLICY_CARD_FIELDS = List.of("Product Name", "IDV", "Policy Number", "Policy End Date");
    private static final String MOTOR_EMPTY_STATE = "see any Motor policies yet";
    private static final String MOTOR_BUY_FLOW_TITLE = "Get Your Car Insurance Quote";
    private static final String COMING_SOON = "Coming Soon";

    private static final List<String> MY_POLICIES_SECTIONS = List.of("Pending Applications", "Active Policies", "Expired Policies");
    private static final List<String> PROFILE_ROWS = List.of("My Consents", "Contact Us", "Logout");

    @Override
    protected boolean requiresLogin() {
        return true;
    }

    private HomePage home() {
        return new HomePage(page()).open();
    }

    // ---------------------------------------------------------------- header

    @Test(description = "SC_01_TC_001: Verify that the user's avatar is displayed in the top bar of the Home tab.")
    public void tc001_avatarInTopBar() {
        assertThat(home().avatar()).isVisible();
    }

    @Test(description = "SC_01_TC_002: Verify that the personalized greeting \"Namaste [Name]\" is displayed in the top bar of the Home tab.")
    public void tc002_personalisedGreeting() {
        String greeting = home().greetingText();

        assertTrue(greeting.startsWith(GREETING_PREFIX + " ") && greeting.length() > GREETING_PREFIX.length() + 1,
                "Greeting is '" + greeting + "', expected 'Namaste <name>'");
    }

    @Test(description = "SC_01_TC_003: Verify that tapping the notification bell opens notifications.")
    public void tc003_bellOpensNotifications() {
        HomePage home = home();

        home.notificationBell().click();

        assertThat(home.notificationsScreen().first()).isVisible();
    }

    @Test(groups = BLOCKED, description = "SC_01_TC_004: Verify that the notification bell badge reflects the unread notification count.")
    public void tc004_bellBadgeCount() {
        blocked("needs an account with a known number of unread notifications (and the badge count is not exposed to the page)");
    }

    // ---------------------------------------------------------------- hero banner

    @Test(description = "SC_01_TC_005: Verify that the hero banner renders on the Home tab.")
    public void tc005_heroBannerRenders() {
        assertThat(home().heroBanner().first()).isVisible();
    }

    @Test(description = "SC_01_TC_006: Verify that tapping the hero banner \"Explore plans\" CTA navigates to the relevant product flow.")
    public void tc006_heroExplorePlans() {
        HomePage home = home();
        Locator cta = home.heroExplorePlansButton();
        assertTrue(cta.count() > 0, "No 'Explore plans' button on the hero banner for this account");
        String before = home.currentRoute();

        cta.first().click();
        page().waitForTimeout(2500);

        assertNotEquals(home.currentRoute(), before, "'Explore plans' did not open a product flow");
    }

    @Test(groups = NON_AUTOMATABLE, description = "SC_01_TC_047: Verify that the hero banner displays different promotional content correctly based on configuration.")
    public void tc047_heroBannerPromotions() {
        nonAutomatable("needs promotions configured on the backend per run");
    }

    // ---------------------------------------------------------------- quick actions

    @Test(description = "SC_01_TC_007: Verify that the \"Quick Actions\" header is displayed on the Home tab.")
    public void tc007_quickActionsHeader() {
        HomePage home = home();
        assertThat(home.scrollTo(home.quickActions())).isVisible();
    }

    @Test(description = "SC_01_TC_008: Verify that Quick Actions displays policy/action cards in a horizontal carousel.")
    public void tc008_quickActionsCarousel() {
        HomePage home = home();
        home.scrollTo(home.quickActionCards());
        Locator cards = home.quickActionCards();

        assertTrue(cards.count() > 0, "No Quick Actions cards for this account");
        if (cards.count() > 1) {
            BoundingBox a = cards.nth(0).boundingBox();
            BoundingBox b = cards.nth(1).boundingBox();
            assertTrue(Math.abs(a.y - b.y) < 5 && b.x > a.x, "Cards are not laid out side by side");
        }
    }

    @Test(description = "SC_01_TC_009: Verify that the Quick Actions pager dots work correctly.")
    public void tc009_quickActionsPagerDots() {
        HomePage home = home();
        Locator first = home.scrollTo(home.quickActionCards());
        if (home.quickActionCards().count() < 2) {
            blocked("account has fewer than 2 Quick Actions cards, so there is nothing to page through");
        }
        BoundingBox box = first.boundingBox();
        double before = box.x;

        home.swipeLeftAcross(box);

        assertTrue(home.quickActionCards().first().boundingBox().x < before, "Swiping did not move the Quick Actions carousel");
    }

    @Test(description = "SC_01_TC_010: Verify that tapping a Quick Actions card navigates to the relevant policy/action.")
    public void tc010_quickActionCardNavigates() {
        HomePage home = home();
        Locator card = home.scrollTo(home.quickActionCards());
        String before = home.currentRoute();

        card.click();
        page().waitForTimeout(2500);

        assertNotEquals(home.currentRoute(), before, "Tapping a Quick Actions card did not navigate");
    }

    @Test(description = "SC_01_TC_011: Verify that tapping \"View All\" in Quick Actions navigates to the full list.")
    public void tc011_quickActionsViewAll() {
        HomePage home = home();
        home.scrollTo(home.quickActions());
        String before = home.currentRoute();

        home.viewAll().first().click();
        page().waitForTimeout(2500);

        assertNotEquals(home.currentRoute(), before, "'View All' in Quick Actions did not open the full list");
    }

    // ---------------------------------------------------------------- buy insurance

    @Test(description = "SC_01_TC_012: Verify that tapping the Term Life Insurance tile under Buy Insurance starts the correct buy flow.")
    public void tc012_buyTermLife() {
        assertBuyTileStartsFlow("Term Life Insurance");
    }

    @Test(description = "SC_01_TC_013: Verify that tapping the Health Insurance tile under Buy Insurance starts the correct buy flow.")
    public void tc013_buyHealth() {
        assertBuyTileStartsFlow("Health Insurance");
    }

    @Test(description = "SC_01_TC_014: Verify that tapping the Motor Insurance tile under Buy Insurance starts the correct buy flow.")
    public void tc014_buyMotor() {
        HomePage home = home();
        Locator tile = home.scrollTo(home.labelContaining("Private Car Insurance"));

        tile.click();

        assertThat(home.label(MOTOR_BUY_FLOW_TITLE).first()).isVisible();
    }

    // ---------------------------------------------------------------- brand banners

    @Test(description = "SC_01_TC_015: Verify that the \"CONSUMER FIRST\" brand banner renders on the Home tab.")
    public void tc015_consumerFirstBanner() {
        assertBannerRenders(BRAND_BANNERS.get(0));
    }

    @Test(description = "SC_01_TC_016: Verify that the \"NOT-FOR-PROFIT\" brand banner renders on the Home tab.")
    public void tc016_notForProfitBanner() {
        assertBannerRenders(BRAND_BANNERS.get(1));
    }

    @Test(description = "SC_01_TC_017: Verify that the \"TRANSPARENT & NEUTRAL\" brand banner renders on the Home tab.")
    public void tc017_transparentNeutralBanner() {
        assertBannerRenders(BRAND_BANNERS.get(2));
    }

    @Test(description = "SC_01_TC_018: Verify that tapping \"Know More\" on a brand banner opens the relevant info content.")
    public void tc018_knowMoreOpensInfo() {
        HomePage home = home();
        Locator knowMore = home.scrollTo(home.knowMoreButton());

        knowMore.click();

        assertThat(home.labelStartingWith(BRAND_INFO_PREFIX).first()).isVisible();
    }

    // ---------------------------------------------------------------- about our products

    @Test(description = "SC_01_TC_019: Verify that the \"About Our Products\" carousel displays Bima Jeevan, Bima Swasthya and Bima Vahan.")
    public void tc019_aboutOurProducts() {
        HomePage home = home();
        home.scrollTo(home.aboutOurProducts());
        SoftAssert soft = new SoftAssert();
        Locator next = home.aboutProductsNextArrow();

        for (String product : ABOUT_PRODUCTS) {
            boolean found = false;
            for (int i = 0; i < ABOUT_PRODUCTS.size() && !found; i++) {
                found = home.labelStartingWith(product).count() > 0;
                if (!found) {
                    next.click();
                    page().waitForTimeout(800);
                }
            }
            soft.assertTrue(found, product + " not shown in About Our Products");
        }
        soft.assertAll();
    }

    @Test(description = "SC_01_TC_020: Verify that the \"About Our Products\" carousel pages correctly using the next arrow.")
    public void tc020_aboutProductsNext() {
        HomePage home = home();
        home.scrollTo(home.aboutOurProducts());
        Set<String> before = home.visibleAboutProducts(ABOUT_PRODUCTS);

        home.aboutProductsNextArrow().click();
        page().waitForTimeout(1000);

        assertNotEquals(home.visibleAboutProducts(ABOUT_PRODUCTS), before, "Next arrow did not change the products shown");
    }

    @Test(description = "SC_01_TC_021: Verify that the \"About Our Products\" carousel pages correctly using the prev arrow.")
    public void tc021_aboutProductsPrev() {
        HomePage home = home();
        home.scrollTo(home.aboutOurProducts());
        Set<String> start = home.visibleAboutProducts(ABOUT_PRODUCTS);
        home.aboutProductsNextArrow().click();
        page().waitForTimeout(1000);

        home.aboutProductsPrevArrow().click();
        page().waitForTimeout(1000);

        assertEquals(home.visibleAboutProducts(ABOUT_PRODUCTS), start, "Prev arrow did not go back to the first products");
    }

    @Test(description = "SC_01_TC_022: Verify that tapping a product in the \"About Our Products\" carousel navigates to the product info.")
    public void tc022_aboutProductOpensInfo() {
        HomePage home = home();
        home.scrollTo(home.aboutOurProducts());
        FlutterApp app = new FlutterApp(page());
        Set<String> before = app.visibleTexts();
        String route = home.currentRoute();

        home.labelStartingWith(ABOUT_PRODUCTS.get(0)).first().click();
        page().waitForTimeout(2500);

        assertTrue(!home.currentRoute().equals(route) || !app.visibleTexts().equals(before), "Tapping Bima Jeevan opened no product info");
    }

    // ---------------------------------------------------------------- your insurance policies

    @Test(description = "SC_01_TC_023: Verify that \"Your Insurance Policies\" section displays the Motor, Health and Term Life product tabs.")
    public void tc023_policyTabs() {
        HomePage home = home();
        home.scrollTo(home.yourInsurancePolicies());
        SoftAssert soft = new SoftAssert();

        for (String tab : POLICY_TABS) {
            soft.assertTrue(home.policyTab(tab).count() > 0, "Tab '" + tab + "' missing");
        }
        soft.assertAll();
    }

    @Test(description = "SC_01_TC_024: Verify that selecting the Motor tab displays the matching Motor policies.")
    public void tc024_motorTab() {
        assertPolicyTabShowsItsPolicies("Motor");
    }

    @Test(description = "SC_01_TC_025: Verify that selecting the Health tab displays the matching Health policies.")
    public void tc025_healthTab() {
        assertPolicyTabShowsItsPolicies("Health");
    }

    @Test(description = "SC_01_TC_026: Verify that selecting the Term Life tab displays the matching Term Life policies.")
    public void tc026_termLifeTab() {
        assertPolicyTabShowsItsPolicies("Term Life");
    }

    @Test(description = "SC_01_TC_027: Verify that the policy card displays the correct data-driven values (insurer logo, vehicle type, product name, IDV, policy number, end date).")
    public void tc027_policyCardValues() {
        HomePage home = home();
        Locator card = policyCardOrBlock(home);
        String text = home.policyCardText(card);
        SoftAssert soft = new SoftAssert();

        for (String field : POLICY_CARD_FIELDS) {
            soft.assertTrue(text.contains(field), "Policy card has no '" + field + "': " + text);
        }
        soft.assertTrue(Pattern.compile("\\d{2}/\\d{2}/\\d{4}").matcher(text).find(), "No end date (dd/MM/yyyy) on the card: " + text);
        soft.assertAll();
    }

    @Test(description = "SC_01_TC_028: Verify that tapping \"View Details\" on a policy card works correctly.")
    public void tc028_policyViewDetails() {
        HomePage home = home();
        policyCardOrBlock(home);
        String before = home.currentRoute();

        home.viewDetailsButton().first().click();
        page().waitForTimeout(2500);

        assertNotEquals(home.currentRoute(), before, "'View Details' did not open the policy details");
    }

    @Test(description = "SC_01_TC_029: Verify that tapping \"Download Policy\" on a policy card works correctly.")
    public void tc029_policyDownload() {
        HomePage home = home();
        policyCardOrBlock(home);

        Download download = page().waitForDownload(() -> home.downloadPolicyButton().first().click());

        assertTrue(download.suggestedFilename().toLowerCase().endsWith(".pdf"), "Downloaded file is not a PDF: " + download.suggestedFilename());
    }

    @Test(description = "SC_01_TC_030: Verify that tapping \"View All\" in \"Your Insurance Policies\" navigates to the full list.")
    public void tc030_policiesViewAll() {
        HomePage home = home();
        home.scrollTo(home.yourInsurancePolicies());
        String before = home.currentRoute();

        Locator viewAll = home.viewAll();
        viewAll.nth(viewAll.count() - 1).click();
        page().waitForTimeout(2500);

        assertNotEquals(home.currentRoute(), before, "'View All' in Your Insurance Policies did not open the full list");
    }

    // ---------------------------------------------------------------- educational videos

    @Test(description = "SC_01_TC_031: Verify that the educational video carousel lists videos with thumbnail, duration and title.")
    public void tc031_educationalVideos() {
        HomePage home = home();
        Locator duration = home.scrollTo(home.videoDuration());

        assertThat(duration).isVisible();
    }

    @Test(description = "SC_01_TC_032: Verify that tapping play on an educational video opens the video.")
    public void tc032_videoPlays() {
        HomePage home = home();
        Locator duration = home.scrollTo(home.videoDuration());
        String route = home.currentRoute();

        home.tapVideoThumbnailAbove(duration);
        page().waitForTimeout(2500);

        boolean opened = !home.currentRoute().equals(route) || home.videoPlayer().count() > 0 || page().context().pages().size() > 1;
        assertTrue(opened, "Tapping the video thumbnail opened no video");
    }

    @Test(groups = BLOCKED, description = "SC_01_TC_033: Verify that tapping like/favorite on an educational video toggles the favorite state.")
    public void tc033_videoFavoriteToggle() {
        blocked("the like/favorite icon has no label in the app; need its accessibility label (or Semantics) to find it");
    }

    @Test(groups = BLOCKED, description = "SC_01_TC_034: Verify that the like/favorite state of an educational video persists.")
    public void tc034_videoFavoritePersists() {
        blocked("depends on SC_01_TC_033 (like/favorite icon has no label)");
    }

    // ---------------------------------------------------------------- service requests

    @Test(description = "SC_01_TC_035: Verify that \"Service Request and Claims\" module shows the \"Coming Soon\" state.")
    public void tc035_serviceRequestComingSoon() {
        HomePage home = home();
        home.scrollTo(home.serviceRequestAndClaims());

        assertThat(home.label(COMING_SOON).first()).isVisible();
    }

    @Test(description = "SC_01_TC_036: Verify that \"Service Request and Claims\" module is non-interactive.")
    public void tc036_serviceRequestNonInteractive() {
        HomePage home = home();
        Locator module = home.scrollTo(home.serviceRequestAndClaims());
        String route = home.currentRoute();
        FlutterApp app = new FlutterApp(page());
        Set<String> before = app.visibleTexts();

        module.click(new Locator.ClickOptions().setForce(true));
        page().waitForTimeout(1500);

        assertEquals(home.currentRoute(), route, "Tapping the module navigated");
        assertEquals(app.visibleTexts(), before, "Tapping the module changed the screen");
    }

    // ---------------------------------------------------------------- bottom navigation

    @Test(description = "SC_01_TC_037: Verify that Home is shown as the active tab by default in the bottom navigation.")
    public void tc037_homeActiveByDefault() {
        HomePage home = home();
        Locator homeTab = home.bottomTab(TAB_HOME);

        assertThat(homeTab).isVisible();
        String selected = homeTab.getAttribute("aria-selected");
        assertTrue(selected == null || selected.equals("true"), "Home tab is not the selected tab");
    }

    @Test(description = "SC_01_TC_038: Verify that tapping the My Policies tab in the bottom navigation navigates to the My Policies section.")
    public void tc038_myPoliciesTab() {
        HomePage home = home();
        home.tapBottomTab(TAB_MY_POLICIES);

        assertTrue(MY_POLICIES_SECTIONS.stream().anyMatch(s -> home.label(s).count() > 0), "My Policies section not shown");
    }

    @Test(description = "SC_01_TC_039: Verify that tapping the Resources tab in the bottom navigation navigates to the Resources section.")
    public void tc039_resourcesTab() {
        HomePage home = home();
        home.tapBottomTab(TAB_RESOURCES);

        assertTrue(home.label(TAB_RESOURCES).count() > 1, "Resources section title not shown (only the tab)");
    }

    @Test(description = "SC_01_TC_040: Verify that tapping the Profile tab in the bottom navigation navigates to the Profile section.")
    public void tc040_profileTab() {
        HomePage home = home();
        home.tapBottomTab(TAB_PROFILE);

        assertTrue(PROFILE_ROWS.stream().anyMatch(r -> home.label(r).count() > 0), "Profile (My Account) section not shown");
    }

    @Test(description = "SC_01_TC_041: Verify that the state is preserved appropriately when switching tabs and returning to the Home tab.")
    public void tc041_statePreservedAcrossTabs() {
        HomePage home = home();
        String greeting = home.greetingText();

        home.tapBottomTab(TAB_MY_POLICIES);
        home.tapBottomTab(TAB_HOME);

        assertEquals(home.waitUntilShown().greetingText(), greeting, "Home did not come back with the same content");
    }

    // ---------------------------------------------------------------- loading, empty, error

    @Test(description = "SC_01_TC_042: Verify that the loading state is handled with appropriate messaging/placeholders on the Home tab.")
    public void tc042_loadingPlaceholders() {
        List<Route> held = new CopyOnWriteArrayList<>();
        page().route("**/*", route -> {
            String type = route.request().resourceType();
            if (type.equals("fetch") || type.equals("xhr")) {
                held.add(route); // keep the Home tab's data requests pending
            } else {
                route.fallback();
            }
        });
        new FlutterApp(page()).open(HomePage.ROUTE);
        page().waitForTimeout(2000);

        double spread = ImageUtils.contentSpread(page().screenshot());
        held.forEach(Route::resume);
        assertTrue(spread > 5, "Home tab is blank while its data is loading (no placeholders)");
    }

    @Test(description = "SC_01_TC_043: Verify that the empty state message \"You don't see any Motor policies yet\" with \"Explore Plans\" CTA is displayed when the user has no policies in \"Your Insurance Policies\" section.")
    public void tc043_motorEmptyState() {
        HomePage home = home();
        home.scrollTo(home.yourInsurancePolicies());
        home.selectPolicyTab("Motor");
        if (home.policyCards().count() > 0) {
            blocked("this account has Motor policies; needs an account with none");
        }

        SoftAssert soft = new SoftAssert();
        soft.assertTrue(home.labelContaining(MOTOR_EMPTY_STATE).count() > 0,
                "Empty-state text is not \"You don't see any Motor policies yet\": " + home.emptyPolicies().allTextContents());
        soft.assertTrue(home.label("Explore Plans").count() > 0, "No 'Explore Plans' button in the empty state");
        soft.assertAll();
    }

    @Test(description = "SC_01_TC_044: Verify that an appropriate error message is displayed when a section on the Home tab fails to load.")
    public void tc044_sectionLoadError() {
        page().route("**/*", route -> {
            String type = route.request().resourceType();
            if ((type.equals("fetch") || type.equals("xhr")) && !route.request().url().contains("/assets/")) {
                route.fulfill(new Route.FulfillOptions().setStatus(500).setContentType("application/json").setBody("{\"message\":\"Internal Server Error\"}"));
            } else {
                route.fallback();
            }
        });
        new FlutterApp(page()).open(HomePage.ROUTE);
        page().waitForTimeout(3000);

        Set<String> texts = new FlutterApp(page()).visibleTexts();
        boolean errorShown = texts.stream().anyMatch(t -> t.matches("(?is).*(error|wrong|failed|unable|try again|retry).*"));
        assertTrue(errorShown, "No error message when the Home tab's data failed to load; screen shows: " + texts);
    }

    @Test(groups = NON_AUTOMATABLE, description = "SC_01_TC_045: Verify that the Home tab matches the provided design across supported device sizes.")
    public void tc045_matchesDesign() {
        nonAutomatable("visual comparison with the provided design");
    }

    @Test(description = "SC_01_TC_046: Verify that the Home tab page scrolls correctly with the bottom navigation remaining persistent.")
    public void tc046_scrollKeepsBottomNav() {
        HomePage home = home();
        BoundingBox tabBefore = home.bottomTab(TAB_HOME).boundingBox();
        double greetingY = home.greeting().first().boundingBox().y;

        page().mouse().move(200, 400);
        page().mouse().wheel(0, 800);
        page().waitForTimeout(1000);

        Locator greetingAfter = home.greeting();
        assertTrue(greetingAfter.count() == 0 || greetingAfter.first().boundingBox().y < greetingY, "Home tab did not scroll");
        assertEquals(home.bottomTab(TAB_HOME).boundingBox().y, tabBefore.y, 1.0, "Bottom navigation moved while scrolling");
    }

    @Test(description = "SC_01_TC_048: Verify that the avatar shows a default placeholder when the profile photo is absent or fails to load.")
    public void tc048_avatarPlaceholder() {
        page().route(Pattern.compile("(?i).*(profile|avatar|photo|user_image).*\\.(png|jpe?g|webp)(\\?.*)?$"),
                route -> route.fulfill(new Route.FulfillOptions().setStatus(404)));
        HomePage home = home();

        Locator avatar = home.avatar();
        assertThat(avatar).isVisible();
        double spread = ImageUtils.contentSpread(home.areaScreenshot(avatar));
        assertTrue(spread > 5, "Avatar is a blank circle when the photo cannot load");
    }

    @Test(groups = BLOCKED, description = "SC_01_TC_049: Verify that the notification bell badge clears / decrements after the user reads notifications and returns to Home.")
    public void tc049_badgeClearsAfterReading() {
        blocked("needs an account with unread notifications (and the badge count is not exposed to the page)");
    }

    @Test(groups = NON_AUTOMATABLE, description = "SC_01_TC_050: Verify that opening the app from a push notification and pressing back lands on Home.")
    public void tc050_pushNotificationBack() {
        nonAutomatable("needs a real push notification with the app killed, outside browser automation");
    }

    @Test(groups = { SENDS_OTP }, description = "SC_01_TC_051: Verify that data of a previous user is not shown after logout and login with another user.")
    public void tc051_noPreviousUserDataAfterRelogin() {
        HomePage home = home();
        String firstGreeting = home.greetingText();

        home.logOut();
        Account other = TestAccounts.next();
        LoginPage login = new LoginPage(page()).open();
        login.enterValidDetails(other);
        TestAccounts.clearOtp();
        login.tapLoginViaOtp();
        new OtpSheet(page()).waitUntilShown().enterOtpAndValidate(TestAccounts.waitForOtp(other.masked()));
        String secondGreeting = new HomePage(page()).waitUntilShown().greetingText();

        StepLog.step("Greeting captured for both users (names not logged)");
        assertNotEquals(secondGreeting, firstGreeting, "The second user sees the first user's greeting");
    }

    @Test(description = "SC_01_TC_052: Verify that an expired session on the Home tab is handled correctly.")
    public void tc052_expiredSession() {
        home();
        StepLog.step("Expire the session: clear the app's stored login");
        page().evaluate("async () => { localStorage.clear(); sessionStorage.clear(); for (const db of (await indexedDB.databases?.() ?? [])) indexedDB.deleteDatabase(db.name); }");
        page().context().clearCookies();

        page().reload();
        FlutterApp app = new FlutterApp(page());
        app.enableSemantics();
        page().waitForTimeout(2000);

        String route = app.currentRoute();
        boolean onLogin = route.startsWith(LoginPage.ROUTE) || route.startsWith(OnboardingPage.ROUTE);
        assertTrue(onLogin, "With an expired session the app stayed on #/" + route + " instead of going to Login");
    }

    // ---------------------------------------------------------------- helpers

    private void assertBannerRenders(String title) {
        HomePage home = home();
        assertThat(home.scrollTo(home.brandBanner(title))).isVisible();
    }

    private void assertBuyTileStartsFlow(String tile) {
        HomePage home = home();
        home.scrollTo(home.buyInsurance());
        Locator target = home.scrollTo(home.label(tile));
        String route = home.currentRoute();

        target.click();
        page().waitForTimeout(2500);

        assertNotEquals(home.currentRoute(), route, "Tapping '" + tile + "' under Buy Insurance started no buy flow");
    }

    private void assertPolicyTabShowsItsPolicies(String tab) {
        HomePage home = home();
        home.scrollTo(home.yourInsurancePolicies());

        home.selectPolicyTab(tab);

        Locator cards = home.policyCards();
        if (cards.count() == 0) {
            assertTrue(home.emptyPolicies().count() > 0, "Tab '" + tab + "' shows neither policies nor an empty-state message");
            return;
        }
        for (int i = 0; i < cards.count(); i++) {
            assertTrue(home.policyCardText(cards.nth(i)).startsWith(tab), "A non-" + tab + " policy is listed under the " + tab + " tab: " + home.policyCardText(cards.nth(i)));
        }
    }

    private Locator policyCardOrBlock(HomePage home) {
        home.scrollTo(home.yourInsurancePolicies());
        if (home.policyCards().count() == 0) {
            blocked("this account has no policies; needs an account with at least one policy");
        }
        return home.policyCards().first();
    }
}
