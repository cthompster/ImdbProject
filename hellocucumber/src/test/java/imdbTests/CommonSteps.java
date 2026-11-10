package imdbTests;

import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Then("I should be taken to the {string}")
    public void iShouldBeTakenToThe(String pageName) {
        BasePage expected = pages.getPage(PageName.fromLabel(pageName));
        assertTrue(expected.isCurrent(), () -> "Expected the " + pageName + " but the browser is on " + expected.getCurrentUrl());
    }
}
