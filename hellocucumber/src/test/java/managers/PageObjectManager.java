package managers;

import org.openqa.selenium.WebDriver;
import pageObjects.ImdbPage;

public class PageObjectManager {
    private WebDriver driver;
    private ImdbPage imdbPage;

    public PageObjectManager(WebDriver driver) {

        this.driver = driver;

    }


    public ImdbPage getImdbPage() {

        return (imdbPage == null) ? imdbPage = new ImdbPage(driver) : imdbPage;

    }

}
