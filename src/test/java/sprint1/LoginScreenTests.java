package sprint1;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.BoundingBox;

import base.BaseTest;
import pages.LoginPage;
import pages.OtpSheet;
import pages.ProductJourneyPage;
import utils.ApiInterceptor;
import utils.FlutterApp;
import utils.ImageLog;
import utils.ImageUtils;
import utils.StepLog;
import utils.TestAccounts;
import utils.TestAccounts.Account;

/**
 * BD2M-672 App - Pre-Login / Login Screen (OTP Login + Explore Products) - scenario SC_01.
 *
 * OTP safety: tests that tap "Login via OTP" without meaning to send an OTP block
 * the request inside the browser (ApiInterceptor), so nothing reaches UAT and no
 * SMS is sent. Only tests in the "otp" group send a real OTP, to a test account.
 */
@Test(groups = { "BD2M-672", "sprint1" })
public class LoginScreenTests extends BaseTest {

    // ---------------------------------------------------------------- test data

    /** Valid format, used only in tests that never let a request reach UAT (no SMS is sent to it). */
    private static final String VALID_FORMAT_MOBILE = "9876543210";
    private static final String ADULT_DOB_DIGITS = "01011990";
    private static final String INVALID_DATE_DIGITS = "31022020";
    private static final String SINGLE_DIGIT_DOB = "1";

    /** Message the test case (SC_01_TC_038) specifies for an invalid mobile number. */
    private static final String INVALID_MOBILE_MESSAGE_SPEC = "Mobile number is invalid";

    private static final String BRAND_LOGO = "bsif_logo_blue";
    private static final String INDIA_FLAG = "india_flag";

    private static final String PRIVATE_CAR = "Private Car Insurance";
    private static final String PRIVATE_BIKE = "Private Bike Insurance";
    private static final String COMMERCIAL_VEHICLE = "Commercial Vehicle Insurance";
    private static final String HEALTH = "Health Insurance";
    private static final String TERM_LIFE = "Term Life Insurance";

    /** Screen sizes (CSS pixels) for the no-clipping check: small and large phones, a tablet. */
    private static final List<int[]> DEVICE_SIZES = List.of(
            new int[] { 320, 568 }, new int[] { 360, 640 }, new int[] { 375, 667 },
            new int[] { 412, 915 }, new int[] { 430, 932 }, new int[] { 768, 1024 });

    /** JSON bodies answered to "send OTP" in the error-message tests (never sent to UAT). */
    private static final String INVALID_NUMBER_RESPONSE = "{\"status\":false,\"message\":\"Invalid mobile number\"}";
    private static final String RATE_LIMIT_RESPONSE = "{\"status\":false,\"message\":\"Too many OTP requests. Please try again later.\"}";
    private static final String SERVICE_ERROR_RESPONSE = "{\"status\":false,\"message\":\"Service unavailable\"}";

    private static final int IMAGE_WAIT_MS = 20_000;

    @DataProvider(name = "invalidMobileNumbers")
    public static Object[][] invalidMobileNumbers() {
        return new Object[][] { { "12345", "5 digits" }, { "987654321", "9 digits" } };
    }

    // ---------------------------------------------------------------- login card

    @Test(description = "SC_01_TC_001: Verify that the login card displays the brand logo.")
    public void tc001_loginCardBrandLogo() {
        ImageLog images = ImageLog.attach(page());
        LoginPage login = new LoginPage(page()).open();

        assertThat(login.heading()).isVisible();
        assertTrue(images.awaitLoaded(List.of(BRAND_LOGO), IMAGE_WAIT_MS).isEmpty(), "Brand logo image not loaded; failed: " + images.failed());
    }

    @Test(description = "SC_01_TC_002: Verify that the login card displays the \"Login\" heading.")
    public void tc002_loginHeading() {
        assertThat(new LoginPage(page()).open().heading()).isVisible();
    }

    @Test(description = "SC_01_TC_003: Verify that the login card displays the note \"Your details will be verified by a secure OTP\".")
    public void tc003_secureOtpNote() {
        assertThat(new LoginPage(page()).open().secureOtpNote()).isVisible();
    }

