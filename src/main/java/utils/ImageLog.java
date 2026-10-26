package utils;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;

/**
 * Records every image the page downloads. Flutter draws images on a canvas, so the
 * network is the reliable place to see which image files a screen really used.
 * Attach before navigating.
 */
public final class ImageLog {

    private static final Pattern IMAGE_URL = Pattern.compile(".*\\.(png|jpe?g|webp|gif|svg)(\\?.*)?$", Pattern.CASE_INSENSITIVE);
    /** "…/person_nine_0.jpeg" and "…/person_nine.jpeg" are the same image under a CMS revision suffix. */
    private static final Pattern NAME = Pattern.compile("^.*/([^/?]+?)(_\\d+)?\\.[a-z]+(\\?.*)?$", Pattern.CASE_INSENSITIVE);

    public record Load(String url, int status, String contentType) {

        public String name() {
            var m = NAME.matcher(url);
            return m.matches() ? m.group(1) : url;
        }

        public boolean ok() {
            return status == 200 && (contentType.startsWith("image/") || url.toLowerCase().endsWith(".svg"));
        }
    }

    private final Page page;
    private final List<Load> loads = new CopyOnWriteArrayList<>();

    private ImageLog(Page page) {
        this.page = page;
        page.onResponse(this::record);
    }

    public static ImageLog attach(Page page) {
        return new ImageLog(page);
    }

    private void record(Response response) {
        if (IMAGE_URL.matcher(response.url()).matches()) {
            loads.add(new Load(response.url(), response.status(), response.headers().getOrDefault("content-type", "")));
        }
    }

    /** Names of images that loaded successfully, e.g. "person_one". */
    public Set<String> loadedNames() {
        return loads.stream().filter(Load::ok).map(Load::name).collect(Collectors.toCollection(TreeSet::new));
    }

    public List<Load> failed() {
        return loads.stream().filter(l -> !l.ok()).toList();
    }

    /** Waits until every name has loaded successfully; returns the names still missing at the deadline. */
    public Set<String> awaitLoaded(Collection<String> names, double timeoutMs) {
        long deadline = System.currentTimeMillis() + (long) timeoutMs;
        Set<String> missing = missing(names);
        while (!missing.isEmpty() && System.currentTimeMillis() < deadline) {
            page.waitForTimeout(250); // lets Playwright deliver pending response events
            missing = missing(names);
        }
        return missing;
    }

    private Set<String> missing(Collection<String> names) {
        Set<String> loaded = loadedNames();
        return names.stream().filter(n -> !loaded.contains(n)).collect(Collectors.toCollection(TreeSet::new));
    }
}
