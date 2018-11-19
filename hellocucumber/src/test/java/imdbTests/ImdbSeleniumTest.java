package imdbTests;



import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.concurrent.TimeUnit;

//import org.openqa.selenium.firefox.FirefoxDriver;


public class ImdbSeleniumTest {

    private static String url = "http://www.imdb.com/chart/top";
    private static String genre = "Comedy";
    private static String comedyResults = "1,662 titles";
    private static String firstMovieTitle = "The Chaos Class";

    public static void main(String[] args) {

        refineTopRatedMoviesByGenre();
        sortTopRatedMoviesByReleaseDate();

    }

    @Test
    public static void refineTopRatedMoviesByGenre() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--disable-notifications");
        options.addArguments("--start-maximized");
        options.addArguments("disable-infobars");
        System.setProperty("webdriver.chrome.driver", "/Users/christiant/tools/web_drivers/chromedriver");
        WebDriver driver = new ChromeDriver(options);

        // Implicit wait
        driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);
        WebDriverWait wait = new WebDriverWait(driver, 10);

        //Launch the top rated films page
        System.out.println("Loading the top rated movies page: ===> http://www.imdb.com/chart/top ");
        driver.get(url);


        // Refine the search by genre 'Comedy'
        System.out.println("Refining search to genre: \"Comedy\" ===> .../search/title?genres=comedy&sort=user_rating,desc");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.linkText("Comedy")));
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.linkText("Comedy")));
        driver.findElement(By.linkText(genre)).click();

        // driver.findElement(By.xpath("//*[@id='popsubform']/a/img")).click();  // Should close pop-up Ads

        // Assert 1: verify movie result list total
        String actualMovieTitleCount = driver.findElement(By.xpath("//div[@class='desc']")).getText().substring(8,20);
        System.out.println(actualMovieTitleCount);
        String expectedMovieTitleCount = comedyResults;
        Assert.assertEquals(actualMovieTitleCount, expectedMovieTitleCount);


        /*
          Assert 2: Verify first movie in result list (page:1)
         */
        //driver.findElement(By.linkText("The Chaos Class")).click();
        String actualFirstMovie = driver.findElement(By.xpath("//h3[@class='lister-item-header']")).getText().substring(3,18);
        System.out.println(actualFirstMovie);
        String expectedFirstMovieTitle = firstMovieTitle;
        Assert.assertEquals(actualFirstMovie, expectedFirstMovieTitle);

        // Print a Log In message to the screen
        System.out.println("Search Completed Successfully");

        // Close the driver
        driver.quit();
    }

    @Test
    public static void sortTopRatedMoviesByReleaseDate() {

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--disable-notifications");
        options.addArguments("--start-maximized");
        options.addArguments("disable-infobars");
        System.setProperty("webdriver.chrome.driver", "/Users/christiant/tools/web_drivers/chromedriver");
        WebDriver driver = new ChromeDriver(options);

        //Put a Implicit wait, this means that any search for elements on the page could take the time the implicit wait is set for before throwing exception

        driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);
        WebDriverWait wait = new WebDriverWait(driver, 10);

        //Launch the top rated films page
        System.out.println("Loading the Top Rated movies page: ===> sorted by Release Date & Descending Order");
        driver.get(url);
        //Thread.sleep(15000);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.name("sort")));
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("sort")));

        String sortValue = "Release Date";
        System.out.println("Sort movies by Release Date and Descending Order ===> ");


        Select dropdown = new Select(driver.findElement(By.name("sort")));
        dropdown.selectByVisibleText(sortValue);
        driver.findElement(By.xpath("//span[@title='Descending order']")).click();

        /*
          Assert 1: verify movie list sorted by Release Date
         */
        String actualMovieTitleCount = driver.findElement(By.xpath("//div[@class='desc']")).getText().substring(8,18);
        System.out.println(actualMovieTitleCount);
        String expectedMovieTitleCount = "250 Titles";
        Assert.assertEquals(actualMovieTitleCount, expectedMovieTitleCount);


        /*
          Assert 2: Verify first movie in result list (page:1)
         */
        //driver.findElement(By.linkText("The Chaos Class")).click();
        String actualFirstMovie = driver.findElement(By.xpath("//td[@class='titleColumn']")).getText().substring(4,18);
        System.out.println(actualFirstMovie);
        String expectedFirstMovieTitle = "The Kid (1921)";
        Assert.assertEquals(actualFirstMovie, expectedFirstMovieTitle);

        // Close the driver
        driver.quit();
    }

}