    @Test(description = "SC_01_TC_004: Verify that the Mobile Number field is mandatory on the login card.")
    public void tc004_mobileNumberMandatory() {
        LoginPage login = new LoginPage(page()).open();
        ApiInterceptor api = ApiInterceptor.answer(page(), 400, INVALID_NUMBER_RESPONSE);

        login.tapLoginViaOtp();
        page().waitForTimeout(1500);

        assertTrue(api.requests().isEmpty(), "Login via OTP sent a request with an empty mobile number: " + api.requests());
        assertTrue(!login.mobileValidationMessage().isEmpty() || login.mobileShowsRedError(),
                "No 'mandatory' message for the empty Mobile Number field after tapping Login via OTP");
    }

    @Test(description = "SC_01_TC_005: Verify that a valid 10-digit Indian mobile number is accepted in the Mobile Number field.")
    public void tc005_validMobileAccepted() {
        LoginPage login = new LoginPage(page()).open();

        login.enterMobile(VALID_FORMAT_MOBILE).leaveField();

        assertEquals(login.mobileInput().inputValue(), VALID_FORMAT_MOBILE);
        assertEquals(login.mobileValidationMessage(), "", "Validation message for a valid number");
    }

    @Test(dataProvider = "invalidMobileNumbers",
            description = "SC_01_TC_006: Verify that an invalid mobile number is rejected in the Mobile Number field.")
    public void tc006_invalidMobileRejected(String mobile, String reason) {
        LoginPage login = new LoginPage(page()).open();

        login.enterMobile(mobile).leaveField();

        assertFalse(login.mobileValidationMessage().isEmpty(), "No validation message for '" + mobile + "' (" + reason + ")");
    }

    @Test(description = "SC_01_TC_007: Verify that the country code selector is displayed with a default flag on the login card.")
    public void tc007_countryCodeDefaultFlag() {
        ImageLog images = ImageLog.attach(page());
        LoginPage login = new LoginPage(page()).open();

        assertTrue(images.awaitLoaded(List.of(INDIA_FLAG), IMAGE_WAIT_MS).isEmpty(), "Indian flag image not loaded");
        double spread = ImageUtils.contentSpread(login.countryFlagScreenshot());
        assertTrue(spread > 10, "Country code box shows no flag (brightness spread " + spread + ")");
    }

    @Test(description = "SC_01_TC_008: Verify that the country code is selectable on the login card.")
    public void tc008_countryCodeSelectable() {
        LoginPage login = new LoginPage(page()).open();
        FlutterApp app = new FlutterApp(page());
        Set<String> before = app.visibleTexts();

        login.tapCountryFlag();
        page().waitForTimeout(1500);

        Set<String> appeared = new TreeSet<>(app.visibleTexts());
        appeared.removeAll(before);
        assertFalse(appeared.isEmpty(), "Tapping the country code opened no country list (nothing new on screen)");
    }

    @Test(description = "SC_01_TC_009: Verify that the Date of Birth / Date of Incorporation field is mandatory on the login card.")
    public void tc009_dobMandatory() {
        LoginPage login = new LoginPage(page()).open();
        ApiInterceptor api = ApiInterceptor.answer(page(), 400, INVALID_NUMBER_RESPONSE);

        login.enterMobile(VALID_FORMAT_MOBILE).leaveField();
        login.tapLoginViaOtp();
        page().waitForTimeout(1500);

        assertTrue(api.requests().isEmpty(), "Login via OTP sent a request with an empty date of birth: " + api.requests());
        assertTrue(login.dobShowsError(), "No 'mandatory' message for the empty Date of Birth field after tapping Login via OTP");
    }

    @Test(description = "SC_01_TC_010: Verify that the Date of Birth / Date of Incorporation field opens a date picker.")
    public void tc010_dobOpensDatePicker() {
        LoginPage login = new LoginPage(page()).open();

        login.dobInput().click();
        page().waitForTimeout(1500);
        boolean fieldOpensPicker = login.datePicker().count() > 0;
        String calendarIcon = "";
        if (!fieldOpensPicker) {
            page().keyboard().press("Escape");
            login.calendarButton().click();
            page().waitForTimeout(1500);
            calendarIcon = login.datePicker().count() > 0 ? " (the calendar icon does open one)" : " (the calendar icon does not either)";
        }

        assertTrue(fieldOpensPicker, "Tapping the Date of Birth field opened no date picker" + calendarIcon);
    }

