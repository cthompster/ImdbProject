package pageObjects;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.PageFactory;

import managers.FileReaderManager;
import org.openqa.selenium.support.ui.Select;

import java.util.List;
import java.util.Set;

public class ImdbPage implements WebDriver {
    private WebDriver driver;

    public ImdbPage(WebDriver driver){
        this.driver = driver;
        PageFactory.initElements(driver, this);
    }

    public void filterTopRatedMovies() {
        driver.navigate().to(FileReaderManager.getInstance().getConfigReader().getApplicationUrl() + "/chart/top");
    }

    public void navigateTo_ImdbPage() {
        driver.get(FileReaderManager.getInstance().getConfigReader().getApplicationUrl());
    }

    public void refineByGenre(String genre) {
        driver.navigate().to(FileReaderManager.getInstance().getConfigReader().getApplicationUrl() + "/search/title?genres=" + genre + "&sort=user_rating,desc&title_type=feature&num_votes=25000,&pf_rd_m=A2FGELUUNOQJNL&pf_rd_p=5aab685f-35eb-40f3-95f7-c53f09d542c3&pf_rd_r=F850JMVQATEKBG13EN4J&pf_rd_s=right-6&pf_rd_t=15506&pf_rd_i=top&ref_=chttp_gnr_5");
    }

    public String getMovieTitleCount() {
        String actualResult = driver.findElement(By.xpath("//div[@class='desc']")).getText().substring(8,20);
        return actualResult;
    }

    public void sortMoviesByReleaseDate() {
        String sortValue = "Release Date";
        Select dropdown = new Select(driver.findElement(By.name("sort")));
        dropdown.selectByVisibleText(sortValue);
        driver.findElement(By.xpath("//span[@title='Descending order']")).click();
    }

    public String getEarliestMovieDate() {
        String actualFirstMovie = driver.findElement(By.xpath("//td[@class='titleColumn']")).getText().substring(4,18);
        return actualFirstMovie;
    }

    @Override
    public void get(String url) {

    }

    @Override
    public String getCurrentUrl() {
        return null;
    }

    @Override
    public String getTitle() {
        return null;
    }

    @Override
    public List<WebElement> findElements(By by) {
        return null;
    }

    @Override
    public WebElement findElement(By by) {
        return null;
    }

    @Override
    public String getPageSource() {
        return null;
    }

    @Override
    public void close() {

    }

    @Override
    public void quit() {

    }

    @Override
    public Set<String> getWindowHandles() {
        return null;
    }

    @Override
    public String getWindowHandle() {
        return null;
    }

    @Override
    public TargetLocator switchTo() {
        return null;
    }

    @Override
    public Navigation navigate() {
        return null;
    }

    @Override
    public Options manage() {
        return null;
    }
}
