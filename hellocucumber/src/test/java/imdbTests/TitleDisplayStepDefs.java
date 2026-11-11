package imdbTests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Year;
import java.util.List;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import pageObjects.TitlePage;

/** Steps for title_display_page.feature. */
public class TitleDisplayStepDefs {
    /** Year of the oldest surviving film; nothing on IMDB can be released earlier. */
    private static final int FIRST_FILM_YEAR = 1874;
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
        List<String> options = titlePage.getShareOptions();
        assertTrue(options.stream().anyMatch(option::equalsIgnoreCase), () -> "Share menu options were " + options);
    }

    @Then("the {string} option should share this movie via {string}")
    public void theOptionShouldShareThisMovieVia(String option, String linkStart) {
        String href = titlePage.getShareLink(option);
        String titleId = titlePage.getTitleId();
        assertNotNull(href, "\"" + option + "\" has no link");
        assertTrue(href.startsWith(linkStart), "\"" + option + "\" links to " + href + ", expected it to start with " + linkStart);
        assertTrue(href.contains(titleId), "\"" + option + "\" link does not mention " + titleId + ": " + href);
    }

    @When("I choose {string} from the share menu")
    public void iChooseFromTheShareMenu(String option) {
        titlePage.chooseShareOption(option);
    }

    @Then("the page should show the movie's name, release year and IMDb rating")
    public void thePageShouldShowNameYearAndRating() {
        String heading = titlePage.getHeading();
        String tabTitle = titlePage.getTabTitle();
        assertFalse(heading.isEmpty(), "The page heading is empty");
        assertTrue(tabTitle.contains(heading), () -> "The browser tab title \"" + tabTitle
                + "\" does not contain the heading \"" + heading + "\"");

        int year = titlePage.getReleaseYear();
        int latestYear = Year.now().getValue() + 10;
        assertTrue(year >= FIRST_FILM_YEAR && year <= latestYear, () -> "Release year " + year + " in \"" + tabTitle
                + "\" is outside " + FIRST_FILM_YEAR + "-" + latestYear);

        double rating = titlePage.getImdbRating();
        assertTrue(rating >= 1.0 && rating <= 10.0, () -> "IMDb rating " + rating + " is outside 1-10");
    }

    @Then("the movie's name should be the only main heading")
    public void theMovieNameShouldBeTheOnlyMainHeading() {
        assertEquals("h1", titlePage.getHeadingTagName(), "Element type of the movie's name heading");
        List<String> headings = titlePage.getVisibleLevel1Headings();
        assertEquals(List.of(titlePage.getHeading()), headings, "Visible level-1 headings on the page");
    }

    @Then("the page should declare its language")
    public void thePageShouldDeclareItsLanguage() {
        String lang = titlePage.getPageLanguage();
        assertTrue(lang.matches("[A-Za-z]{2,3}(-[A-Za-z0-9]+)*"), () -> "The page's lang attribute is \"" + lang
                + "\", expected a language tag such as en-US");
    }

    @Then("these controls should have an accessible name:")
    public void theseControlsShouldHaveAnAccessibleName(List<String> controls) {
        for (String control : controls) {
            String name = titlePage.getAccessibleName(control);
            assertFalse(name.isEmpty(), () -> "The \"" + control + "\" control has no accessible name for screen readers");
        }
    }

    @Then("the poster image should have alternative text")
    public void thePosterImageShouldHaveAlternativeText() {
        String alt = titlePage.getPosterAltText();
        assertNotNull(alt, "The poster image has no alt attribute");
        assertFalse(alt.isBlank(), "The poster image's alt text is empty");
    }

    @Then("I should see the message {string}")
    public void iShouldSeeTheMessage(String message) {
        assertEquals(message, titlePage.getNotificationText());
    }
}
