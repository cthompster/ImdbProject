package imdbTests;

import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriverException;

import io.cucumber.java.After;
import io.cucumber.java.Scenario;
import managers.WebDriverManager;

public class Hooks {
    private final TestContext context;

    public Hooks(TestContext context) {
        this.context = context;
    }

    /** Attaches a screenshot to the report when a scenario fails, then always closes the browser. */
    @After
    public void tearDown(Scenario scenario) {
        WebDriverManager driverManager = context.getWebDriverManager();
        try {
            if (scenario.isFailed() && driverManager.hasDriver()) {
                byte[] png = ((TakesScreenshot) driverManager.getDriver()).getScreenshotAs(OutputType.BYTES);
                scenario.attach(png, "image/png", "Screenshot at failure");
                scenario.log("URL at failure: " + driverManager.getDriver().getCurrentUrl());
            }
        } catch (WebDriverException e) {
            scenario.log("Could not capture a screenshot: " + e.getMessage());
        } finally {
            driverManager.quitDriver();
        }
    }
}
