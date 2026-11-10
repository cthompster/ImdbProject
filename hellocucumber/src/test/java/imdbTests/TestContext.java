package imdbTests;

import managers.PageObjectManager;
import managers.WebDriverManager;

/**
 * State shared by the step classes of one scenario. PicoContainer creates a single instance per
 * scenario and passes it to every step class's constructor, so all steps drive the same browser.
 * Never create one with {@code new} in step code.
 */
public class TestContext {
    private final WebDriverManager webDriverManager = new WebDriverManager();
    private final PageObjectManager pageObjectManager = new PageObjectManager(webDriverManager);

    public WebDriverManager getWebDriverManager() {
        return webDriverManager;
    }

    public PageObjectManager getPageObjectManager() {
        return pageObjectManager;
    }
}
