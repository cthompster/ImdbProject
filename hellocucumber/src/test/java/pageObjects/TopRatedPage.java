package pageObjects;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    /** Title of each row in the list; the page heading uses the same class, so stay inside the list. */
    private static final By ROW_TITLES = By.cssSelector(
            "ul.ipc-metadata-list > li.ipc-metadata-list-summary-item .ipc-title__text");
    /** First entry of each row's metadata line ("1994 · 2h 22m · R") is the release year. */
    private static final By RELEASE_YEARS = By.cssSelector(
            "ul.ipc-metadata-list > li.ipc-metadata-list-summary-item .cli-title-metadata li:first-child");
    /** Each row's IMDb rating, e.g. "9.3 (3.3M)". */
    private static final By ROW_RATINGS = By.cssSelector(
            "ul.ipc-metadata-list > li.ipc-metadata-list-summary-item [data-testid='ratingGroup--imdb-rating']");
    /** Each row's rank badge, shown before the title, e.g. "#1". */
    private static final By ROW_RANKS = By.cssSelector(
            "ul.ipc-metadata-list > li.ipc-metadata-list-summary-item [data-testid='title-list-item-ranking']");
    /** The number in a rank badge: "#1" gives 1. */
    private static final Pattern RANK = Pattern.compile("^#(\\d+)$");
    /** A rating such as "9.3" at the start of the row's rating text. */
    private static final Pattern RATING = Pattern.compile("^(\\d{1,2}(?:\\.\\d)?)");
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
    protected void waitUntilLoaded() {
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
                // The property falls back to the option's text when it has no value attribute, as the browser does.
                .map(o -> String.valueOf(o.getDomProperty("value")))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No sort option \"" + label + "\". Options: "
                        + options.stream().map(o -> String.valueOf(o.getDomProperty("text"))).collect(Collectors.joining(", "))));
        if (value.equals(select.getDomProperty("value"))) return;
        String firstTitle = firstTitle();
        js(SELECT_OPTION_JS, select, value);
        waitFor(ExpectedConditions.urlContains("sort=" + value.toLowerCase(Locale.ROOT)));
        // Most sort fields put a different movie first; wait for that so callers don't read the old list.
        // Some (e.g. "IMDb rating" from "Ranking") may keep the same movie first, so a timeout here isn't an error.
        try {
            waitForFirstTitleToChangeFrom(firstTitle);
        } catch (TimeoutException e) {
            // The URL already shows the new sort; the list kept its first movie.
        }
    }

    public SortOrder getSortOrder() {
        // The button's label names the order currently applied, e.g. "Ascending sort order".
        String label = visible(SORT_ORDER_BUTTON).getDomAttribute("aria-label");
        String lower = String.valueOf(label).toLowerCase(Locale.ROOT);
        if (lower.startsWith("ascending")) return SortOrder.ASCENDING;
        if (lower.startsWith("descending")) return SortOrder.DESCENDING;
        throw new IllegalStateException("Can't tell the sort order from the sort button's label: \"" + label + "\"");
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

    /**
     * The rank badge of each row, top to bottom ("#1" gives 1).
     * First waits until {@code expectedRows} rows are drawn.
     */
    public List<Integer> getRanks(int expectedRows) {
        return readRows(ROW_RANKS, expectedRows, RANK, "a rank such as \"#1\"", Integer::valueOf);
    }

    /** The IMDb rating of each row, top to bottom. First waits until {@code expectedRows} rows have one. */
    public List<Double> getImdbRatings(int expectedRows) {
        return readRows(ROW_RATINGS, expectedRows, RATING, "an IMDb rating", Double::valueOf);
    }

    private <T> List<T> readRows(By locator, int expectedRows, Pattern pattern, String what, Function<String, T> parse) {
        try {
            waitFor(d -> d.findElements(locator).size() >= expectedRows);
        } catch (TimeoutException e) {
            // Fall through: the caller's assertion reports how many rows were found.
        }
        return driver.findElements(locator).stream()
                .map(e -> String.valueOf(e.getDomProperty("textContent")).trim())
                .map(text -> {
                    Matcher m = pattern.matcher(text);
                    if (!m.find()) {
                        throw new IllegalStateException("Expected " + what + " but found \"" + text + "\"");
                    }
                    return parse.apply(m.group(1));
                })
                .collect(Collectors.toList());
    }

    private String firstTitle() {
        return visible(ROW_TITLES).getText();
    }

    private void waitForFirstTitleToChangeFrom(String previous) {
        waitFor(d -> {
            List<WebElement> titles = d.findElements(ROW_TITLES);
            return !titles.isEmpty() && !titles.get(0).getText().equals(previous);
        });
    }

}
