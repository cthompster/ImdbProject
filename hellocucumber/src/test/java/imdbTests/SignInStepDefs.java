package imdbTests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import pageObjects.SignInPage;

/**
 * Steps for the signed-out part of the sign-in and create-account journeys. They follow the links on the
 * sign-in page and check where they lead; they never enter credentials or submit a form.
 */
public class SignInStepDefs {
    private final SignInPage signInPage;

    public SignInStepDefs(TestContext context) {
        this.signInPage = context.getPageObjectManager().getSignInPage();
    }

    @Then("the Sign In page should offer {string}")
    public void theSignInPageShouldOffer(String option) {
        assertTrue(signInPage.offers(option), () -> "The sign-in page does not offer \"" + option
                + "\". Options shown: " + signInPage.getOptions());
    }

    @When("I choose {string} on the Sign In page")
    public void iChooseOnTheSignInPage(String option) {
        signInPage.choose(option);
    }

    /**
     * {@code destination} is a host, optionally followed by a path prefix: "amazon.com/ap/signin" accepts
     * https://www.amazon.com/ap/signin?..., and "amazon.com" accepts any https page on amazon.com or a subdomain.
     */
    @Then("I should be taken to an HTTPS page on {string}")
    public void iShouldBeTakenToAnHttpsPageOn(String destination) {
        if (destination.contains("://")) {
            throw new IllegalArgumentException("Give a host and optional path, without the scheme (e.g. \"amazon.com/ap/signin\"), not \""
                    + destination + "\"");
        }
        int slash = destination.indexOf('/');
        String host = slash < 0 ? destination : destination.substring(0, slash);
        String pathPrefix = slash < 0 ? "" : destination.substring(slash);
        assertTrue(signInPage.waitForSecureDestination(host, pathPrefix), () -> "Expected an https:// page on " + host
                + (pathPrefix.isEmpty() ? "" : " with a path starting " + pathPrefix)
                + ", but the browser is on " + signInPage.getCurrentUrl());
    }
}
