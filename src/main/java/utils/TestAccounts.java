package utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

/**
 * UAT test accounts (src/test/resources/testdata/test-accounts.csv), handed out
 * least recently used first. UAT silently ignores "Login via OTP" for a number
 * that had several OTPs in a short time, so each OTP goes to the most rested
 * account. Last-use times are kept across runs in {@code account.usage.file}.
 */
public final class TestAccounts {

    /** One test account. {@link #dobDigits()} is how the DOB field is typed (it formats itself). */
    public record Account(String mobile, LocalDate dob) {

        public String dobDigits() {
            return dob.format(DateTimeFormatter.ofPattern("ddMMyyyy"));
        }

        /** As the OTP sheet shows it, e.g. "89******71". */
        public String masked() {
            return mobile.substring(0, 2) + "******" + mobile.substring(mobile.length() - 2);
        }

        public boolean isAdult() {
            return !dob.plusYears(18).isAfter(LocalDate.now());
        }
    }

    private static List<Account> accounts;

    private TestAccounts() {
    }

    /** The adult account used least recently; marks it as used now. */
    public static synchronized Account next() {
        Properties usage = loadUsage();
        Account chosen = all().stream()
                .filter(Account::isAdult)
                .min(Comparator.comparingLong(a -> Long.parseLong(usage.getProperty(a.mobile(), "0"))))
                .orElseThrow(() -> new IllegalStateException("No adult test account in test-accounts.csv"));
        usage.setProperty(chosen.mobile(), String.valueOf(System.currentTimeMillis()));
        saveUsage(usage);
        StepLog.step("Using test account " + chosen.masked());
        return chosen;
    }

    public static synchronized List<Account> all() {
        if (accounts == null) {
            List<Account> list = new ArrayList<>();
            try (InputStream in = TestAccounts.class.getClassLoader().getResourceAsStream("testdata/test-accounts.csv")) {
                if (in == null) {
                    throw new IllegalStateException("testdata/test-accounts.csv not found on the classpath");
                }
                for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }
                    String[] parts = trimmed.split(",");
                    list.add(new Account(parts[0].trim(), LocalDate.parse(parts[1].trim())));
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            accounts = List.copyOf(list);
        }
        return accounts;
    }

    // ------------------------------------------------------------ OTP (UAT texts a new one on every login)

    private static final java.util.regex.Pattern OTP_CODE = java.util.regex.Pattern.compile("(?<!\\d)(\\d{6})(?!\\d)");

    /** Empties {@code otp.file} before an OTP is requested, so an old code is never used. */
    public static void clearOtp() {
        try {
            Files.createDirectories(otpFile().toAbsolutePath().getParent());
            Files.writeString(otpFile(), "");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Waits up to {@code otp.wait.seconds} for the tester to write the OTP sent to {@code maskedMobile} into {@code otp.file}. */
    public static String waitForOtp(String maskedMobile) {
        long waitSeconds = Long.parseLong(FrameworkConfig.get("otp.wait.seconds"));
        System.out.println("\n================================================================\n"
                + "  OTP sent to " + maskedMobile + ".\n"
                + "  Type the 6-digit OTP into: " + otpFile().toAbsolutePath() + "\n"
                + "  (waiting up to " + waitSeconds + " s)\n"
                + "================================================================");
        StepLog.step("Waiting for OTP for " + maskedMobile + " in " + otpFile());
        long deadline = System.currentTimeMillis() + waitSeconds * 1000;
        while (System.currentTimeMillis() < deadline) {
            try {
                if (Files.exists(otpFile())) {
                    java.util.regex.Matcher m = OTP_CODE.matcher(Files.readString(otpFile()));
                    if (m.find()) {
                        StepLog.step("OTP received");
                        return m.group(1);
                    }
                }
                Thread.sleep(1000);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new IllegalStateException("No OTP written to " + otpFile().toAbsolutePath() + " within " + waitSeconds + " s");
    }

    private static Path otpFile() {
        return Path.of(FrameworkConfig.get("otp.file"));
    }

    // ------------------------------------------------------------ usage file

    private static Path usageFile() {
        return Path.of(FrameworkConfig.get("account.usage.file"));
    }

    private static Properties loadUsage() {
        Properties usage = new Properties();
        if (Files.exists(usageFile())) {
            try (Reader reader = Files.newBufferedReader(usageFile())) {
                usage.load(reader);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return usage;
    }

    private static void saveUsage(Properties usage) {
        try {
            Files.createDirectories(usageFile().toAbsolutePath().getParent());
            try (Writer writer = Files.newBufferedWriter(usageFile())) {
                usage.store(writer, "Last OTP login per test account (epoch ms)");
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
