package pageObjects;

import java.util.List;
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
    public void load() {
        super.load();
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

    private WebElement shareItem(String option) {
        visible(SHARE_MENU_ITEMS);
        return driver.findElements(SHARE_MENU_ITEMS).stream()
                .filter(e -> e.getText().trim().equalsIgnoreCase(option))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No share option \"" + option + "\". Options: " + getShareOptions()));
    }
}
