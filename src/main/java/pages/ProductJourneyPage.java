package pages;

import java.util.regex.Pattern;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.BoundingBox;
import utils.FlutterApp;
import utils.StepLog;

/** A product's quote journey opened from a product tile, e.g. {@code #/journey/motor/fourWheeler}. */
public class ProductJourneyPage {

    public static final String CAR_ROUTE = "journey/motor/fourWheeler";
    public static final String BIKE_ROUTE = "journey/motor/twoWheeler";

    private final Page page;

    public ProductJourneyPage(Page page) {
        this.page = page;
    }

    public String currentRoute() {
        return new FlutterApp(page).currentRoute();
    }

    /** "Fetch Car Details" / "Fetch Bike Details", the motor journey's main action. */
    public Locator fetchDetailsButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("^Fetch .* Details$")));
    }

    /** Close (X) at the top right of the journey header, which cancels back to where the user came from. */
    public void tapClose() {
        StepLog.step("Tap close (X) on the journey header");
        Locator buttons = page.locator("flt-semantics[role=button]");
        int width = new FlutterApp(page).windowWidth();
        for (int i = 0; i < buttons.count(); i++) {
            BoundingBox box = buttons.nth(i).boundingBox();
            if (box != null && box.y < 140 && box.x > width * 0.6) {
                buttons.nth(i).click();
                return;
            }
        }
        throw new IllegalStateException("Close button not found in the journey header");
    }
}