    @Test(description = "SC_01_TC_011: Verify that an inline error is displayed for an invalid Date of Birth / Date of Incorporation entry.")
    public void tc011_invalidDobInlineError() {
        LoginPage login = new LoginPage(page()).open();

        login.enterDob(INVALID_DATE_DIGITS).leaveField();

        assertTrue(login.dobShowsError(), "No inline error for the invalid date 31/02/2020");
    }

    @Test(description = "SC_01_TC_012: Verify that an inline error is displayed for an empty Date of Birth / Date of Incorporation entry.")
    public void tc012_emptyDobInlineError() {
        LoginPage login = new LoginPage(page()).open();
        ApiInterceptor.answer(page(), 400, INVALID_NUMBER_RESPONSE);

        login.enterMobile(VALID_FORMAT_MOBILE).leaveField();
        login.tapLoginViaOtp();
        page().waitForTimeout(1500);

        assertTrue(login.dobShowsError(), "No inline error for an empty Date of Birth after tapping Login via OTP");
    }

    @Test(groups = BLOCKED, description = "SC_01_TC_013: Verify that the Date of Birth / Date of Incorporation label adapts correctly for individual and entity contexts.")
    public void tc013_dobLabelIndividualAndEntity() {
        blocked("the login screen offers no way to switch between an individual and an entity, and the field label is not exposed to the page; need how the entity context is reached");
    }

    @Test(description = "SC_01_TC_014: Verify that \"Login via OTP\" button remains disabled until both Mobile Number and Date of Birth / Date of Incorporation fields are valid.")
    public void tc014_loginDisabledUntilValid() {
        LoginPage login = new LoginPage(page()).open();
        ApiInterceptor api = ApiInterceptor.answer(page(), 400, INVALID_NUMBER_RESPONSE);
        SoftAssert soft = new SoftAssert();

        soft.assertFalse(login.loginButtonLooksEnabled(), "Enabled with both fields empty");
        login.enterMobile(VALID_FORMAT_MOBILE).leaveField();
        soft.assertFalse(login.loginButtonLooksEnabled(), "Enabled with only the mobile number filled");
        login.tapLoginViaOtp();
        page().waitForTimeout(1500);
        soft.assertTrue(api.requests().isEmpty(), "Tapping the disabled button sent a request: " + api.requests());
        soft.assertEquals(new OtpSheet(page()).title().count(), 0, "OTP sheet opened from the disabled button");
        soft.assertAll();
    }

    @Test(description = "SC_01_TC_015: Verify that \"Login via OTP\" button is enabled when both Mobile Number and Date of Birth / Date of Incorporation fields are valid.")
    public void tc015_loginEnabledWhenValid() {
        LoginPage login = new LoginPage(page()).open();

        login.enterDetails(VALID_FORMAT_MOBILE, ADULT_DOB_DIGITS);

        assertTrue(login.loginButtonLooksEnabled(), "Login via OTP still looks disabled (grey) with a valid mobile number and date of birth");
    }

    // ---------------------------------------------------------------- OTP

    @Test(groups = SENDS_OTP, description = "SC_01_TC_016: Verify that tapping \"Login via OTP\" submits the details and triggers OTP generation.")
    public void tc016_loginViaOtpTriggersOtp() {
        LoginPage login = new LoginPage(page()).open();
        ApiInterceptor api = ApiInterceptor.record(page());

        login.enterValidDetails(TestAccounts.next());
        login.tapLoginViaOtp();
        new OtpSheet(page()).waitUntilShown();

        assertFalse(api.requests().isEmpty(), "No request was sent to generate the OTP");
        StepLog.step("OTP request(s): " + api.requests());
    }

    @Test(groups = SENDS_OTP, description = "SC_01_TC_017: Verify that tapping \"Login via OTP\" navigates the user to the OTP verification bottomsheet/popup.")
    public void tc017_loginViaOtpOpensOtpSheet() {
        LoginPage login = new LoginPage(page()).open();

        login.enterValidDetails(TestAccounts.next());
        login.tapLoginViaOtp();

        OtpSheet sheet = new OtpSheet(page()).waitUntilShown();
        assertThat(sheet.title()).isVisible();
        assertThat(sheet.validateButton()).isVisible();
    }

