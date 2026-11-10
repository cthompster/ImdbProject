package managers;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.SessionNotCreatedException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.safari.SafariDriver;

import dataProviders.ConfigFileReader;

/**
 * Creates and disposes of the browser for one scenario. Selenium Manager (built into Selenium 4)
 * downloads a driver that matches the installed browser, so no driver path is configured.
 */
public class WebDriverManager {
    /** Cookie in which IMDB's bot protection (AWS WAF) records a solved CAPTCHA. */
    private static final String CAPTCHA_PASS_COOKIE = "aws-waf-token";

    private final ConfigFileReader config = FileReaderManager.getInstance().getConfigReader();
    private WebDriver driver;

    public WebDriver getDriver() {
        if (driver == null) {
            driver = createDriver();
        }
        return driver;
    }

    public boolean hasDriver() {
        return driver != null;
    }

    private WebDriver createDriver() {
        switch (config.getEnvironment()) {
            case LOCAL:
                return createLocalDriver();
            case REMOTE:
            default:
                throw new UnsupportedOperationException("RemoteWebDriver is not yet implemented");
        }
    }

    private WebDriver createLocalDriver() {
        WebDriver newDriver;
        switch (config.getBrowser()) {
            case FIREFOX:
                newDriver = new FirefoxDriver(firefoxOptions());
                break;
            case SAFARI:
                if (config.isHeadlessRequested()) {
                    System.out.println(">>> Safari has no headless mode; it will open a normal window.");
                }
                newDriver = new SafariDriver();
                break;
            case CHROME:
            default:
                newDriver = createChromeDriver();
                break;
        }
        try {
            if (config.isWindowMaximize() && !config.isHeadless()) {
                newDriver.manage().window().maximize();
            }
            // Page objects use explicit waits; mixing them with implicit waits causes unpredictable timeouts.
            newDriver.manage().timeouts().implicitlyWait(Duration.ZERO);
        } catch (RuntimeException e) {
            quitQuietly(newDriver, e);
            throw e;
        }
        return newDriver;
    }

    private ChromeDriver createChromeDriver() {
        Path profile = config.getChromeProfileDir();
        ChromeDriver chrome;
        try {
            chrome = new ChromeDriver(chromeOptions(profile));
        } catch (SessionNotCreatedException e) {
            // Chrome leaves a SingletonLock link in a profile it is using. Newer Chrome versions only report
            // "Chrome instance exited", so check for the lock rather than the message.
            if (profile != null && (String.valueOf(e.getMessage()).contains("already in use")
                    || Files.exists(profile.resolve("SingletonLock"), LinkOption.NOFOLLOW_LINKS))) {
                throw new IllegalStateException("Chrome could not start: the profile " + profile + " is in use by another Chrome. "
                        + "Close any Chrome left open by an earlier test run (or wait for another run to finish). "
                        + "If no Chrome is running, delete " + profile.resolve("SingletonLock") + ".", e);
            }
            throw e;
        }
        if (profile != null) {
            try {
                startSignedOut(chrome);
            } catch (RuntimeException e) {
                quitQuietly(chrome, e);
                throw e;
            }
        }
        return chrome;
    }

    /**
     * Closes a browser whose setup failed. It isn't stored in {@code driver} yet, so the After hook can't
     * close it, and a Chrome left open keeps the shared profile locked for every later scenario.
     */
    private static void quitQuietly(WebDriver browser, RuntimeException cause) {
        try {
            browser.quit();
        } catch (RuntimeException quitFailure) {
            cause.addSuppressed(quitFailure);
        }
    }

    /**
     * The shared profile keeps cookies between runs. So that every scenario starts signed out and
     * independent of earlier ones, clear all cookies except the CAPTCHA pass.
     */
    @SuppressWarnings("unchecked")
    private static void startSignedOut(ChromeDriver chrome) {
        List<Map<String, Object>> cookies =
                (List<Map<String, Object>>) chrome.executeCdpCommand("Storage.getCookies", Map.of()).get("cookies");
        List<Map<String, Object>> keep = cookies.stream()
                .filter(c -> CAPTCHA_PASS_COOKIE.equals(c.get("name")))
                .map(WebDriverManager::toCookieParam)
                .collect(Collectors.toList());
        chrome.executeCdpCommand("Storage.clearCookies", Map.of());
        if (!keep.isEmpty()) {
            chrome.executeCdpCommand("Storage.setCookies", Map.of("cookies", keep));
        }
    }

    /** Converts a cookie returned by Storage.getCookies into the form Storage.setCookies accepts. */
    private static Map<String, Object> toCookieParam(Map<String, Object> cookie) {
        Map<String, Object> param = new HashMap<>();
        for (String key : List.of("name", "value", "domain", "path", "secure", "httpOnly", "sameSite", "priority")) {
            if (cookie.get(key) != null) param.put(key, cookie.get(key));
        }
        if (!Boolean.TRUE.equals(cookie.get("session")) && cookie.get("expires") != null) {
            param.put("expires", cookie.get("expires"));
        }
        return param;
    }

    private ChromeOptions chromeOptions(Path profile) {
        ChromeOptions options = new ChromeOptions();
        // Return from driver.get() once the HTML is ready instead of waiting for every ad and tracker
        // to finish; page objects wait for the exact elements they need.
        options.setPageLoadStrategy(PageLoadStrategy.EAGER);
        options.addArguments("--disable-notifications", "--lang=en-US", "--window-size=1400,1000");
        if (profile != null) {
            // Reusing one profile keeps IMDB's human-verification cookie between scenarios.
            options.addArguments("--user-data-dir=" + profile);
        }
        if (config.isHeadless()) {
            options.addArguments("--headless=new");
        }
        return options;
    }

    private FirefoxOptions firefoxOptions() {
        FirefoxOptions options = new FirefoxOptions();
        options.setPageLoadStrategy(PageLoadStrategy.EAGER);
        options.addPreference("intl.accept_languages", "en-US");
        if (config.isHeadless()) {
            options.addArguments("-headless");
        }
        return options;
    }

    /** Quits the browser if one was started. Safe to call more than once. */
    public void quitDriver() {
        if (driver != null) {
            try {
                driver.quit();
            } finally {
                driver = null;
            }
        }
    }
}
