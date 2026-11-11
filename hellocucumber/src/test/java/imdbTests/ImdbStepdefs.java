package imdbTests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
        GenreSearchResultsPage results = genreResults();
        assertTrue(results.isFilteredByGenre(genre),
                () -> "Expected \"" + genre + "\" to be an applied filter on " + results.getCurrentUrl());
    }

    @Then("the results should contain at least {int} title(s)")
    public void theResultsShouldContainAtLeast(int minimum) {
        GenreSearchResultsPage results = genreResults();
        int total = results.getTotalResults();
        assertTrue(total >= minimum, "Expected at least " + minimum + " titles but IMDB reports " + total);
        assertTrue(results.getDisplayedResultCount() > 0, "IMDB reports " + total + " titles but none are listed");
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

    @Then("the movies should be numbered from 1 to the total shown, in ranking order")
    public void theMoviesShouldBeNumberedInRankingOrder() {
        int total = topRatedPage.getTotalTitles();
        assertTrue(total > 0, "Expected the chart to report at least one title, but it reports " + total);
        List<Integer> ranks = topRatedPage.getRanks(total);
        List<Integer> expected = IntStream.rangeClosed(1, total).boxed().collect(Collectors.toList());
        assertEquals(expected, ranks, () -> "Rank badges on the " + ranks.size()
                + " titles listed are not 1 to " + total + " in order");
    }

    @Then("all movies should be listed in {word} order of IMDb rating")
    public void moviesShouldBeInOrderOfImdbRating(String order) {
        int total = topRatedPage.getTotalTitles();
        List<Double> ratings = topRatedPage.getImdbRatings(total);
        assertEquals(total, ratings.size(), "Movies with an IMDb rating in the list");
        assertTrue(ratings.stream().allMatch(r -> r >= 1.0 && r <= 10.0), () -> "IMDb ratings outside 1-10: " + ratings);

        Comparator<Double> comparator = SortOrder.fromText(order) == SortOrder.ASCENDING
                ? Comparator.naturalOrder() : Comparator.reverseOrder();
        List<Double> sorted = new ArrayList<>(ratings);
        sorted.sort(comparator);
        assertEquals(sorted, ratings, () -> "IMDb ratings are not in " + order + " order");
    }

    private GenreSearchResultsPage genreResults() {
        if (genreResults == null) {
            throw new IllegalStateException("There are no genre results to check. "
                    + "Put a \"When I refine by Genre ...\" step before it.");
        }
        return genreResults;
    }
}
