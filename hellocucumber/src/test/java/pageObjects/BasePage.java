package pageObjects;

import java.util.Locale;
import java.util.function.Function;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
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

    protected final WebDriver driver;
    protected final ConfigFileReader config;
    protected final WebDriverWait wait;

    protected BasePage(WebDriver driver) {
        this.driver = driver;
        this.config = FileReaderManager.getInstance().getConfigReader();
        this.wait = new WebDriverWait(driver, config.getExplicitWait());
    }

    /** Path of this page relative to the site URL, e.g. {@code /chart/top/}. */
    protected abstract String path();

    /** Navigates to this page and waits until it is ready. */
    public void load() {
        driver.get(config.getApplicationUrl() + path());
        afterNavigation();
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
        waitForHumanVerificationIfShown();
        driver.findElements(CONSENT_ACCEPT).stream().filter(WebElement::isDisplayed).findFirst().ifPresent(WebElement::click);
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
        if (config.isHeadless()) {
            throw new IllegalStateException("IMDB is asking for human verification (CAPTCHA), which can't be solved "
                    + "in headless mode. Run without -Dheadless=true and solve it in the browser window.");
        }
        long seconds = config.getCaptchaWait().getSeconds();
        System.out.println("\n>>> IMDB is asking you to confirm you are human. Solve the CAPTCHA in the Chrome window. "
                + "Waiting up to " + seconds + " seconds...\n");
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

    /** Quote a value for use inside an XPath string literal. */
    protected static String xpathLiteral(String value) {
        if (!value.contains("'")) return "'" + value + "'";
        return "concat('" + value.replace("'", "', \"'\", '") + "')";
    }
}