    @Test(groups = SENDS_OTP, description = "SC_01_TC_018: Verify that the entered Mobile Number is passed correctly to the OTP verification bottomsheet/popup.")
    public void tc018_mobileNumberOnOtpSheet() {
        LoginPage login = new LoginPage(page()).open();
        Account account = TestAccounts.next();

        login.enterValidDetails(account);
        login.tapLoginViaOtp();

        OtpSheet sheet = new OtpSheet(page()).waitUntilShown();
        assertTrue(sheet.maskedNumber(account.masked()).count() > 0, "OTP sheet does not show " + account.masked());
    }

    @Test(description = "SC_01_TC_019: Verify that an appropriate error message is displayed when OTP send fails due to an invalid number.")
    public void tc019_otpSendFailsInvalidNumber() {
        assertOtpFailureShowsMessage(400, INVALID_NUMBER_RESPONSE, "invalid number");
    }

    @Test(description = "SC_01_TC_020: Verify that an appropriate error message is displayed when OTP send fails due to rate limit.")
    public void tc020_otpSendFailsRateLimit() {
        assertOtpFailureShowsMessage(429, RATE_LIMIT_RESPONSE, "rate limit");
    }

    @Test(description = "SC_01_TC_021: Verify that an appropriate error message is displayed when OTP send fails due to a service error.")
    public void tc021_otpSendFailsServiceError() {
        assertOtpFailureShowsMessage(503, SERVICE_ERROR_RESPONSE, "service error");
    }

    // ---------------------------------------------------------------- explore products

    @Test(description = "SC_01_TC_022: Verify that the \"Explore Our Products\" heading is displayed on the pre-login screen.")
    public void tc022_exploreOurProductsHeading() {
        assertThat(new LoginPage(page()).open().exploreProductsHeading()).isVisible();
    }

    @Test(description = "SC_01_TC_023: Verify that tapping the Private Car product tile navigates to the correct product entry/browse flow.")
    public void tc023_privateCarTile() {
        LoginPage login = new LoginPage(page()).open();

        login.tapProductTile(PRIVATE_CAR);

        ProductJourneyPage journey = new ProductJourneyPage(page());
        assertThat(journey.fetchDetailsButton()).isVisible();
        assertEquals(journey.currentRoute(), ProductJourneyPage.CAR_ROUTE);
    }

    @Test(description = "SC_01_TC_024: Verify that tapping the Private Bike product tile navigates to the correct product entry/browse flow.")
    public void tc024_privateBikeTile() {
        LoginPage login = new LoginPage(page()).open();

        login.tapProductTile(PRIVATE_BIKE);

        ProductJourneyPage journey = new ProductJourneyPage(page());
        assertThat(journey.fetchDetailsButton()).isVisible();
        assertEquals(journey.currentRoute(), ProductJourneyPage.BIKE_ROUTE);
    }

    @Test(description = "SC_01_TC_025: Verify that tapping the Commercial Vehicle product tile navigates to the correct product entry/browse flow.")
    public void tc025_commercialVehicleTile() {
        assertTileOpensAFlow(COMMERCIAL_VEHICLE);
    }

    @Test(description = "SC_01_TC_026: Verify that tapping the Health product tile navigates to the correct product entry/browse flow.")
    public void tc026_healthTile() {
        assertTileOpensAFlow(HEALTH);
    }

    @Test(description = "SC_01_TC_027: Verify that tapping the Term Life product tile navigates to the correct product entry/browse flow.")
    public void tc027_termLifeTile() {
        assertTileOpensAFlow(TERM_LIFE);
    }

    // ---------------------------------------------------------------- background, design, layout

    @Test(groups = NON_AUTOMATABLE, description = "SC_01_TC_028: Verify that the animated doodle background renders smoothly on the pre-login screen.")
    public void tc028_doodleRendersSmoothly() {
        nonAutomatable("smoothness of the animation is judged by eye on a real device");
    }

