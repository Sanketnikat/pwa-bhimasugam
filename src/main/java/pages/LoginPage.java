package pages;

import java.util.List;
import java.util.regex.Pattern;

import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Keyboard;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.BoundingBox;
import com.microsoft.playwright.options.Clip;
import utils.FlutterApp;
import utils.ImageUtils;
import utils.StepLog;
import utils.TestAccounts.Account;

/**
 * Pre-login / login screen ({@code #/login}): login card (mobile number with
 * country flag, date of birth, Login via OTP) and "Explore Our Products".
 *
 * Inputs are taken by order (mobile first, then DOB) because the app labels them
 * with their placeholder, which disappears once text is typed. The calendar icon
 * has no label and is found by position next to the date of birth field.
 * DOB error messages are drawn but not exposed to the page, so they are detected
 * by the field growing and red text appearing.
 */
public class LoginPage {

    public static final String ROUTE = "login";

    private final Page page;
    private final FlutterApp app;

    public LoginPage(Page page) {
        this.page = page;
        this.app = new FlutterApp(page);
    }

    public LoginPage open() {
        app.open(ROUTE);
        return waitUntilShown();
    }

    /** Current hash route, e.g. "login" or "journey/motor/fourWheeler". */
    public String currentRoute() {
        return app.currentRoute();
    }

    public LoginPage waitUntilShown() {
        loginViaOtpButton().waitFor();
        return this;
    }

    // ------------------------------------------------------------ card content

    public Locator heading() {
        return page.getByText("Login", new Page.GetByTextOptions().setExact(true));
    }

    public Locator secureOtpNote() {
        return page.getByText("Your details will be verified by a secure OTP", new Page.GetByTextOptions().setExact(true));
    }

    public Locator exploreProductsHeading() {
        return page.getByText("Explore Our Products", new Page.GetByTextOptions().setExact(true));
    }

    /** The whole mobile field; its accessible label also carries the validation message. */
    public Locator mobileField() {
        return page.locator("flt-semantics[aria-label^='Mobile Number']");
    }

    public Locator mobileInput() {
        return page.locator("input").first();
    }

    public Locator dobInput() {
        return page.locator("input").nth(1);
    }

