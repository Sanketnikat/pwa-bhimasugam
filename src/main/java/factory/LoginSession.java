package factory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.PlaywrightException;
import pages.HomePage;
import pages.LoginPage;
import pages.OtpSheet;
import utils.FrameworkConfig;
import utils.StepLog;
import utils.TestAccounts;
import utils.TestAccounts.Account;

/**
 * Logged-in browser state (cookies, local storage, IndexedDB) for tests that need
 * a logged-in user, e.g. the Home tab. One OTP login per platform; the state is
 * saved to {@code session.dir/<PLATFORM>.json} and reused by every test, and by
 * later runs while it is younger than {@code session.max.age.minutes} and still
 * opens the Home tab. That keeps OTPs to a minimum (UAT locks a number out after
 * several OTPs in a short time).
 */
public final class LoginSession {

    private static final Map<Platform, Path> CHECKED = new HashMap<>();
    /** A failed login is not retried in the same run: every retry would text another OTP. */
    private static final Map<Platform, RuntimeException> FAILED = new HashMap<>();

    private LoginSession() {
    }

    /** Saved logged-in state for {@code platform}, logging in (with an OTP) when there is no usable one. */
    public static synchronized Path storageState(Platform platform) {
        Path cached = CHECKED.get(platform);
        if (cached != null) {
            return cached;
        }
        if (platform.mode != Platform.Mode.EMULATED) {
            throw new IllegalStateException("Saved login sessions are only supported on emulated platforms");
        }
        if (FAILED.containsKey(platform)) {
            throw FAILED.get(platform);
        }
        Path file = Path.of(FrameworkConfig.get("session.dir"), platform.name() + ".json");
        try {
            if (isFresh(file) && stillLoggedIn(platform, file)) {
                StepLog.step("Reusing saved login session " + file);
            } else {
                logIn(platform, file);
            }
        } catch (RuntimeException e) {
            FAILED.put(platform, e);
            throw e;
        }
        CHECKED.put(platform, file);
        return file;
    }

    private static boolean isFresh(Path file) {
        try {
            long maxAgeMinutes = Long.parseLong(FrameworkConfig.get("session.max.age.minutes"));
            return Files.exists(file) && Duration.between(Files.getLastModifiedTime(file).toInstant(), Instant.now()).toMinutes() < maxAgeMinutes;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean stillLoggedIn(Platform platform, Path file) {
        try (BrowserSession session = new BrowserSession(platform, file, false)) {
            new HomePage(session.page()).open();
            return true;
        } catch (PlaywrightException e) {
            StepLog.step("Saved login session no longer works; logging in again");
            return false;
        }
    }

    private static void logIn(Platform platform, Path file) {
        StepLog.step("Logging in for " + platform + " (one OTP for this platform)");
        try (BrowserSession session = new BrowserSession(platform, null, false)) {
            Account account = TestAccounts.next();
            LoginPage login = new LoginPage(session.page()).open();
            login.enterValidDetails(account);
            TestAccounts.clearOtp();
            login.tapLoginViaOtp();
            OtpSheet sheet = new OtpSheet(session.page()).waitUntilShown();
            sheet.enterOtpAndValidate(TestAccounts.waitForOtp(account.masked()));
            new HomePage(session.page()).greeting().first().waitFor(new Locator.WaitForOptions().setTimeout(60_000));
            Files.createDirectories(file.toAbsolutePath().getParent());
            BrowserContext context = session.context();
            context.storageState(new BrowserContext.StorageStateOptions().setPath(file).setIndexedDB(true));
            StepLog.step("Logged in as " + account.masked() + "; session saved to " + file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