    @Test(description = "SC_01_TC_029: Verify that the animated doodle background does not block user taps on the pre-login screen.")
    public void tc029_doodleDoesNotBlockTaps() {
        LoginPage login = new LoginPage(page()).open();

        login.enterMobile(VALID_FORMAT_MOBILE);
        login.enterDob(ADULT_DOB_DIGITS);

        assertEquals(login.mobileInput().inputValue(), VALID_FORMAT_MOBILE, "Mobile field did not take the taps/typing");
        assertEquals(login.dobInput().inputValue(), "01/01/1990", "DOB field did not take the taps/typing");
    }

    @Test(groups = NON_AUTOMATABLE, description = "SC_01_TC_030: Verify that the animated doodle background does not cause jank on the pre-login screen.")
    public void tc030_doodleNoJank() {
        nonAutomatable("jank is device- and GPU-dependent; judged on a real device");
    }

    @Test(groups = NON_AUTOMATABLE, description = "SC_01_TC_031: Verify that the animated doodle background degrades gracefully on low-end devices.")
    public void tc031_doodleLowEndDevice() {
        nonAutomatable("needs a physical low-end device");
    }

    @Test(groups = NON_AUTOMATABLE, description = "SC_01_TC_032: Verify that the pre-login screen matches the approved Figma design.")
    public void tc032_matchesFigma() {
        nonAutomatable("visual comparison with the approved Figma design");
    }

    @Test(description = "SC_01_TC_033: Verify that the pre-login screen content is reachable without clipping across supported device sizes.")
    public void tc033_noClippingAcrossSizes() {
        LoginPage login = new LoginPage(page()).open();
        SoftAssert soft = new SoftAssert();

        for (int[] size : DEVICE_SIZES) {
            StepLog.step("Screen size " + size[0] + "x" + size[1]);
            page().setViewportSize(size[0], size[1]);
            page().waitForTimeout(1200);
            for (Locator element : List.of(login.heading(), login.mobileInput(), login.dobInput(), login.loginViaOtpButton(),
                    login.exploreProductsHeading(), login.productTile(TERM_LIFE).first())) {
                element.scrollIntoViewIfNeeded();
                BoundingBox box = element.boundingBox();
                soft.assertNotNull(box, "Element not rendered at " + size[0] + "x" + size[1]);
                if (box != null) {
                    soft.assertTrue(box.x >= 0 && box.x + box.width <= size[0] + 1,
                            "Clipped horizontally at " + size[0] + "x" + size[1] + ": x=" + box.x + " width=" + box.width);
                }
            }
        }
        soft.assertAll();
    }

    @Test(description = "SC_01_TC_034: Verify that the pre-login screen scrolls if the content exceeds the visible area.")
    public void tc034_screenScrolls() {
        LoginPage login = new LoginPage(page()).open();
        page().setViewportSize(360, 640);
        page().waitForTimeout(1200);
        Locator lastTile = login.productTile(TERM_LIFE).first();
        double before = lastTile.boundingBox().y;

        page().mouse().move(180, 400);
        page().mouse().wheel(0, 600);
        page().waitForTimeout(1000);

        double after = lastTile.boundingBox().y;
        assertTrue(after < before, "Screen did not scroll: Term Life tile stayed at y=" + before);
    }

    // ---------------------------------------------------------------- loading, validation details

    @Test(description = "SC_01_TC_035: Verify that a loading state is shown while OTP is being requested.")
    public void tc035_loadingStateWhileOtpRequested() {
        assertLoadingShownWhileOtpPending();
    }

    @Test(description = "SC_01_TC_036: Verify that when the user enters a number in the Date of Birth field, the validation message is displayed.")
    public void tc036_dobSingleDigitValidation() {
        LoginPage login = new LoginPage(page()).open();

        login.enterDob(SINGLE_DIGIT_DOB).leaveField();

        assertTrue(login.dobShowsError(), "No validation message after entering one digit in Date of Birth");
    }

