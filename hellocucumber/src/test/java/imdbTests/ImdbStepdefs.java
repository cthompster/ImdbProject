package imdbTests;

import cucumber.api.java.en.And;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import pageObjects.ImdbPage;

import static org.junit.Assert.assertEquals;

public class ImdbStepdefs {

    private ImdbPage imdbPage;
    private TestContext testContext;

    public ImdbStepdefs(TestContext context) {
        imdbPage = context.getPageObjectManager().getImdbPage();
        testContext = new TestContext();
    }


    @Given("^I load the \"([^\"]*)\"$")
    public void i_load_the_Top_Rated_Movies_page(String imdbPage) {
        this.imdbPage.navigateTo_ImdbPage();
        this.imdbPage.filterTopRatedMovies();
        System.out.println("[ ==> Step 1: Loaded Top Rated Movies page ");
        System.out.println();
    }

    @And("^I refine by Genre \"([^\"]*)\"$")
    public void i_refine_by_Genre(String genre) {
        imdbPage.refineByGenre(genre);
        System.out.println("[ ==> Step 2: Refined movies by genre: " + genre );
        System.out.println();
    }

    @And("^I sort the list by \"([^\"]*)\"$")
    public void i_sort_the_list_by_Release_Date(String releaseDate) {
        imdbPage.sortMoviesByReleaseDate();
        System.out.println("[ ==> Step 2: Sorted movies by: " + releaseDate );
        System.out.println();
    }


    @Then("^the list of movies should only contain \"([^\"]*)\"$")
    public void the_list_of_movies_should_only_contain_relevant_results(String expectedResults) {
        String actualResults = imdbPage.getMovieTitleCount();
        assertEquals(expectedResults, actualResults);
        System.out.println("[ ==> Step 3: Verified results - Expected: <" + expectedResults + ">  ..and found: <" + actualResults + ">");
        System.out.println();
        testContext.getWebDriverManager().closeDriver();
    }

    @Then("^the list of movies should be displayed in order of \"([^\"]*)\"$")
    public void the_list_of_movies_should_be_displayed_in_order_of(String descendingReleaseDate) {
        String expectedFirstMovie = "The Kid (1921)";
        String actualFirstMovie = imdbPage.getEarliestMovieDate();
        assertEquals(expectedFirstMovie, actualFirstMovie);
        System.out.println("[ ==> Step 3: Verified results - Expected: <" + expectedFirstMovie + ">  ..and found: <" + actualFirstMovie + ">");
        System.out.println();
        testContext.getWebDriverManager().closeDriver();
    }



}