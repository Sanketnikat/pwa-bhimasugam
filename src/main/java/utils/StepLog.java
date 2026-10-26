package utils;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/**
 * Step-by-step log of what a test is doing: printed live to the console (Maven and
 * Eclipse) with the time and platform, and added to the HTML report's test entry.
 */
public final class StepLog {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    /** Where the report wants steps of the current test; set by the report listener. */
    private static volatile Consumer<String> reportSink;
    private static volatile String context = "";

    private StepLog() {
    }

    public static void step(String message) {
        System.out.println("[" + LocalTime.now().format(TIME) + "] " + context + message);
        Consumer<String> sink = reportSink;
        if (sink != null) {
            sink.accept(message);
        }
    }

    /** Called by the report listener when a test starts / ends. */
    public static void startTest(String testContext, Consumer<String> sink) {
        context = testContext.isEmpty() ? "" : "[" + testContext + "] ";
        reportSink = sink;
    }

    public static void endTest() {
        reportSink = null;
        context = "";
    }
}