    @Test(description = "SC_01_TC_037: Verify the behavior when the user clicks \"LOGIN via OTP\" and \"Private Bike Insurance\" buttons at same time after entering valid mobile number and Date of Birth credentials.")
    public void tc037_simultaneousLoginAndBike() {
        List<String> pageErrors = new ArrayList<>();
        page().onPageError(pageErrors::add);
        LoginPage login = new LoginPage(page()).open();
        ApiInterceptor api = ApiInterceptor.hold(page()); // OTP request never leaves the browser

        login.enterDetails(VALID_FORMAT_MOBILE, ADULT_DOB_DIGITS);
        login.tapTogether(login.loginViaOtpButton(), login.productTile(PRIVATE_BIKE).first());
        page().waitForTimeout(3000);

        boolean otpRequested = !api.requests().isEmpty();
        boolean bikeOpened = ProductJourneyPage.BIKE_ROUTE.equals(login.currentRoute());
        api.answerHeld(503, SERVICE_ERROR_RESPONSE);
        assertEquals(pageErrors, List.of(), "JavaScript errors after the simultaneous taps");
        assertFalse(otpRequested && bikeOpened, "Both actions ran: an OTP was requested AND the Bike journey opened");
    }

    @Test(description = "SC_01_TC_038: mobile number is invalid message appears when user enters the invalid mobile number and message appears in red color.")
    public void tc038_invalidMobileRedMessage() {
        LoginPage login = new LoginPage(page()).open();

        login.enterMobile("12345").leaveField();

        SoftAssert soft = new SoftAssert();
        soft.assertEquals(login.mobileValidationMessage(), INVALID_MOBILE_MESSAGE_SPEC, "Message text");
        soft.assertTrue(login.mobileShowsRedError(), "Message is not shown in red");
        soft.assertAll();
    }

    @Test(description = "SC_01_TC_039: Verify that the user cannot enter more than 10 digits in the Mobile Number field")
    public void tc039_maxTenDigits() {
        LoginPage login = new LoginPage(page()).open();

        login.enterMobile(VALID_FORMAT_MOBILE + "12");

        assertEquals(login.mobileInput().inputValue(), VALID_FORMAT_MOBILE, "Field took more than 10 digits");
    }

    @Test(groups = SENDS_OTP, description = "SC_01_TC_040: Verify that multiple tap on Login via OTP button and check the behaviour.")
    public void tc040_multipleTapsLoginViaOtp() {
        LoginPage login = new LoginPage(page()).open();
        ApiInterceptor api = ApiInterceptor.record(page());

        login.enterValidDetails(TestAccounts.next());
        login.rapidTapLoginViaOtp(3);

        OtpSheet sheet = new OtpSheet(page()).waitUntilShown();
        page().waitForTimeout(2000);
        assertEquals(sheet.title().count(), 1, "OTP verification window shown more than once");
        assertEquals(api.requests().size(), 1, "OTP requested more than once: " + api.requests());
    }

    @Test(description = "SC_01_TC_041: Verify that Copy/paste invalid/valid data into mobile field")
    public void tc041_pasteMobileNumber() {
        LoginPage login = new LoginPage(page()).open();

        login.pasteIntoMobile(VALID_FORMAT_MOBILE);

        assertEquals(login.mobileInput().inputValue(), VALID_FORMAT_MOBILE, "Pasted number not accepted");
    }

    @Test(description = "SC_01_TC_042: Verify loading indicator while login request is processing")
    public void tc042_loadingIndicatorWhileLoginProcessing() {
        assertLoadingShownWhileOtpPending();
    }

    @Test(description = "SC_01_TC_043: Verify age validation message behavior after navigating back from Private Bike Insurance")
    public void tc043_ageValidationKeptAfterBack() {
        LoginPage login = new LoginPage(page()).open();
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("ddMMyyyy"));

        login.enterDob(today).leaveField();
        assertTrue(login.dobShowsError(), "No age message for today's date");
        login.tapProductTile(PRIVATE_BIKE);
        ProductJourneyPage journey = new ProductJourneyPage(page());
        journey.fetchDetailsButton().waitFor();
        journey.tapClose();
        login.waitUntilShown();

