package pageObjects;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;

/** A movie's title page, e.g. https://www.imdb.com/title/tt0111161/ */
public class TitlePage extends BasePage {
    private static final By PAGE_TITLE = By.cssSelector("[data-testid='hero__pageTitle']");
    private static final By WATCHLIST_BUTTON = By.cssSelector("[data-testid='tm-box-wl-button']");
    private static final By RATE_BUTTON = By.cssSelector("[data-testid='hero-rating-bar__user-rating'] button");
    private static final By RATING_PROMPT = By.cssSelector("[data-testid='promptable__pc']");
    private static final By SUBMIT_RATING = By.xpath("//*[@data-testid='promptable__pc']//button[normalize-space()='Rate']");
    private static final By SHARE_BUTTON = By.cssSelector("[data-testid='hero-subnav-bar-share-button'] [data-testid='share-button']");
    private static final By SHARE_MENU_ITEMS = By.cssSelector("[data-testid='share-menu-list'] [role='menuitem']");
    private static final By SNACKBAR_TEXT = By.cssSelector("[data-testid='snackbase-text']");
    /** The IMDb rating in the hero rating bar, e.g. "9.3/10". */
    private static final By AGGREGATE_RATING = By.cssSelector("[data-testid='hero-rating-bar__aggregate-rating__score']");
    private static final By POSTER_IMAGE = By.cssSelector("[data-testid='hero-media__poster'] img");
    private static final By HEADINGS_LEVEL_1 = By.tagName("h1");
    private static final By HTML_ROOT = By.tagName("html");
    /** The year in the browser tab title: "The Shawshank Redemption (1994) - IMDb", "Fargo (TV Series 2014– ) - IMDb". */
    private static final Pattern TAB_TITLE_YEAR = Pattern.compile("\\([^()\\d]*(\\d{4})");
    /** Controls whose accessible name is checked, by the label used in the feature file. */
    private static final Map<String, By> CONTROLS = Map.of(
            "add to watchlist", WATCHLIST_BUTTON,
            "rate", RATE_BUTTON,
            "share", SHARE_BUTTON);

    public TitlePage(WebDriver driver) {
        super(driver);
    }

    @Override
    protected String path() {
        return "/title/" + getTitleId() + "/";
    }

    /** IMDB id of the movie this page shows, e.g. tt0111161 (the {@code titleId} setting). */
    public String getTitleId() {
        return config.getTitleId();
    }

    @Override
    protected void waitUntilLoaded() {
        visible(PAGE_TITLE);
    }

    public void addToWatchlist() {
        click(WATCHLIST_BUTTON);
    }

    /** Opens the "Rate this" prompt, picks a star (1-10) and presses Rate. */
    public void rate(int stars) {
        if (stars < 1 || stars > 10) {
            throw new IllegalArgumentException("IMDB ratings go from 1 to 10 stars, not " + stars);
        }
        click(RATE_BUTTON);
        visible(RATING_PROMPT);
        WebElement star = visible(By.cssSelector("[data-testid='promptable__pc'] button[aria-label='Rate " + stars + "']"));
        // The star buttons sit under a transparent touch layer, so click with a real mouse move
        // rather than WebElement.click(), which refuses covered elements.
        new Actions(driver).moveToElement(star).click().perform();
        click(SUBMIT_RATING);
    }

    public void openShareMenu() {
        click(SHARE_BUTTON);
        visible(SHARE_MENU_ITEMS);
    }

    public List<String> getShareOptions() {
        visible(SHARE_MENU_ITEMS);
        return driver.findElements(SHARE_MENU_ITEMS).stream()
                .map(WebElement::getText).map(String::trim).collect(Collectors.toList());
    }

    /** The link a share option points to, e.g. a facebook.com/sharer URL. Null for options without a link. */
    public String getShareLink(String option) {
        return shareItem(option).getDomAttribute("href");
    }

    public void chooseShareOption(String option) {
        WebElement item = shareItem(option);
        waitFor(ExpectedConditions.elementToBeClickable(item)).click();
    }

    /** Text of the pop-up notification at the bottom of the page, e.g. "Link copied". */
    public String getNotificationText() {
        return visible(SNACKBAR_TEXT).getText().trim();
    }

    /** The movie's name as shown in the page heading. */
    public String getHeading() {
        return visible(PAGE_TITLE).getText().trim();
    }

    /** Tag name of the page heading element, e.g. "h1". */
    public String getHeadingTagName() {
        return visible(PAGE_TITLE).getTagName().toLowerCase(Locale.ROOT);
    }

    /** Text of every visible level-1 heading on the page. */
    public List<String> getVisibleLevel1Headings() {
        visible(PAGE_TITLE);
        return driver.findElements(HEADINGS_LEVEL_1).stream()
                .filter(WebElement::isDisplayed)
                .map(WebElement::getText).map(String::trim).collect(Collectors.toList());
    }

    /** The browser tab title, e.g. "The Shawshank Redemption (1994) - IMDb". */
    public String getTabTitle() {
        return String.valueOf(driver.getTitle());
    }

    /** The release year shown in the browser tab title. */
    public int getReleaseYear() {
        return parseCount(getTabTitle(), TAB_TITLE_YEAR);
    }

    /** The IMDb rating shown at the top of the page, e.g. 9.3. */
    public double getImdbRating() {
        String text = String.valueOf(visible(AGGREGATE_RATING).getDomProperty("textContent")).trim();
        Matcher m = LEADING_RATING.matcher(text);
        if (!m.find()) throw new IllegalStateException("Expected an IMDb rating but found \"" + text + "\"");
        return Double.parseDouble(m.group(1));
    }

    /** The {@code lang} attribute of the page, e.g. "en-US"; empty if it has none. */
    public String getPageLanguage() {
        String lang = driver.findElement(HTML_ROOT).getDomAttribute("lang");
        return lang == null ? "" : lang.trim();
    }

    /** Accessible name the browser computes for a control ("Add to Watchlist", "Rate" or "Share"). */
    public String getAccessibleName(String control) {
        By locator = CONTROLS.get(control.trim().toLowerCase(Locale.ROOT));
        if (locator == null) {
            throw new IllegalArgumentException("Unknown control \"" + control + "\". Known controls: " + CONTROLS.keySet());
        }
        String name = visible(locator).getAccessibleName();
        return name == null ? "" : name.trim();
    }

    /** The alt text of the poster image, or null if it has none. */
    public String getPosterAltText() {
        return visible(POSTER_IMAGE).getDomAttribute("alt");
    }

    private WebElement shareItem(String option) {
        visible(SHARE_MENU_ITEMS);
        return driver.findElements(SHARE_MENU_ITEMS).stream()
                .filter(e -> e.getText().trim().equalsIgnoreCase(option))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No share option \"" + option + "\". Options: " + getShareOptions()));
    }
}
