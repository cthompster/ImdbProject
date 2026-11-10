package imdbTests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import enums.SortOrder;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import pageObjects.GenreSearchResultsPage;
import pageObjects.TopRatedPage;

/** Steps for top_rated_movies.feature. */
public class ImdbStepdefs {
    private final TopRatedPage topRatedPage;
    private GenreSearchResultsPage genreResults;

    public ImdbStepdefs(TestContext context) {
        this.topRatedPage = context.getPageObjectManager().getTopRatedPage();
    }

    @When("I refine by Genre {string}")
    public void iRefineByGenre(String genre) {
        genreResults = topRatedPage.refineByGenre(genre);
    }

    @Then("the results should be filtered by the {string} genre")
    public void theResultsShouldBeFilteredByGenre(String genre) {
        assertTrue(genreResults.isFilteredByGenre(genre),
                () -> "Expected \"" + genre + "\" to be an applied filter on " + genreResults.getCurrentUrl());
    }

    @Then("the results should contain at least {int} title(s)")
    public void theResultsShouldContainAtLeast(int minimum) {
        int total = genreResults.getTotalResults();
        assertTrue(total >= minimum, "Expected at least " + minimum + " titles but IMDB reports " + total);
        assertTrue(genreResults.getDisplayedResultCount() > 0, "IMDB reports " + total + " titles but none are listed");
    }

    @When("I sort the list by {string} in {word} order")
    public void iSortTheListBy(String field, String order) {
        topRatedPage.sortBy(field);
        topRatedPage.setSortOrder(SortOrder.fromText(order));
    }

    @Then("all {int} movies should be listed in {word} order of release year")
    public void moviesShouldBeInOrderOfReleaseYear(int expectedCount, String order) {
        int total = topRatedPage.getTotalTitles();
        assertEquals(expectedCount, total, "Total shown above the list");
        List<Integer> years = topRatedPage.getReleaseYears(total);
        assertEquals(expectedCount, years.size(), "Movies with a release year in the list");

        Comparator<Integer> comparator = SortOrder.fromText(order) == SortOrder.ASCENDING
                ? Comparator.naturalOrder() : Comparator.reverseOrder();
        List<Integer> sorted = new ArrayList<>(years);
        sorted.sort(comparator);
        assertEquals(sorted, years, () -> "Release years are not in " + order + " order");
    }
}
