package factory;

/**
 * Where a test runs.
 *
 * Emulated platforms run a desktop browser engine locally through Playwright,
 * pretending to be a phone. Device values mirror Playwright's "Pixel 7" and
 * "iPhone 15" descriptors, which Playwright Java does not ship.
 *
 * {@link #ANDROID_CHROME_DEVICE} drives the real Chrome app on an Android emulator
 * or USB phone, connected through adb and Chrome's DevTools protocol.
 *
 * {@link #BROWSERSTACK_ANDROID} drives Chrome on a real Android phone in BrowserStack's
 * cloud (see {@link BrowserStack}); it needs the {@code browserstack} Maven profile.
 */
public enum Platform {

    ANDROID_CHROME_EMULATION(Mode.EMULATED, Engine.CHROMIUM, 412, 839, 2.625,
            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"),

    IOS_SAFARI_EMULATION(Mode.EMULATED, Engine.WEBKIT, 393, 659, 3,
            "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1"),

    /** Real Chrome on an Android emulator or phone; screen size and user agent come from the device. */
    ANDROID_CHROME_DEVICE(Mode.ANDROID_DEVICE, Engine.CHROMIUM, 0, 0, 0, null),

    /** Real Chrome on a BrowserStack Android phone (browserstack.device in config.properties). */
    BROWSERSTACK_ANDROID(Mode.BROWSERSTACK, Engine.CHROMIUM, 0, 0, 0, null);

    public enum Mode { EMULATED, ANDROID_DEVICE, BROWSERSTACK }

    public enum Engine { CHROMIUM, WEBKIT }

    public final Mode mode;
    public final Engine engine;
    public final int viewportWidth;
    public final int viewportHeight;
    public final double deviceScaleFactor;
    public final String userAgent;

    Platform(Mode mode, Engine engine, int viewportWidth, int viewportHeight, double deviceScaleFactor, String userAgent) {
        this.mode = mode;
        this.engine = engine;
        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewportHeight;
        this.deviceScaleFactor = deviceScaleFactor;
        this.userAgent = userAgent;
    }

    /** Accepts the enum name in any case, e.g. "android_chrome_emulation". */
    public static Platform from(String name) {
        return valueOf(name.trim().toUpperCase());
    }
}
