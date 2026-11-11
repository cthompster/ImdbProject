package pageObjects;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.openqa.selenium.By;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;

/**
 * IMDB's sign-in page, where signed-out users are sent when they try to rate or use the watchlist.
 * It first offers "Create an account" and "Sign in to an existing account". The latter reveals, on the same page,
 * "Sign in with" Amazon, IMDb, Google and Apple. The tests only follow these options; they never fill in or
 * submit a sign-in or sign-up form.
 */
public class SignInPage extends BasePage {
    private static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    /** The element's visible text, trimmed and lower-cased, for case-insensitive XPath matching. */
    private static final String TEXT = "translate(normalize-space(.), '" + UPPER + "', '" + LOWER + "')";
    /**
     * Links and buttons that sign in or create an account; used to list the options in failure messages, so it is
     * deliberately broader than the option locators.
     */
    private static final By ALL_OPTIONS = By.xpath("//*[self::a or self::button][contains(" + TEXT + ", 'sign in')"
            + " or (contains(" + TEXT + ", 'create') and contains(" + TEXT + ", 'account'))]");
    /** The sign-in options by the label used in the feature file, matched on visible text, ignoring case. */
    private static final Map<String, By> OPTIONS = Map.of(
            "create an account", option("create an account"),
            "sign in to an existing account", option("sign in to an existing account"),
            "sign in with amazon", option("sign in with amazon"),
            "sign in with imdb", option("sign in with imdb"),
            "sign in with google", option("sign in with google"),
            "sign in with apple", option("sign in with apple"));
    private static final String IMDB_HOST = "imdb.com";

    public SignInPage(WebDriver driver) {
        super(driver);
    }

    private static By option(String text) {
        return By.xpath("//*[self::a or self::button][contains(" + TEXT + ", '" + text + "')]");
    }

    @Override
    protected String path() {
        return "/registration/signin";
    }

    @Override
    public boolean isCurrent() {
        if (!super.isCurrent()) return false;
        afterNavigation();
        try {
            return waitFor(d -> String.valueOf(d.getTitle()).toLowerCase(Locale.ROOT).contains("sign in"));
        } catch (TimeoutException e) {
            return false;
        }
    }

    /** True if the page shows the option, e.g. "Create an account" or (once revealed) "Sign in with Google". */
    public boolean offers(String option) {
        return isPresent(optionLocator(option));
    }

    /**
     * Visible text of every sign-in and create-account option on the page, for failure messages. If the page changes
     * while it is being read, returns an empty list rather than hiding the real failure behind a stale element.
     */
    public List<String> getOptions() {
        try {
            return driver.findElements(ALL_OPTIONS).stream()
                    .filter(WebElement::isDisplayed)
                    .map(WebElement::getText).map(t -> t.trim().replaceAll("\\s+", " "))
                    .distinct().collect(Collectors.toList());
        } catch (WebDriverException e) {
            return List.of();
        }
    }

    /**
     * Chooses a sign-in option and waits until it has taken effect: the browser has left this page, a new window
     * has opened (and is switched to), or the option has gone from the page because it revealed more options in
     * place, as "Sign in to an existing account" does. Nothing is typed or submitted.
     */
    public void choose(String option) {
        Set<String> windowsBefore = driver.getWindowHandles();
        By locator = optionLocator(option);
        click(locator);
        // Checks the locator rather than the clicked element, so a re-render just before the click can't count as
        // the option having gone.
        waitFor(d -> d.getWindowHandles().size() > windowsBefore.size() || !d.getCurrentUrl().contains(path())
                || isGone(d, locator));
        driver.getWindowHandles().stream()
                .filter(h -> !windowsBefore.contains(h))
                .findFirst()
                .ifPresent(h -> driver.switchTo().window(h));
    }

    /**
     * Waits until the browser is on an https:// page whose host is {@code host} or one of its subdomains and whose
     * path is {@code pathPrefix} or lies under it (empty for any path). Only the address is checked: Amazon's
     * sign-in page may refuse automated browsers, so its content isn't reliable. Returns false on timeout.
     * <p>
     * IMDb's own destinations then go through the usual 403 and CAPTCHA handling, so a CAPTCHA detected there is
     * waited for (a person must solve it), and an unsolved one fails with its own message rather than as a wrong
     * address. Other sites get a plain wait, because IMDb's CAPTCHA handling doesn't apply to them.
     */
    public boolean waitForSecureDestination(String host, String pathPrefix) {
        Function<WebDriver, Boolean> reached = d -> isSecureDestination(d.getCurrentUrl(), host, pathPrefix);
        boolean onImdb = isHostOrSubdomain(host.toLowerCase(Locale.ROOT), IMDB_HOST);
        try {
            if (onImdb) {
                waitFor(reached);
            } else {
                wait.until(reached);
            }
        } catch (TimeoutException e) {
            return false;
        }
        if (onImdb) afterNavigation();
        return true;
    }

    private static boolean isSecureDestination(String url, String host, String pathPrefix) {
        try {
            // java.net.URL rather than URI: URI rejects characters Chrome leaves unencoded in some
            // third-party redirect addresses (e.g. '|' or '{' in a query).
            URL u = new URL(url);
            String path = u.getPath();
            return "https".equalsIgnoreCase(u.getProtocol())
                    && isHostOrSubdomain(String.valueOf(u.getHost()).toLowerCase(Locale.ROOT), host.toLowerCase(Locale.ROOT))
                    && isUnderPath(path, pathPrefix);
        } catch (MalformedURLException e) {
            return false;
        }
    }

    /** True when no element matching {@code locator} is displayed; retries on the next poll if the page is changing. */
    private static boolean isGone(WebDriver driver, By locator) {
        try {
            return driver.findElements(locator).stream().noneMatch(WebElement::isDisplayed);
        } catch (StaleElementReferenceException e) {
            return false;
        }
    }

    private static boolean isHostOrSubdomain(String actualHost, String expectedHost) {
        return actualHost.equals(expectedHost) || actualHost.endsWith("." + expectedHost);
    }

    /** "/ap/signin" accepts "/ap/signin" and "/ap/signin/..." but not "/ap/signinXYZ". */
    private static boolean isUnderPath(String path, String prefix) {
        if (prefix.isEmpty()) return true;
        String base = prefix.endsWith("/") ? prefix : prefix + "/";
        return path.equals(prefix) || path.startsWith(base);
    }

    private static By optionLocator(String option) {
        By locator = OPTIONS.get(option.trim().toLowerCase(Locale.ROOT));
        if (locator == null) {
            throw new IllegalArgumentException("Unknown sign-in option \"" + option + "\". Known options: " + OPTIONS.keySet());
        }
        return locator;
    }
}
