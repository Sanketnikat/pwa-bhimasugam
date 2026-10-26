package factory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import utils.FrameworkConfig;

/**
 * Prepares Chrome on an Android emulator or USB phone so Playwright can drive it
 * over the DevTools protocol, using adb.
 *
 * Chrome only opens its DevTools socket while it is running, and its first-run
 * screens would cover the page, so Chrome is started with those screens disabled
 * before the port is forwarded to this machine.
 */
public final class AndroidDevice {

    private static final String CHROME = "com.android.chrome";
    private static final String CHROME_FLAGS = "_ --disable-fre --no-default-browser-check --no-first-run --disable-features=SigninPromo";

    private static boolean prepared;

    private AndroidDevice() {
    }

    /** Returns the local DevTools URL for Chrome on the device, preparing Chrome once per run. */
    public static synchronized String chromeDevToolsUrl() {
        int port = Integer.parseInt(FrameworkConfig.get("android.cdp.port"));
        if (!prepared) {
            requireDevice();
            // Chrome reads this flags file only when it is the debug app (works on emulators and debuggable builds)
            adb("shell", "echo '" + CHROME_FLAGS + "' > /data/local/tmp/chrome-command-line");
            adb("shell", "am", "set-debug-app", "--persistent", CHROME);
            adb("shell", "am", "force-stop", CHROME);
            adb("shell", "am", "start", "-n", CHROME + "/com.google.android.apps.chrome.Main", "-d", "about:blank");
            prepared = true;
        }
        adb("forward", "tcp:" + port, "localabstract:chrome_devtools_remote");
        return "http://127.0.0.1:" + port;
    }

    /** Fails with a clear message when no emulator or phone is connected and fully booted. */
    private static void requireDevice() {
        String devices = adbGlobal("devices");
        boolean any = devices.lines().skip(1).anyMatch(line -> line.endsWith("\tdevice"));
        if (!any) {
            throw new IllegalStateException("No Android emulator or phone connected (adb devices:\n" + devices + ")\n"
                    + "Start one first, e.g. Android Studio > Device Manager > Pixel_API_35 > Run, "
                    + "or: ~/Library/Android/sdk/emulator/emulator -avd Pixel_API_35");
        }
        if (!"1".equals(adb("shell", "getprop", "sys.boot_completed"))) {
            throw new IllegalStateException("Android device is still booting - wait for the home screen and run again");
        }
    }

    /** Runs an adb command that is not tied to one device, such as "devices". */
    private static String adbGlobal(String... args) {
        return run(prepend(FrameworkConfig.get("adb.path"), args));
    }

    private static List<String> prepend(String first, String... rest) {
        List<String> command = new ArrayList<>();
        command.add(first);
        command.addAll(List.of(rest));
        return command;
    }

    /** Runs adb against the configured device (any single connected device when android.serial is "auto"). */
    static String adb(String... args) {
        List<String> command = new ArrayList<>();
        command.add(FrameworkConfig.get("adb.path"));
        String serial = FrameworkConfig.get("android.serial");
        if (!"auto".equalsIgnoreCase(serial)) {
            command.add("-s");
            command.add(serial);
        }
        command.addAll(List.of(args));
        return run(command);
    }

    private static String run(List<String> command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("adb timed out: " + String.join(" ", command));
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0) {
                throw new IllegalStateException("adb failed (" + process.exitValue() + "): " + String.join(" ", command) + "\n" + output);
            }
            return output;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not run adb - set adb.path in config.properties", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
