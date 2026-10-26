package sprint1;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;

import base.BaseTest;
import pages.LaunchPage;
import pages.LoginPage;
import pages.OnboardingPage;
import utils.FlutterApp;
import utils.ImageLog;
import utils.ImageUtils;

/**
 * BD2M-576 Launch Icon & Splash Screen - test scenario SC_01.
 *
 * Terms as used in the test cases:
 *   launch splash   - the Bima Sugam logo screen shown while the app loads
 *   Splash 1-4      - the four auto-advancing onboarding slides with the photo grid
 */
@Test(groups = { "BD2M-576", "sprint1" })
public class LaunchIconSplashTests extends BaseTest {

    // ---------------------------------------------------------------- test data

    /** Name expected on the home screen, in the manifest and in the iOS app title. */
    private static final String APP_NAME = "Bima Sugam";

    /** Onboarding carousel ("Splash 1-4") captions, in the expected order (as on UAT build 2.0.3+11). */
    private static final List<String> SLIDES = List.of(
            "Secure insurance access for every Indian",
            "Access all insurers, securely, in one place",
            "Helping protect you, your family and your future",
            "A single, secure home for all your insurance documents.");

    /** Grid tile photos on the onboarding screens. */
    private static final List<String> ONBOARDING_GRID = List.of(
            "person_one", "person_two", "person_three", "person_four", "person_five", "person_six", "person_seven",
            "person_eight", "person_nine", "person_ten", "person_eleven", "person_twelve", "person_thirten", "person_forten");

    /** Grid tile photos added on the login screen. */
    private static final List<String> LOGIN_GRID = List.of(
            "person_fiften", "person_sixten", "person_seventen", "person_eighten", "person_ninten", "person_twenty",
            "person_twentyone");

    /** Logo on the HTML launch splash shown while the app loads. */
    private static final String SPLASH_LOGO = "bima_sugam_logo";

    /** Bima Sugam logo drawn by the app on its screens. */
    private static final String BRAND_LOGO = "bsif_logo_blue";

    /** Brightness spread above which a screen area is showing photos rather than a plain background. */
    private static final double MIN_GRID_CONTENT_SPREAD = 10;

    /** Carousel moves about every 2 s; a full loop of 4 slides fits well inside this. */
    private static final int CAROUSEL_WAIT_MS = 15_000;
    /** Long enough to see a full loop of the 4 slides and the start of the next. */
    private static final int SEQUENCE_WATCH_MS = 12_000;
    private static final int IMAGE_WAIT_MS = 20_000;

    // ---------------------------------------------------------------- launch icon

    @Test(groups = NON_AUTOMATABLE, description = "SC_01_TC_001: Verify that the updated launch icon is displayed on the device home screen after installing the app.")
    public void tc001_launchIconOnHomeScreenAfterInstall() {
        nonAutomatable("needs installing the PWA to the home screen and viewing the OS home screen, outside browser automation");
    }

    @Test(groups = NON_AUTOMATABLE, description = "SC_01_TC_002: Verify that the updated launch icon matches the approved design.")
    public void tc002_launchIconMatchesApprovedDesign() {
        nonAutomatable("visual comparison of the home-screen icon with the approved design");
    }

    // ---------------------------------------------------------------- launch splash

    @Test(description = "SC_01_TC_003: Verify that the updated splash screen images are displayed when the app is launched.")
    public void tc003_splashImagesDisplayedOnLaunch() {
        ImageLog images = ImageLog.attach(page());
        LaunchPage launch = new LaunchPage(page()).openBeforeAppStarts();

        launch.waitForSplashLogoLoaded();
        assertThat(launch.splashLogo()).isVisible();
        assertAllLoaded(images, List.of(SPLASH_LOGO), "launch splash");
    }

    @Test(groups = NON_AUTOMATABLE, description = "SC_01_TC_004: Verify that the updated splash screen images match the approved design.")
    public void tc004_splashImagesMatchApprovedDesign() {
        nonAutomatable("visual comparison of the splash screen with the approved design");
    }

    // ---------------------------------------------------------------- grid tiles

    @Test(description = "SC_01_TC_005: Verify that the updated grid tile images are displayed on the onboarding screens.")
    public void tc005_gridTilesOnOnboarding() {
        ImageLog images = ImageLog.attach(page());
        OnboardingPage onboarding = new OnboardingPage(page()).open();

        assertAllLoaded(images, ONBOARDING_GRID, "onboarding");
        assertGridShowsPhotos(onboarding.gridScreenshot(), "onboarding");
    }

