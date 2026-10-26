package factory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;
import utils.FrameworkConfig;
import utils.StepLog;

/**
 * Serves the app's unchanging files (Flutter engine, app code, fonts, images) from
 * memory after the first download, for the whole test run.
 *
 * Every test starts a fresh browser, so without this each test downloads ~60 files
 * (~10 MB) again. On 2026-10-23 that volume got this machine's IP blocked (HTTP 403)
 * by the Bima Sugam AWS firewall, production included. Downloads are also capped and
 * retried, because UAT sometimes never answers a request and Flutter then hangs on
 * its splash screen; each retry is logged so the server problem stays visible.
 */
final class StaticFileCache {

    private static final Logger LOG = LoggerFactory.getLogger(StaticFileCache.class);
    private static final int ATTEMPTS = 3;
    /** Per try; the Flutter engine and app code are 6-7 MB each and take well over 10 s on a slow line. */
    private static final double TIMEOUT_MS = 60_000;
    /** The body is stored decoded, so these headers must not be replayed. */
    private static final List<String> DROPPED_HEADERS = List.of("content-encoding", "content-length", "transfer-encoding");

    private record CachedFile(int status, Map<String, String> headers, byte[] body) {
    }

    private static final Map<String, CachedFile> FILES = new ConcurrentHashMap<>();
    private static final Set<String> WARMED = new HashSet<>();
    private static final double WARM_UP_TIMEOUT_MS = 120_000;

    private StaticFileCache() {
    }

    /** Routes every URL pattern listed in {@code static.file.patterns} through the cache. */
    static void install(BrowserContext context) {
        for (String pattern : FrameworkConfig.get("static.file.patterns").split(",")) {
            context.route(pattern.trim(), StaticFileCache::serve);
        }
    }

    /**
     * Loads the app once in a throwaway page so every large file is downloaded and
     * stored before the first test. Downloads run inside Playwright's route handler,
     * which holds up everything else on that page until they finish, so doing the
     * slow first download during a test can make the test's own app load time out.
     */
    static synchronized void warmUp(Browser browser, Browser.NewContextOptions options, String engineKey) {
        if (!WARMED.add(engineKey)) {
            return;
        }
        StepLog.step("First app load in this run for " + engineKey + ": downloading app files once (up to 2 min)");
        long start = System.currentTimeMillis();
        BrowserContext context = browser.newContext(options);
        try {
            install(context);
            Page page = context.newPage();
            page.navigate(FrameworkConfig.baseUrl(), new Page.NavigateOptions().setWaitUntil(WaitUntilState.LOAD).setTimeout(WARM_UP_TIMEOUT_MS));
            page.locator("flt-semantics-placeholder").waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.ATTACHED).setTimeout(WARM_UP_TIMEOUT_MS));
            StepLog.step("App files ready after " + (System.currentTimeMillis() - start) + " ms (" + FILES.size() + " files stored)");
        } catch (PlaywrightException e) {
            StepLog.step("App warm-up did not finish (" + e.getMessage().lines().findFirst().orElse("") + "); tests will download what is missing");
        } finally {
            context.close();
        }
    }

    private static void serve(Route route) {
        String url = route.request().url();
        CachedFile cached = FILES.get(url);
        if (cached != null) {
            route.fulfill(new Route.FulfillOptions().setStatus(cached.status()).setHeaders(cached.headers()).setBodyBytes(cached.body()));
            return;
        }
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                APIResponse response = route.fetch(new Route.FetchOptions().setTimeout(TIMEOUT_MS));
                if (response.status() == 200) {
                    Map<String, String> headers = new HashMap<>(response.headers());
                    DROPPED_HEADERS.forEach(headers::remove);
                    FILES.put(url, new CachedFile(200, headers, response.body()));
                }
                route.fulfill(new Route.FulfillOptions().setResponse(response));
                return;
            } catch (PlaywrightException e) {
                LOG.warn("Asset request failed (attempt {}/{}): {}", attempt, ATTEMPTS, url);
            }
        }
        route.abort();
    }
}
