package pageObjects;

import java.util.Locale;

import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;

/** IMDB's sign-in page, where signed-out users are sent when they try to rate or use the watchlist. */
public class SignInPage extends BasePage {

    public SignInPage(WebDriver driver) {
        super(driver);
    }

    @Override
    protected String path() {
        return "/registration/signin";
    }

    @Override
    public boolean isCurrent() {
        if (!super.isCurrent()) return false;
        afterNavigation();
        try {
            return waitFor(d -> String.valueOf(d.getTitle()).toLowerCase(Locale.ROOT).contains("sign in"));
        } catch (TimeoutException e) {
            return false;
        }
    }
}