    @Test(description = "SC_01_TC_006: Verify that the updated grid tile images are displayed on the splash screen (Splash 1-4).")
    public void tc006_gridTilesOnSplashScreens() {
        ImageLog images = ImageLog.attach(page());
        OnboardingPage onboarding = new OnboardingPage(page()).open();
        assertAllLoaded(images, ONBOARDING_GRID, "Splash 1-4");
        SoftAssert soft = new SoftAssert();

        for (String slide : SLIDES) {
            onboarding.waitForSlide(slide, CAROUSEL_WAIT_MS);
            double spread = ImageUtils.contentSpread(onboarding.gridScreenshot());
            soft.assertTrue(spread > MIN_GRID_CONTENT_SPREAD, "Grid not showing photos on slide '" + slide + "' (spread " + spread + ")");
        }
        soft.assertAll();
    }

    @Test(description = "SC_01_TC_007: Verify that the updated grid tile images are displayed on the login screen.")
    public void tc007_gridTilesOnLogin() {
        ImageLog images = ImageLog.attach(page());
        LoginPage login = new OnboardingPage(page()).open().tapGetStarted();

        assertAllLoaded(images, LOGIN_GRID, "login");
        assertGridShowsPhotos(login.gridScreenshot(), "login");
    }

    @Test(groups = NON_AUTOMATABLE, description = "SC_01_TC_008: Verify that the updated grid tile images match the approved design.")
    public void tc008_gridTilesMatchApprovedDesign() {
        nonAutomatable("visual comparison of the grid tiles with the approved design");
    }

    // ---------------------------------------------------------------- branding

    @Test(description = "SC_01_TC_009: Verify that the branding updates are applied consistently on the onboarding screens.")
    public void tc009_brandingOnOnboarding() {
        ImageLog images = ImageLog.attach(page());
        OnboardingPage onboarding = new OnboardingPage(page()).open();
        SoftAssert soft = new SoftAssert();

        soft.assertTrue(onboarding.welcomeHeading().isVisible(), "Welcome text visible");
        soft.assertTrue(images.awaitLoaded(List.of(BRAND_LOGO), IMAGE_WAIT_MS).isEmpty(), "Bima Sugam logo loaded");
        assertBrandedTitle(soft);
        soft.assertAll();
    }

    @Test(description = "SC_01_TC_010: Verify that the branding updates are applied consistently on the splash screen.")
    public void tc010_brandingOnSplash() {
        LaunchPage launch = new LaunchPage(page()).openBeforeAppStarts();
        launch.waitForSplashLogoLoaded();
        Map<String, Object> manifest = launch.manifest();
        SoftAssert soft = new SoftAssert();

        soft.assertEquals(launch.splashLogo().getAttribute("alt"), APP_NAME, "Splash logo alt text");
        soft.assertTrue(launch.documentTitle().contains(APP_NAME), "Title shown during launch is '" + launch.documentTitle() + "'");
        soft.assertEquals(manifest.get("name"), APP_NAME, "App name in manifest");
        soft.assertEquals(launch.metaContent("apple-mobile-web-app-title"), APP_NAME, "iOS app title");
        soft.assertAll();
    }

    @Test(description = "SC_01_TC_011: Verify that the branding updates are applied consistently on the login screen.")
    public void tc011_brandingOnLogin() {
        ImageLog images = ImageLog.attach(page());
        LoginPage login = new OnboardingPage(page()).open().tapGetStarted();
        SoftAssert soft = new SoftAssert();

        soft.assertTrue(login.heading().isVisible(), "Login heading visible");
        soft.assertTrue(images.awaitLoaded(List.of(BRAND_LOGO), IMAGE_WAIT_MS).isEmpty(), "Bima Sugam logo loaded");
        assertBrandedTitle(soft);
        soft.assertAll();
    }

    // ---------------------------------------------------------------- onboarding screens

    @Test(description = "SC_01_TC_12: Validate the \"Welcome to Bima Sugam\" screen functionality.")
    public void tc012_welcomeScreen() {
        OnboardingPage onboarding = new OnboardingPage(page()).open();
        FlutterApp app = new FlutterApp(page());

        assertEquals(app.currentRoute(), OnboardingPage.ROUTE);
        assertThat(onboarding.welcomeHeading()).isVisible();
        assertThat(onboarding.getStartedButton()).isVisible();
        onboarding.tapGetStarted();
        assertEquals(app.currentRoute(), LoginPage.ROUTE, "Get Started leads to login");
    }

    @Test(description = "SC_01_TC_13: Verify Splash 4 is displayed correctly")
    public void tc013_splash4Displayed() {
        ImageLog images = ImageLog.attach(page());
        OnboardingPage onboarding = new OnboardingPage(page()).open();
        String splash4 = SLIDES.get(3);

        onboarding.waitForSlide(splash4, CAROUSEL_WAIT_MS);

        assertThat(onboarding.slide(splash4)).isVisible();
        assertThat(onboarding.welcomeHeading()).isVisible();
        assertThat(onboarding.getStartedButton()).isVisible();
        assertTrue(images.awaitLoaded(List.of(BRAND_LOGO), IMAGE_WAIT_MS).isEmpty(), "Bima Sugam logo loaded");
        assertGridShowsPhotos(onboarding.gridScreenshot(), "Splash 4");
    }

