package utils;

import java.util.LinkedHashMap;
import java.util.Map;

import com.microsoft.playwright.Page;

/**
 * App performance seen by the test's browser, for the report: how long the
 * Flutter app took to become usable, plus the browser's own page-load timings.
 */
public final class PerformanceMetrics {

    /** Browser Navigation/Paint/Resource Timing for the current page; null values when the browser doesn't report one. */
    private static final String BROWSER_TIMINGS = """
            () => {
              const nav = performance.getEntriesByType('navigation')[0];
              const paint = Object.fromEntries(performance.getEntriesByType('paint').map(e => [e.name, Math.round(e.startTime)]));
              const res = performance.getEntriesByType('resource');
              const ms = v => (v === undefined || v === null || v <= 0) ? null : Math.round(v);
              return {
                'Server response (TTFB) ms': nav ? ms(nav.responseStart - nav.requestStart) : null,
                'DOM content loaded ms': nav ? ms(nav.domContentLoadedEventEnd) : null,
                'Page load event ms': nav ? ms(nav.loadEventEnd) : null,
                'First paint ms': paint['first-paint'] ?? null,
                'First contentful paint ms': paint['first-contentful-paint'] ?? null,
                'Requests': res.length + 1,
                'Downloaded KB (not from cache)': Math.round(res.reduce((s, e) => s + (e.transferSize || 0), 0) / 1024),
                'JS memory used MB': performance.memory ? Math.round(performance.memory.usedJSHeapSize / 1048576) : null
              };
            }
            """;

    /** How long a page may take to answer before its browser timings are reported as unavailable. */
    private static final double PAGE_CHECK_MS = 5_000;

    private static volatile Long appStartMs;

    private PerformanceMetrics() {
    }

    /** Called when the Flutter app becomes usable after opening it. */
    static void recordAppStart(long millis) {
        if (appStartMs == null) {
            appStartMs = millis;
        }
    }

    public static void reset() {
        appStartMs = null;
    }

    /** Collects the metrics for the current page; never throws (an empty map when the page is gone). */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> collect(Page page) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("App start until usable ms", appStartMs);
        try {
            // evaluate() has no timeout and waits forever on a page whose app never started (seen on UAT),
            // which froze the whole run; check with a time-limited call that the page can run scripts first
            page.waitForFunction("() => true", null, new Page.WaitForFunctionOptions().setTimeout(PAGE_CHECK_MS).setPollingInterval(100));
            metrics.putAll((Map<String, Object>) page.evaluate(BROWSER_TIMINGS));
        } catch (RuntimeException e) {
            metrics.put("Browser timings", "unavailable: " + e.getMessage().lines().findFirst().orElse(""));
        }
        return metrics;
    }
}
