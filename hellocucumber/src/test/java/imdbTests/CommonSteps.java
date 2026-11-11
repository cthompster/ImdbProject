package imdbTests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import enums.PageName;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import managers.PageObjectManager;
import pageObjects.BasePage;

/** Steps used by more than one feature. */
public class CommonSteps {
    private final PageObjectManager pages;
    private BasePage currentPage;

    public CommonSteps(TestContext context) {
        this.pages = context.getPageObjectManager();
    }

    @Given("I load the {string}")
    public void iLoadThe(String pageName) {
        currentPage = pages.getPage(PageName.fromLabel(pageName));
        currentPage.load();
    }

    @Given("I am not logged in")
    public void iAmNotLoggedIn() {
        if (currentPage == null) {
            throw new IllegalStateException("\"I am not logged in\" needs a page to check. "
                    + "Put a \"Given I load the ...\" step before it.");
        }
        assertTrue(currentPage.isSignedOut(), "Expected to be signed out, but the header has no \"Sign in\" link");
    }

    @Given("I open the {string} through its plain HTTP address")
    public void iOpenThroughPlainHttp(String pageName) {
        currentPage = pages.getPage(PageName.fromLabel(pageName));
        currentPage.loadOverPlainHttp();
    }

    @Then("the browser should be redirected to the page's HTTPS address")
    public void theBrowserShouldBeRedirectedToHttps() {
        BasePage page = currentPage();
        assertTrue(page.isCurrent(), () -> "Expected to stay on the requested page, but the browser is on " + page.getCurrentUrl());
        String url = page.getCurrentUrl();
        assertTrue(url.startsWith("https://"), () -> "Expected an https:// address after the redirect, but the browser is on " + url);
    }

    @Then("the page should not load any content over plain HTTP")
    public void thePageShouldNotLoadInsecureContent() {
        List<String> insecure = currentPage().getInsecureResources();
        assertTrue(insecure.isEmpty(), () -> insecure.size() + " resource(s) loaded over plain HTTP (mixed content): " + insecure);
    }

    @Then("the page should be ready within {int} seconds")
    public void thePageShouldBeReadyWithin(int seconds) {
        BasePage page = currentPage();
        Map<String, Long> timings = page.getNavigationTimings();
        long domContentLoaded = timings.get("domContentLoaded");
        // Printed on every run so timings can be compared between runs; only a large slowdown fails.
        System.out.println("Page timings for " + page.getCurrentUrl() + " (ms since navigation start): " + timings);
        assertTrue(domContentLoaded <= seconds * 1000L, () -> "The page took " + domContentLoaded
                + " ms to reach DOMContentLoaded, more than " + seconds + " s. All timings (ms): " + timings);
    }

    private BasePage currentPage() {
        if (currentPage == null) {
            throw new IllegalStateException("This step needs a page to check. Put a \"Given I load the ...\" step before it.");
        }
        return currentPage;
    }

    @Then("I should be taken to the {string}")
    public void iShouldBeTakenToThe(String pageName) {
        BasePage expected = pages.getPage(PageName.fromLabel(pageName));
        assertTrue(expected.isCurrent(), () -> "Expected the " + pageName + " but the browser is on " + expected.getCurrentUrl());
    }
}
