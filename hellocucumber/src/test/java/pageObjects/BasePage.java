package pageObjects;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import dataProviders.ConfigFileReader;
import managers.FileReaderManager;

/** Shared driver, waits and navigation for all IMDB page objects. */
public abstract class BasePage {
    private static final String CAPTCHA_TITLE = "confirm you are human";
    /** The header "Sign in" link; its ref_ ends in _nv_generic_lgin on every page. */
    private static final By HEADER_SIGN_IN_LINK = By.cssSelector("a[href*='/registration/signin'][href*='_nv_generic_lgin']");
    /** Cookie-consent banner shown in some regions (none appears in the US). Best effort. */
    private static final By CONSENT_ACCEPT = By.xpath(
            "//*[contains(@data-testid,'consent') or contains(@data-testid,'cookie')]//button[normalize-space()='Accept']");

    /** A rating such as "9.3" at the start of a rating text ("9.3/10", "9.3 (3.3M)"). */
    protected static final Pattern LEADING_RATING = Pattern.compile("^(\\d{1,2}(?:\\.\\d)?)");
    /** First number in the text, allowing thousands separators: "250 Titles" gives 250. */
    private static final Pattern FIRST_NUMBER = Pattern.compile("(\\d[\\d,]*)");

    protected final WebDriver driver;
    protected final ConfigFileReader config;
    protected final WebDriverWait wait;
    private boolean captchaShownDuringLoad;

    protected BasePage(WebDriver driver) {
        this.driver = driver;
        this.config = FileReaderManager.getInstance().getConfigReader();
        this.wait = new WebDriverWait(driver, config.getExplicitWait());
        // React redraws lists (e.g. after a re-sort); an element read mid-redraw goes stale. Retry instead of failing.
        this.wait.ignoring(StaleElementReferenceException.class);
    }

    /** Path of this page relative to the site URL, e.g. {@code /chart/top/}. */
    protected abstract String path();

    /** Navigates to this page and waits until it is ready. */
    public void load() {
        open(config.getApplicationUrl());
    }

    /**
     * Navigates to this page through its plain {@code http://} address, as a user typing the address
     * without "https" would, and waits until it is ready. The browser should end up on HTTPS. Note that
     * Chrome may upgrade the address to HTTPS itself (HTTPS-Upgrades, or a remembered HSTS entry) without
     * asking the server over plain HTTP, so this checks the visitor's result, not the server's redirect.
     */
    public void loadOverPlainHttp() {
        String url = config.getApplicationUrl();
        if (!url.startsWith("https://")) {
            throw new IllegalStateException("The plain-HTTP check needs an https:// site URL, but url is " + url);
        }
        open("http://" + url.substring("https://".length()));
    }

    private void open(String siteUrl) {
        captchaShownDuringLoad = false;
        driver.get(siteUrl + path());
        afterNavigation();
        waitUntilLoaded();
    }

    /**
     * True if IMDB showed its CAPTCHA during the last {@link #load()}. The browser's timing data may
     * then describe the challenge page rather than this page.
     */
    public boolean wasCaptchaShownDuringLoad() {
        return captchaShownDuringLoad;
    }

    /** Waits for the element that shows this page has rendered. Override in pages that have one. */
    protected void waitUntilLoaded() {
    }

    /**
     * Sub-resources (scripts, styles, images, frames, media) that this page loaded or references over plain
     * {@code http:}, i.e. mixed content. Ordinary links ({@code <a href>}) don't count. Uses the browser's
     * Resource Timing list, whose default buffer keeps the first 250 entries, plus the elements in the DOM
     * (including every candidate in {@code srcset}). Images that load lazily after the check aren't included.
     */
    public List<String> getInsecureResources() {
        Object result = js(
                "const urls = new Set();"
              + "performance.getEntriesByType('resource').forEach(e => { if (e.name.startsWith('http:')) urls.add(e.name); });"
              + "document.querySelectorAll('img[src], script[src], iframe[src], source[src], video[src], audio[src], "
              + "embed[src], link[rel~=\"stylesheet\"][href], link[rel~=\"preload\"][href]').forEach(el => {"
              + "  const url = el.src || el.href;"
              + "  if (url && url.startsWith('http:')) urls.add(url);"
              + "});"
              + "document.querySelectorAll('img[srcset], source[srcset]').forEach(el => {"
              + "  el.getAttribute('srcset').split(',').map(c => c.trim().split(/\\s+/)[0]).forEach(c => {"
              + "    if (!c) return;"
              + "    const url = new URL(c, document.baseURI).href;"
              + "    if (url.startsWith('http:')) urls.add(url);"
              + "  });"
              + "});"
              + "return Array.from(urls);");
        List<String> urls = new ArrayList<>();
        if (result instanceof List) {
            ((List<?>) result).forEach(u -> urls.add(String.valueOf(u)));
        }
        return urls;
    }

    /**
     * Timings of the current page load from the browser's Navigation Timing API, in milliseconds since
     * navigation start: {@code responseStart} (time to first byte) and {@code domContentLoaded}. Waits until
     * the DOMContentLoaded event has finished.
     */
    public Map<String, Long> getNavigationTimings() {
        String script = "const n = performance.getEntriesByType('navigation')[0];"
              + "if (!n || n.domContentLoadedEventEnd <= 0) return null;"
              + "return {responseStart: Math.round(n.responseStart), domContentLoaded: Math.round(n.domContentLoadedEventEnd)};";
        Object result = waitFor(d -> js(script));
        Map<String, Long> timings = new LinkedHashMap<>();
        ((Map<?, ?>) result).forEach((k, v) -> timings.put(String.valueOf(k), ((Number) v).longValue()));
        return timings;
    }