        String dobNow = login.dobInput().inputValue();
        assertTrue(dobNow.isEmpty() || login.dobShowsError(),
                "Back on Login the date '" + dobNow + "' is still filled but the 'You must be at least 18 years old' message is gone");
    }

    @Test(description = "SC_01_TC_044: Verify the behavior when the user clicks \"Private Car Insurance\" and \"Private Bike Insurance\" buttons at same time after entering valid mobile number and Date of Birth credentials.")
    public void tc044_simultaneousCarAndBike() {
        LoginPage login = new LoginPage(page()).open();

        login.tapTogether(login.productTile(PRIVATE_CAR).first(), login.productTile(PRIVATE_BIKE).first());
        page().waitForTimeout(3000);
        String opened = login.currentRoute();
        StepLog.step("Route after the simultaneous taps: #/" + opened);
        page().goBack();
        page().waitForTimeout(1500);

        assertNotEquals(opened, LoginPage.ROUTE, "Neither journey opened");
        assertEquals(login.currentRoute(), LoginPage.ROUTE,
                "Both journeys opened: one Back from #/" + opened + " landed on #/" + login.currentRoute() + " instead of the login screen");
    }

    @Test(description = "SC_01_TC_045: Verify that alphabets and special characters are rejected in the Mobile Number field.")
    public void tc045_lettersAndSymbolsRejected() {
        LoginPage login = new LoginPage(page()).open();

        login.enterMobile("abc@#$%");

        assertEquals(login.mobileInput().inputValue(), "", "Mobile field accepted letters/special characters");
    }

    @Test(description = "SC_01_TC_046: Verify that a mobile number with fewer than 10 digits is rejected and the Login via OTP button stays disabled.")
    public void tc046_shortMobileRejected() {
        LoginPage login = new LoginPage(page()).open();

        login.enterDetails("98765", ADULT_DOB_DIGITS);

        SoftAssert soft = new SoftAssert();
        soft.assertFalse(login.mobileValidationMessage().isEmpty(), "No inline error for a 5-digit number");
        soft.assertFalse(login.loginButtonLooksEnabled(), "Login via OTP looks enabled with a 5-digit number");
        soft.assertAll();
    }

    @Test(groups = NON_AUTOMATABLE, description = "SC_01_TC_047: Verify that the onscreen keyboard does not hide the Mobile Number/Date of Birth fields or the Login via OTP button.")
    public void tc047_keyboardDoesNotHideFields() {
        nonAutomatable("browser automation has no on-screen keyboard; checked on a real device");
    }

    // ---------------------------------------------------------------- helpers

    /** "Send OTP" answered with an error inside the browser: an error message must appear and no OTP sheet. */
    private void assertOtpFailureShowsMessage(int status, String body, String kind) {
        LoginPage login = new LoginPage(page()).open();
        FlutterApp app = new FlutterApp(page());
        login.enterDetails(VALID_FORMAT_MOBILE, ADULT_DOB_DIGITS);
        Set<String> before = app.visibleTexts();
        ApiInterceptor api = ApiInterceptor.answer(page(), status, body);

        login.tapLoginViaOtp();
        page().waitForTimeout(3000);

        Set<String> appeared = new TreeSet<>(app.visibleTexts());
        appeared.removeAll(before);
        StepLog.step("New on screen after the failed OTP send: " + appeared);
        assertFalse(api.requests().isEmpty(), "No OTP request was made");
        assertEquals(new OtpSheet(page()).title().count(), 0, "OTP sheet opened although sending the OTP failed (" + kind + ")");
        assertFalse(appeared.isEmpty(), "No error message shown when sending the OTP failed (" + kind + ", HTTP " + status + ")");
    }

    /** "Send OTP" kept pending inside the browser: the app must show a loading state meanwhile. */
    private void assertLoadingShownWhileOtpPending() {
        LoginPage login = new LoginPage(page()).open();
        login.enterDetails(VALID_FORMAT_MOBILE, ADULT_DOB_DIGITS);
        ApiInterceptor api = ApiInterceptor.hold(page());

        login.tapLoginViaOtp();
        page().waitForTimeout(800);

        boolean shown = login.loadingIndicator().count() > 0;
        boolean requested = !api.requests().isEmpty();
        api.answerHeld(503, SERVICE_ERROR_RESPONSE);
        assertTrue(requested, "No OTP request was made");
        assertTrue(shown, "No loading indicator while the OTP request was pending");
    }

    /** A product tile must leave the login screen for its product flow. */
    private void assertTileOpensAFlow(String tile) {
        LoginPage login = new LoginPage(page()).open();

        login.tapProductTile(tile);
        page().waitForTimeout(3000);

        assertNotEquals(login.currentRoute(), LoginPage.ROUTE, "Tapping '" + tile + "' did not open any product flow (stayed on the login screen)");
    }
}
