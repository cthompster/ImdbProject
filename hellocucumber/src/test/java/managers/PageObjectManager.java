package managers;

import enums.PageName;
import pageObjects.BasePage;
import pageObjects.SignInPage;
import pageObjects.TitlePage;
import pageObjects.TopRatedPage;

/** Creates page objects on first use. The browser itself only starts when a page is first requested. */
public class PageObjectManager {
    private final WebDriverManager webDriverManager;
    private TopRatedPage topRatedPage;
    private TitlePage titlePage;
    private SignInPage signInPage;

    public PageObjectManager(WebDriverManager webDriverManager) {
        this.webDriverManager = webDriverManager;
    }

    public TopRatedPage getTopRatedPage() {
        if (topRatedPage == null) topRatedPage = new TopRatedPage(webDriverManager.getDriver());
        return topRatedPage;
    }

    public TitlePage getTitlePage() {
        if (titlePage == null) titlePage = new TitlePage(webDriverManager.getDriver());
        return titlePage;
    }

    public SignInPage getSignInPage() {
        if (signInPage == null) signInPage = new SignInPage(webDriverManager.getDriver());
        return signInPage;
    }

    public BasePage getPage(PageName name) {
        switch (name) {
            case TOP_RATED_MOVIES:
                return getTopRatedPage();
            case TITLE_DISPLAY:
                return getTitlePage();
            case SIGN_IN:
                return getSignInPage();
            default:
                throw new IllegalArgumentException("No page object for " + name);
        }
    }
}