    @Test(description = "SC_01_TC_14: Verify multiple rapid taps on Login (Get Started) trigger only one navigation and no crash")
    public void tc014_rapidTapsNavigateOnce() {
        List<String> pageErrors = new ArrayList<>();
        page().onPageError(pageErrors::add);
        OnboardingPage onboarding = new OnboardingPage(page()).open();

        onboarding.rapidTapGetStarted(5);
        FlutterApp app = new FlutterApp(page());
        page().waitForCondition(() -> !OnboardingPage.ROUTE.equals(app.currentRoute()));
        page().waitForTimeout(2000); // let any extra taps take effect

        assertEquals(app.currentRoute(), LoginPage.ROUTE,
                "Extra taps triggered another action: app ended on #/" + app.currentRoute() + " instead of the login screen");
        LoginPage login = new LoginPage(page());
        assertThat(login.loginViaOtpButton()).isVisible();
        page().goBack();
        assertThat(onboarding.getStartedButton()).isVisible();
        assertEquals(app.currentRoute(), OnboardingPage.ROUTE,
                "One Back press should return to onboarding; more means login was opened more than once");
        assertEquals(pageErrors, List.of(), "JavaScript errors after rapid taps");
    }

    @Test(description = "SC_01_TC_015: Verify onboarding behavior when network connectivity is unavailable (minimise and reopen offline).")
    public void tc015_offlineMinimiseAndReopen() {
        OnboardingPage onboarding = new OnboardingPage(page()).open();
        assertGridShowsPhotos(onboarding.gridScreenshot(), "onboarding before going offline");

        page().context().setOffline(true);
        try {
            List<String> visibility = new FlutterApp(page()).minimiseAndReopen(2000);

            assertEquals(visibility, List.of("hidden", "visible"), "App was minimised and reopened");
            assertThat(onboarding.welcomeHeading()).isVisible();
            assertThat(onboarding.getStartedButton()).isVisible();
            assertGridShowsPhotos(onboarding.gridScreenshot(), "onboarding after reopening offline");
        } finally {
            page().context().setOffline(false);
        }
    }

    @Test(description = "SC_01_TC_016: Verify that each onboarding screen is displayed in the correct sequence.")
    public void tc016_onboardingSequence() {
        OnboardingPage onboarding = new OnboardingPage(page()).open();

        List<String> seen = onboarding.recordSlideSequence(SLIDES, SEQUENCE_WATCH_MS);

        int first = seen.indexOf(SLIDES.get(0));
        assertTrue(first >= 0 && seen.size() >= first + SLIDES.size(), "Did not see a full cycle of slides: " + seen);
        assertEquals(seen.subList(first, first + SLIDES.size()), SLIDES, "Slide order");
    }

    @Test(description = "SC_01_TC_017: Verify that the updated splash screen images are displayed when the app is launched and when the minimized application is reopened.")
    public void tc017_splashOnLaunchAndOnReopen() {
        LaunchPage launch = new LaunchPage(page()).openBeforeAppStarts();
        launch.waitForSplashLogoLoaded();
        assertThat(launch.splashLogo()).isVisible();

        FlutterApp app = new FlutterApp(page());
        app.enableSemantics();
        OnboardingPage onboarding = new OnboardingPage(page());
        assertThat(onboarding.welcomeHeading()).isVisible();

        List<String> visibility = app.minimiseAndReopen(2000);
        assertEquals(visibility, List.of("hidden", "visible"), "App was minimised and reopened");
        assertThat(onboarding.welcomeHeading()).isVisible();
        assertGridShowsPhotos(onboarding.gridScreenshot(), "after reopening from minimised");

        // Reopening after the app was closed starts it again, with the launch splash
        page().reload(new Page.ReloadOptions().setWaitUntil(WaitUntilState.COMMIT));
        launch.waitForSplashLogoLoaded();
        assertThat(launch.splashLogo()).isVisible();
        app.enableSemantics();
        assertThat(onboarding.welcomeHeading()).isVisible();
    }

    // ---------------------------------------------------------------- helpers

    private static void assertAllLoaded(ImageLog images, List<String> names, String where) {
        var missing = images.awaitLoaded(names, IMAGE_WAIT_MS);
        assertTrue(missing.isEmpty(), "Images not loaded on " + where + ": " + missing + "; failed requests: " + images.failed());
    }

    private static void assertGridShowsPhotos(byte[] gridScreenshot, String where) {
        double spread = ImageUtils.contentSpread(gridScreenshot);
        assertTrue(spread > MIN_GRID_CONTENT_SPREAD, "Grid area on " + where + " is not showing photos (brightness spread " + spread + ")");
    }

    private void assertBrandedTitle(SoftAssert soft) {
        String title = page().title();
        soft.assertTrue(title.contains(APP_NAME), "Title '" + title + "' contains " + APP_NAME);
    }
}
