package pages;

import java.util.regex.Pattern;

import com.microsoft.playwright.Keyboard;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import utils.StepLog;

/** "OTP Verification" bottom sheet shown after "Login via OTP". */
public class OtpSheet {

    /** How long "Login via OTP" may take to open the sheet (UAT generates and texts the OTP first). */
    private static final double SHOW_TIMEOUT_MS = 30_000;

    private final Page page;

    public OtpSheet(Page page) {
        this.page = page;
    }

    public Locator title() {
        return page.getByText("OTP Verification", new Page.GetByTextOptions().setExact(true));
    }

    public OtpSheet waitUntilShown() {
        title().waitFor(new Locator.WaitForOptions().setTimeout(SHOW_TIMEOUT_MS));
        return this;
    }

    /** Text on the sheet containing the masked mobile number, e.g. "89******71". */
    public Locator maskedNumber(String masked) {
        Pattern contains = Pattern.compile(Pattern.quote(masked));
        return page.getByText(contains).or(page.locator("flt-semantics[aria-label*='" + masked + "']"));
    }

    /** The OTP field: the last input on screen once the sheet is open. */
    public Locator otpInput() {
        return page.locator("input").last();
    }

    public Locator validateButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("^\\s*Validate\\s*$")));
    }

    public void enterOtpAndValidate(String otp) {
        StepLog.step("Enter OTP and tap Validate");
        otpInput().click();
        page.waitForTimeout(300);
        page.keyboard().type(otp, new Keyboard.TypeOptions().setDelay(60));
        page.waitForTimeout(500);
        validateButton().click();
    }
}
