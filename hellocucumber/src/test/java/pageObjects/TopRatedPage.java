package pageObjects;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;

import enums.SortOrder;

/** IMDB Top 250 movies chart: https://www.imdb.com/chart/top/ */
public class TopRatedPage extends BasePage {
    private static final By TOTAL_TITLES = By.cssSelector("[data-testid='chart-layout-total-items']");
    private static final By LIST_ITEMS = By.cssSelector("ul.ipc-metadata-list > li.ipc-metadata-list-summary-item");
    private static final By ITEM_TITLE = By.cssSelector(".ipc-title__text");
    /** First entry of each row's metadata line ("1994 · 2h 22m · R") is the release year. */
    private static final By RELEASE_YEARS = By.cssSelector(
            "ul.ipc-metadata-list > li.ipc-metadata-list-summary-item .cli-title-metadata li:first-child");
    private static final By SORT_SELECT = By.id("sort-by-selector");
    private static final By SORT_ORDER_BUTTON = By.id("swap-sort-order-button");

    /** React ignores a plain value change; use the native setter and fire 'change' like a user selection. */
    private static final String SELECT_OPTION_JS =
            "const setter = Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, 'value').set;"
          + "setter.call(arguments[0], arguments[1]);"
          + "arguments[0].dispatchEvent(new Event('change', {bubbles: true}));";

    public TopRatedPage(WebDriver driver) {
        super(driver);
    }

    @Override
    protected String path() {
        return "/chart/top/";
    }

    @Override
    public void load() {
        super.load();
        visible(LIST_ITEMS);
    }

    /** The "250 Titles" count shown above the list. */
    public int getTotalTitles() {
        return parseCount(visible(TOTAL_TITLES).getText());
    }

    /** Clicks a chip in the "Top rated movies by genre" section, which opens IMDB's title search for that genre. */
    public GenreSearchResultsPage refineByGenre(String genre) {
        click(By.xpath("//section[@data-testid='chart-layout-sidebar-genres']//a[normalize-space()="
                + xpathLiteral(genre) + "]"));
        waitFor(ExpectedConditions.urlContains("/search/title/"));
        afterNavigation();
        return new GenreSearchResultsPage(driver);
    }

    /**
     * Picks a sort field by its visible label, e.g. "Release date".
     * IMDB draws its own dropdown on top of a transparent native select, which Selenium's
     * {@link Select} refuses to use, so the option is chosen through the select element directly.
     */
    public void sortBy(String label) {
        WebElement select = waitFor(ExpectedConditions.presenceOfElementLocated(SORT_SELECT));
        List<WebElement> options = new Select(select).getOptions();
        String value = options.stream()
                .filter(o -> label.trim().equalsIgnoreCase(String.valueOf(o.getDomProperty("text")).trim()))
                .map(o -> o.getDomAttribute("value"))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No sort option \"" + label + "\". Options: "
                        + options.stream().map(o -> String.valueOf(o.getDomProperty("text"))).collect(Collectors.joining(", "))));
        if (value.equals(select.getDomProperty("value"))) return;
        String firstTitle = firstTitle();
        js(SELECT_OPTION_JS, select, value);
        waitFor(ExpectedConditions.urlContains("sort=" + value.toLowerCase(Locale.ROOT)));
        waitForFirstTitleToChangeFrom(firstTitle);
    }

    public SortOrder getSortOrder() {
        // The button's label names the order currently applied, e.g. "Ascending sort order".
        String label = String.valueOf(visible(SORT_ORDER_BUTTON).getDomAttribute("aria-label")).toLowerCase(Locale.ROOT);
        return label.startsWith("ascending") ? SortOrder.ASCENDING : SortOrder.DESCENDING;
    }

    public void setSortOrder(SortOrder order) {
        if (getSortOrder() == order) return;
        String firstTitle = firstTitle();
        click(SORT_ORDER_BUTTON);
        waitFor(ExpectedConditions.urlContains("%2C" + order.getUrlValue()));
        waitForFirstTitleToChangeFrom(firstTitle);
    }

    /**
     * Release year of every movie in the list, top to bottom. After a re-sort IMDB redraws the list in
     * batches, so this first waits until {@code expectedRows} rows have their year again.
     */
    public List<Integer> getReleaseYears(int expectedRows) {
        try {
            waitFor(d -> d.findElements(RELEASE_YEARS).size() >= expectedRows);
        } catch (TimeoutException e) {
            // Fall through: the caller's assertion reports how many rows were found.
        }
        return driver.findElements(RELEASE_YEARS).stream()
                .map(e -> String.valueOf(e.getDomProperty("textContent")).trim())
                .map(text -> {
                    if (!text.matches("\\d{4}")) {
                        throw new IllegalStateException("Expected a release year but found \"" + text + "\"");
                    }
                    return Integer.valueOf(text);
                })
                .collect(Collectors.toList());
    }

    private String firstTitle() {
        return visible(LIST_ITEMS).findElement(ITEM_TITLE).getText();
    }

    private void waitForFirstTitleToChangeFrom(String previous) {
        waitFor(d -> {
            List<WebElement> titles = d.findElements(ITEM_TITLE);
            return !titles.isEmpty() && !titles.get(0).getText().equals(previous);
        });
    }

}
