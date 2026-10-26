package factory;

import java.lang.reflect.InvocationTargetException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonObject;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import utils.FrameworkConfig;
import utils.StepLog;

/**
 * Real Android phones in BrowserStack's cloud, driven through BrowserStack's
 * Playwright fork (Maven profile {@code browserstack}), which adds
 * {@code playwright.android()}. The fork's Android API is called by reflection so
 * the project still compiles against standard Playwright, which has no android().
 *
 * Credentials come only from the environment variables BROWSERSTACK_USERNAME and
 * BROWSERSTACK_ACCESS_KEY, never from files, and are removed from any error message.
 * The phone opens the app over BrowserStack's network, not this machine's.
 */
public final class BrowserStack {

    private static final String ENDPOINT = "wss://cdp.browserstack.com/playwright?caps=";
    /** Version of BrowserStack's Playwright fork in the browserstack Maven profile. */
    private static final String CLIENT_VERSION = "1.52.1";
    private static final String CHROME = "com.android.chrome";

    private BrowserStack() {
    }

    /** Connects to a BrowserStack Android phone; the result is the fork's AndroidDevice (close it when done). */
    static AutoCloseable connectAndroid(Playwright playwright, String sessionName) {
        String user = credential("BROWSERSTACK_USERNAME");
        String key = credential("BROWSERSTACK_ACCESS_KEY");
        JsonObject caps = new JsonObject();
        caps.addProperty("platformName", "android");
        caps.addProperty("deviceName", FrameworkConfig.get("browserstack.device"));
        caps.addProperty("browserName", "chrome");
        caps.addProperty("realMobile", "true");
        caps.addProperty("buildName", FrameworkConfig.get("browserstack.build") + " " + FrameworkConfig.runStamp());
        caps.addProperty("sessionName", sessionName);
        caps.addProperty("client.playwrightVersion", CLIENT_VERSION);
        caps.addProperty("browserstack.username", user);
        caps.addProperty("browserstack.accessKey", key);
        String endpoint = ENDPOINT + URLEncoder.encode(caps.toString(), StandardCharsets.UTF_8);

        StepLog.step("Connecting to BrowserStack: " + FrameworkConfig.get("browserstack.device") + ", Chrome");
        Object android;
        try {
            android = Playwright.class.getMethod("android").invoke(playwright);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("BrowserStack runs need BrowserStack's Playwright fork: add -Pbrowserstack to the Maven command");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not reach the fork's android() API", e);
        }
        return (AutoCloseable) call("com.microsoft.playwright.Android", "connect", android, key, endpoint);
    }

    /** Restarts Chrome on the phone and returns its browser context. */
    static BrowserContext launchChrome(AutoCloseable device) {
        call("com.microsoft.playwright.AndroidDevice", "shell", device, null, "am force-stop " + CHROME);
        return (BrowserContext) call("com.microsoft.playwright.AndroidDevice", "launchBrowser", device, null);
    }

    /** Shows the test result on the BrowserStack dashboard (no failure details, which may hold personal data). */
    static void markSession(Page page, boolean passed) {
        String status = passed ? "passed" : "failed";
        page.evaluate("_ => {}", "browserstack_executor: {\"action\":\"setSessionStatus\",\"arguments\":{\"status\":\""
                + status + "\",\"reason\":\"See the local HTML report\"}}");
    }

    private static String credential(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is not set; export BROWSERSTACK_USERNAME and BROWSERSTACK_ACCESS_KEY before running");
        }
        return value.trim();
    }

    /** Calls a method of one of the fork's interfaces; {@code secret}, when given, is removed from any error message. */
    private static Object call(String type, String method, Object target, String secret, Object... args) {
        try {
            Class<?>[] types = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) {
                types[i] = args[i].getClass();
            }
            return Class.forName(type).getMethod(method, types).invoke(target, args);
        } catch (InvocationTargetException e) {
            String message = String.valueOf(e.getCause().getMessage());
            if (secret != null) {
                message = message.replace(secret, "***").replace(URLEncoder.encode(secret, StandardCharsets.UTF_8), "***");
            }
            throw new IllegalStateException("BrowserStack " + method + " failed: " + message.lines().findFirst().orElse(""));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("BrowserStack's Playwright fork has no " + type + "." + method + "; add -Pbrowserstack", e);
        }
    }
}
