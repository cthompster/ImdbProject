package pageObjects;

import java.util.regex.Pattern;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

/** IMDB title search, as opened from a genre chip on the Top 250 page (/search/title/?genres=...). */
public class GenreSearchResultsPage extends BasePage {
    private static final By RESULT_ITEMS = By.cssSelector("li.ipc-metadata-list-summary-item");
    /** The "1-50 of 2,640" range text above the results. */
    private static final By RESULT_RANGE = By.xpath(
            "//div[not(*)][starts-with(normalize-space(.), '1-')][contains(normalize-space(.), ' of ')]");
    private static final Pattern TOTAL = Pattern.compile("of\\s+(\\d[\\d,]*)");

    public GenreSearchResultsPage(WebDriver driver) {
        super(driver);
    }

    @Override
    protected String path() {
        return "/search/title/";
    }

    /** True when the genre appears in the page's list of applied filters. */
    public boolean isFilteredByGenre(String genre) {
        return isPresent(By.cssSelector("[data-testid='selected-input-chip-list-genres-" + genre + "']"));
    }

    /** Total number of matching titles, e.g. 2640 for "1-50 of 2,640". */
    public int getTotalResults() {
        return parseCount(visible(RESULT_RANGE).getText(), TOTAL);
    }

    public int getDisplayedResultCount() {
        visible(RESULT_ITEMS);
        return driver.findElements(RESULT_ITEMS).size();
    }
}