    public Locator loginViaOtpButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("^\\s*Login via OTP\\s*$")));
    }

    /** Product tile under "Explore Our Products"; the live tiles are buttons, the others images. UAT may decorate names. */
    public Locator productTile(String name) {
        Pattern contains = Pattern.compile(Pattern.quote(name));
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(contains))
                .or(page.getByRole(AriaRole.IMG, new Page.GetByRoleOptions().setName(contains)));
    }

    /** Calendar icon in the date of birth field. */
    public Locator calendarButton() {
        return unlabelledButtonInRowOf(dobInput());
    }

    /** Material date picker (dialog with OK/Cancel) or a "Select date" title. */
    public Locator datePicker() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("OK"))
                .or(page.getByText("Select date"))
                .or(page.getByRole(AriaRole.DIALOG));
    }

    /** Progress indicator or "Please wait" shown while a request is pending. */
    public Locator loadingIndicator() {
        return page.getByRole(AriaRole.PROGRESSBAR).or(page.getByText(Pattern.compile("Please wait")));
    }

    // ------------------------------------------------------------ actions

    public LoginPage enterMobile(String text) {
        StepLog.step("Type mobile number \"" + text + "\"");
        typeInto(mobileInput(), text);
        return this;
    }

    /** Types the date as digits, e.g. "04052000"; the field adds the slashes itself. */
    public LoginPage enterDob(String digits) {
        StepLog.step("Type date of birth \"" + digits + "\"");
        typeInto(dobInput(), digits);
        return this;
    }

    /** Types a mobile number and a date of birth, then leaves the field. For made-up values only: both are logged. */
    public LoginPage enterDetails(String mobile, String dobDigits) {
        enterMobile(mobile);
        enterDob(dobDigits);
        return leaveField();
    }

    /** Types a real test account's details; the log shows only the masked number and never the date of birth. */
    public LoginPage enterValidDetails(Account account) {
        StepLog.step("Type mobile number \"" + account.masked() + "\" (masked)");
        typeInto(mobileInput(), account.mobile());
        StepLog.step("Type date of birth (not logged)");
        typeInto(dobInput(), account.dobDigits());
        return leaveField();
    }

    /** Moves focus on (Tab), which triggers the field's validation. */
    public LoginPage leaveField() {
        page.keyboard().press("Tab");
        page.waitForTimeout(500);
        return this;
    }

    public void clear(Locator field) {
        field.click();
        page.keyboard().press("ControlOrMeta+A");
        page.keyboard().press("Backspace");
    }

    public void tapLoginViaOtp() {
        StepLog.step("Tap Login via OTP");
        loginViaOtpButton().click();
    }

    /** Taps Login via OTP {@code times} times as fast as possible, at the button's position on screen. */
    public void rapidTapLoginViaOtp(int times) {
        BoundingBox box = loginViaOtpButton().boundingBox();
        StepLog.step("Tap Login via OTP " + times + " times rapidly");
        for (int i = 0; i < times; i++) {
            page.mouse().click(box.x + box.width / 2, box.y + box.height / 2);
        }
    }

    /** Clicks two elements in the same instant (one JavaScript task), like two fingers tapping together. */
    public void tapTogether(Locator first, Locator second) {
        StepLog.step("Tap two buttons at the same time");
        ElementHandle a = first.elementHandle();
        ElementHandle b = second.elementHandle();
        page.evaluate("([a, b]) => { a.click(); b.click(); }", List.of(a, b));
    }

    /** Puts {@code text} on the clipboard and pastes it into the mobile field (ControlOrMeta+V). */
    public void pasteIntoMobile(String text) {
        try {
            page.context().grantPermissions(List.of("clipboard-read", "clipboard-write"));
        } catch (RuntimeException e) {
            StepLog.step("Clipboard permissions not supported by this browser; trying anyway");
        }
        mobileInput().click();
        page.evaluate("text => navigator.clipboard.writeText(text)", text);
        StepLog.step("Paste " + text + " into the mobile field");
        page.keyboard().press("ControlOrMeta+V");
        page.waitForTimeout(800);
    }

    public void tapProductTile(String name) {
        StepLog.step("Tap product tile \"" + name + "\"");
        productTile(name).first().scrollIntoViewIfNeeded();
        productTile(name).first().click();
    }

    /** Taps where the country flag is drawn (left of the mobile input). */
    public void tapCountryFlag() {
        StepLog.step("Tap the country code flag");
        Clip flag = countryFlagArea();
        page.mouse().click(flag.x + flag.width / 2, flag.y + flag.height / 2);
    }

    // ------------------------------------------------------------ state

    /** Validation text under the mobile field right now, or "" when there is none. */
    public String mobileValidationMessage() {
        String label = mobileField().first().getAttribute("aria-label");
        int newline = label == null ? -1 : label.indexOf('\n');
        return newline < 0 ? "" : label.substring(newline + 1).trim();
    }

    /** DOB field shows an error: it grows to make room for the message, drawn in red. */
    public boolean dobShowsError() {
        BoundingBox box = dobInput().boundingBox();
        return box.height > 64 && ImageUtils.redShare(page.screenshot(new Page.ScreenshotOptions().setClip(clipOf(box)))) > 0.002;
    }

    /** Mobile field shows a red error message. */
    public boolean mobileShowsRedError() {
        BoundingBox box = mobileField().first().boundingBox();
        return ImageUtils.redShare(page.screenshot(new Page.ScreenshotOptions().setClip(clipOf(box)))) > 0.002;
    }

    /** "Login via OTP" is drawn in colour (enabled) rather than grey (disabled). */
    public boolean loginButtonLooksEnabled() {
        BoundingBox box = loginViaOtpButton().boundingBox();
        Clip middle = new Clip(box.x + box.width * 0.05, box.y + box.height * 0.1, box.width * 0.2, box.height * 0.3);
        return !ImageUtils.isGrey(page.screenshot(new Page.ScreenshotOptions().setClip(middle)));
    }

    public byte[] countryFlagScreenshot() {
        return page.screenshot(new Page.ScreenshotOptions().setClip(countryFlagArea()));
    }

    /** Screenshot of the photo grid at the top of the login screen. */
    public byte[] gridScreenshot() {
        return OnboardingPage.topOfScreenScreenshot(page, 0.2);
    }

    // ------------------------------------------------------------ helpers

    private void typeInto(Locator field, String text) {
        field.click();
        page.waitForTimeout(300); // Flutter attaches its keyboard listener just after focus
        page.keyboard().type(text, new Keyboard.TypeOptions().setDelay(30));
        page.waitForTimeout(300);
    }

    /** The flag box between the left edge of the mobile field and its input. */
    private Clip countryFlagArea() {
        BoundingBox field = mobileField().first().boundingBox();
        BoundingBox input = mobileInput().boundingBox();
        return new Clip(field.x, input.y, Math.max(10, input.x - field.x - 8), input.height);
    }

    private Locator unlabelledButtonInRowOf(Locator input) {
        BoundingBox row = input.boundingBox();
        Locator buttons = page.locator("flt-semantics[role=button]");
        for (int i = 0; i < buttons.count(); i++) {
            Locator button = buttons.nth(i);
            BoundingBox box = button.boundingBox();
            if (box != null && button.textContent().isBlank() && Math.abs(box.y - row.y) < 12) {
                return button;
            }
        }
        throw new IllegalStateException("No unlabelled button next to the field at y=" + row.y);
    }

    private static Clip clipOf(BoundingBox box) {
        return new Clip(box.x, box.y, box.width, box.height);
    }
}
