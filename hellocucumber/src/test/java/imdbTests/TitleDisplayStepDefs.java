package imdbTests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import managers.FileReaderManager;
import pageObjects.TitlePage;

/** Steps for title_display_page.feature. */
public class TitleDisplayStepDefs {
    private final TitlePage titlePage;

    public TitleDisplayStepDefs(TestContext context) {
        this.titlePage = context.getPageObjectManager().getTitlePage();
    }

    @When("I add the movie to my watchlist")
    public void iAddTheMovieToMyWatchlist() {
        titlePage.addToWatchlist();
    }

    @When("I rate the movie {int} star(s)")
    public void iRateTheMovie(int stars) {
        titlePage.rate(stars);
    }

    @When("I open the share menu")
    public void iOpenTheShareMenu() {
        titlePage.openShareMenu();
    }

    @Then("the share menu should offer {string}")
    public void theShareMenuShouldOffer(String option) {
        assertTrue(titlePage.getShareOptions().stream().anyMatch(option::equalsIgnoreCase),
                "Share menu options were " + titlePage.getShareOptions());
    }

    @Then("the {string} option should share this movie via {string}")
    public void theOptionShouldShareThisMovieVia(String option, String linkStart) {
        String href = titlePage.getShareLink(option);
        String titleId = FileReaderManager.getInstance().getConfigReader().getTitleId();
        assertNotNull(href, "\"" + option + "\" has no link");
        assertTrue(href.startsWith(linkStart), "\"" + option + "\" links to " + href + ", expected it to start with " + linkStart);
        assertTrue(href.contains(titleId), "\"" + option + "\" link does not mention " + titleId + ": " + href);
    }

    @When("I choose {string} from the share menu")
    public void iChooseFromTheShareMenu(String option) {
        titlePage.chooseShareOption(option);
    }

    @Then("I should see the message {string}")
    public void iShouldSeeTheMessage(String message) {
        assertEquals(message, titlePage.getNotificationText());
    }
}