    /** True once the browser is on this page (waits up to the explicit-wait timeout). */
    public boolean isCurrent() {
        try {
            wait.until(ExpectedConditions.urlContains(path()));
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    public boolean isSignedOut() {
        return isPresent(HEADER_SIGN_IN_LINK);
    }

    public String getCurrentUrl() {
        return driver.getCurrentUrl();
    }

    /** Call after anything that loads a new page: handles the CAPTCHA and the consent banner. */
    protected void afterNavigation() {
        failIfAccessDenied();
        waitForHumanVerificationIfShown();
        driver.findElements(CONSENT_ACCEPT).stream().filter(WebElement::isDisplayed).findFirst().ifPresent(WebElement::click);
    }

    /**
     * IMDB's bot protection sometimes refuses a request outright with "403 Forbidden", which happens
     * mostly to headless browsers. Stop with an explanation instead of timing out waiting for elements.
     */
    private void failIfAccessDenied() {
        String title = String.valueOf(driver.getTitle());
        if (title.contains("403 Forbidden")) {
            throw new IllegalStateException("IMDB refused the request (403 Forbidden) for " + driver.getCurrentUrl() + ". "
                    + (config.isHeadless()
                        ? "IMDB blocks headless browsers on some pages; run without -Dheadless=true."
                        : "IMDB's bot protection is blocking this browser; try again later."));
        }
    }

    private boolean isCaptchaShown() {
        String title = driver.getTitle();
        return title != null && title.toLowerCase(Locale.ROOT).contains(CAPTCHA_TITLE);
    }

    /**
     * IMDB's bot protection can show a CAPTCHA to automated browsers. A person must solve it;
     * this waits for that instead of trying to get around it.
     */
    private void waitForHumanVerificationIfShown() {
        if (!isCaptchaShown()) return;
        captchaShownDuringLoad = true;
        if (config.isHeadless()) {
            throw new IllegalStateException("IMDB is asking for human verification (CAPTCHA), which can't be solved "
                    + "in headless mode. Run without -Dheadless=true and solve it in the browser window.");
        }
        long seconds = config.getCaptchaWait().getSeconds();
        System.out.println("\n>>> IMDB is asking you to confirm you are human. Solve the CAPTCHA in the "
                + config.getBrowserDisplayName() + " window. Waiting up to " + seconds + " seconds...\n");
        new WebDriverWait(driver, config.getCaptchaWait())
                .withMessage("the CAPTCHA was not solved within " + seconds + " seconds")
                .until(d -> !isCaptchaShown());
    }

    /**
     * Waits for a condition. If it times out because a CAPTCHA appeared late, waits for a person
     * to solve it and tries once more.
     */
    protected <T> T waitFor(Function<WebDriver, T> condition) {
        try {
            return wait.until(condition);
        } catch (TimeoutException e) {
            if (!isCaptchaShown()) throw e;
            waitForHumanVerificationIfShown();
            return wait.until(condition);
        }
    }

    protected WebElement visible(By locator) {
        return waitFor(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    protected boolean isPresent(By locator) {
        try {
            visible(locator);
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    /** Scrolls the element to the middle of the screen (away from sticky headers and ads), then clicks it. */
    protected void click(By locator) {
        WebElement element = visible(locator);
        scrollToCenter(element);
        waitFor(ExpectedConditions.elementToBeClickable(element)).click();
    }

    /**
     * IMDB turns on smooth scrolling, so a plain scrollIntoView returns while the page is still moving
     * and the click lands in the wrong place. Scroll instantly, then wait until the element's centre
     * is inside the window.
     */
    protected void scrollToCenter(WebElement element) {
        js("arguments[0].scrollIntoView({block: 'center', inline: 'nearest', behavior: 'instant'});", element);
        waitFor(d -> Boolean.TRUE.equals(js(
                "const r = arguments[0].getBoundingClientRect();"
              + "const x = r.left + r.width / 2, y = r.top + r.height / 2;"
              + "return x >= 0 && y >= 0 && x <= window.innerWidth && y <= window.innerHeight;", element)));
    }

    protected Object js(String script, Object... args) {
        return ((JavascriptExecutor) driver).executeScript(script, args);
    }

    protected static int parseCount(String text) {
        return parseCount(text, FIRST_NUMBER);
    }

    /** The number captured by group 1 of the pattern, e.g. "of (\\d[\\d,]*)" on "1-50 of 2,640" gives 2640. */
    protected static int parseCount(String text, Pattern pattern) {
        Matcher m = pattern.matcher(text);
        if (!m.find()) throw new IllegalStateException("Could not read a count from \"" + text + "\"");
        return Integer.parseInt(m.group(1).replace(",", ""));
    }

    /** Quote a value for use inside an XPath string literal. */
    protected static String xpathLiteral(String value) {
        if (!value.contains("'")) return "'" + value + "'";
        return "concat('" + value.replace("'", "', \"'\", '") + "')";
    }
}
