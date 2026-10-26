package utils;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Route;

/**
 * Controls the app's data-changing requests (POST/PUT/PATCH/DELETE, e.g. "send
 * OTP") inside the test browser, so loading and error states can be tested
 * without the request reaching UAT (no OTP SMS is sent). GET requests (pages,
 * images, app files) are never touched.
 *
 * Route handlers run on Playwright's own thread, so a held request is only kept
 * pending there; the test answers it later with {@link #answerHeld(int, String)}.
 */
public final class ApiInterceptor {

    private enum Mode { RECORD, HOLD, ANSWER }

    private final Mode mode;
    private final int status;
    private final String body;
    private final List<String> seen = new CopyOnWriteArrayList<>();
    private final List<Route> held = new CopyOnWriteArrayList<>();

    private ApiInterceptor(Mode mode, int status, String body) {
        this.mode = mode;
        this.status = status;
        this.body = body;
    }

    /** Lets every request through to UAT but records the data-changing ones (e.g. to count OTP sends). */
    public static ApiInterceptor record(Page page) {
        return install(page, new ApiInterceptor(Mode.RECORD, 0, null));
    }

    /** Keeps data-changing requests pending (the app shows its loading state) until {@link #answerHeld}. */
    public static ApiInterceptor hold(Page page) {
        return install(page, new ApiInterceptor(Mode.HOLD, 0, null));
    }

    /** Answers data-changing requests straight away with {@code status} and a JSON body, without reaching UAT. */
    public static ApiInterceptor answer(Page page, int status, String jsonBody) {
        return install(page, new ApiInterceptor(Mode.ANSWER, status, jsonBody));
    }

    /** Answers every held request with {@code status} (never sent to UAT). */
    public void answerHeld(int answerStatus, String jsonBody) {
        for (Route route : held) {
            route.fulfill(new Route.FulfillOptions().setStatus(answerStatus).setContentType("application/json").setBody(jsonBody));
        }
        held.clear();
    }

    /** Data-changing requests the app made since this interceptor was installed ("POST https://..."). */
    public List<String> requests() {
        return List.copyOf(seen);
    }

    private static ApiInterceptor install(Page page, ApiInterceptor interceptor) {
        page.route("**/*", route -> {
            String method = route.request().method();
            if (method.equals("GET") || method.equals("HEAD") || method.equals("OPTIONS")) {
                route.fallback();
                return;
            }
            interceptor.seen.add(method + " " + route.request().url());
            switch (interceptor.mode) {
                case RECORD -> route.fallback();
                case HOLD -> {
                    StepLog.step("Holding " + method + " " + route.request().url() + " (not sent to UAT)");
                    interceptor.held.add(route);
                }
                case ANSWER -> {
                    StepLog.step("Answering " + method + " " + route.request().url() + " with HTTP " + interceptor.status + " (not sent to UAT)");
                    route.fulfill(new Route.FulfillOptions().setStatus(interceptor.status)
                            .setContentType("application/json").setBody(interceptor.body));
                }
            }
        });
        return interceptor;
    }
}
