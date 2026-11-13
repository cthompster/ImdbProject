**This is the IMDB Coding Assignment**

The objective is to implement BDD using cucumber.io and Selenium to test the IMDB website's behavior.
The project uses Java 17, Maven, Cucumber 7, Selenium 4 and JUnit 5 (see `pom.xml`).

It follows basic BDD design: feature files with Scenarios and Scenario Outlines, step definitions,
and the Selenium Page Object Model. For class documentation see `javaDoc/index.html`.

**Requirements**

    - JDK 17 or newer
    - Maven 3.9 or newer
    - Google Chrome (Firefox and Safari also work, see below)

No chromedriver download or driver path is needed: Selenium Manager fetches a driver that
matches your installed browser automatically.

**Run The Tests**

    From the command line:
        cd ImdbProject/hellocucumber
        mvn test

    Options (any key in configuration.properties can be overridden with -D):
        mvn test -Dheadless=true          run without a visible browser window (IMDB may block this)
        mvn test -Dbrowser=firefox        use Firefox (or safari)
        mvn test -DtitleId=tt0068646      use a different movie for title_display_page.feature

    From the IDE (IntelliJ):
        1. Open the hellocucumber project (pom.xml)
        2. Run imdbTests/RunImdbTests, or right-click a .feature file and choose Run

    Report: target/cucumber-reports/imdb.html (the latest run). Failed scenarios include a screenshot
    and the URL. Every run of RunImdbTests (Maven or IntelliJ, not a right-click run of one .feature
    file) is also kept as a numbered report in test-history/, listed in test-history/index.html with
    its scenario counts (newest 50 full/filtered runs and newest 10 dry runs kept; historyKeep and
    historyKeepDryRuns, 0 keeps all).

**IMDB's "confirm you are human" check**

IMDB sometimes shows an Amazon CAPTCHA to automated browsers. When that happens the run prints a
message and waits (up to `captchaWaitSeconds`, default 180) for you to solve it in the Chrome window.
Chrome uses its own profile in `chrome-profile/` (not in git), so once solved the check usually
stays passed for the rest of the run and for later runs. Each scenario still starts signed out:
every cookie except the CAPTCHA pass is cleared when the browser opens. Headless runs can't show the CAPTCHA;
if it appears they stop with an explanation. Run once with a visible browser first.
IMDB may also refuse headless browsers outright ("403 Forbidden"); as of November 2026 it does so
for every page the suite uses. The run then stops right away with an explanation. Use a visible browser.

**Key Files**

    Feature files (src/test/resources/imdbTests):
        top_rated_movies.feature      genre filter, release-date and rating sorting, ranking order,
                                      HTTPS and load time on the Top 250 chart
        title_display_page.feature    watchlist, rating and sharing; name, year and rating shown;
                                      screen-reader checks, HTTPS and load time on a movie's page;
                                      where the sign-in page's options lead

    Step definitions (src/test/java/imdbTests):
        CommonSteps.java              page loading, signed-out and "taken to page" checks,
                                      HTTPS, mixed-content and load-time checks
        ImdbStepdefs.java             Top 250 steps
        TitleDisplayStepDefs.java     title page steps
        SignInStepDefs.java           sign-in page options and where they lead
        Hooks.java                    screenshot on failure and closing the browser

    Page objects (src/test/java/pageObjects):
        TopRatedPage, GenreSearchResultsPage, TitlePage, SignInPage (all extend BasePage)

    Test runners:
        imdbTests/RunImdbTests.java         the IMDB features
        hellocucumber/RunCucumberTest.java  the Cucumber "Is it Friday yet?" tutorial

    Configuration:
        src/test/resources/configuration.properties   browser, URL, waits, movie, profile folder
        src/test/resources/junit-platform.properties  skips @manual scenarios (Maven, RunImdbTests)
        src/test/resources/cucumber.properties        skips @manual scenarios (IntelliJ right-click Run)

**Manual scenarios**

Scenarios that need real IMDb, Amazon, Google or Apple accounts (creating an account and
signing in) are tagged `@manual`. They document the expected behavior but are not automated,
because they need real credentials and run into CAPTCHAs and 2-step verification.
Their signed-out part is automated: the sign-in page offers each option, and following it reaches
the expected site over HTTPS (checked by address only). No credentials are entered and no form is submitted.
