package managers;

import java.nio.file.Path;
import java.time.Duration;

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
                newDriver = new SafariDriver();
                break;
            case CHROME:
            default:
                newDriver = new ChromeDriver(chromeOptions());
                break;
        }
        if (config.isWindowMaximize() && !config.isHeadless()) {
            newDriver.manage().window().maximize();
        }
        // Page objects use explicit waits; mixing them with implicit waits causes unpredictable timeouts.
        newDriver.manage().timeouts().implicitlyWait(Duration.ZERO);
        return newDriver;
    }

    private ChromeOptions chromeOptions() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--disable-notifications", "--lang=en-US", "--window-size=1400,1000");
        Path profile = config.getChromeProfileDir();
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
