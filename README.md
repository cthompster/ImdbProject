# ImdbProject

An automated test suite for the live [IMDb](https://www.imdb.com) website. The tests are written as
Behaviour-Driven Development (BDD) scenarios with [Cucumber](https://cucumber.io) and drive a real
web browser with [Selenium](https://www.selenium.dev). The project uses Java 17, Maven, Cucumber 7,
Selenium 4 and JUnit 5. Everything lives in the [`hellocucumber`](hellocucumber) folder.

## What it tests

**Top 250 Movies chart** (`top_rated_movies.feature`)
- Refining the list by genre (Comedy, Action, Drama) opens IMDb's search filtered by that genre, and it returns titles.
- Sorting by release date, ascending or descending, lists all 250 movies in that order of release year.
- Sorting by IMDb rating, lowest first, lists the ratings in ascending order.
- The movies are numbered #1 to #250 in ranking order.
- Opening its http:// address ends up on HTTPS, nothing loads over plain HTTP, and it loads within 10 seconds.

**A movie's title page** (`title_display_page.feature`; default movie: *The Shawshank Redemption*, `tt0111161`)
- Adding to the Watchlist or rating the movie while signed out sends you to the sign-in page.
- The sign-in page offers *Create an account*, which leads to Amazon's sign-in page, and
  *Sign in to an existing account*, which reveals sign-in with Amazon, IMDb, Google and Apple; each
  reaches the expected site over HTTPS (checked by address only; nothing is typed or submitted).
- The share menu offers Facebook, Twitter and Email, each linking to this movie.
- *Copy link* shows the message "Link copied".
- The page shows the movie's name, release year and IMDb rating.
- It works with a screen reader: one main heading, a declared language, named controls and poster alt text.
- Opening its http:// address ends up on HTTPS, nothing loads over plain HTTP, and it loads within 10 seconds.

Security and performance checks are passive: they only look at what a normal visitor's browser
receives. There is no load testing or attack testing against IMDb.

Scenarios that need real accounts (creating an account, signing in with IMDb, Amazon, Google or
Apple) are tagged `@manual`. They document the expected behaviour and are skipped in automated runs; only their
signed-out part (above) is automated.

The project also includes Cucumber's "Is it Friday yet?" tutorial as a simple example.

## Running the tests

### Requirements
- JDK 17 or newer
- Maven 3.9 or newer
- Google Chrome (Firefox and Safari also work)

No browser driver download is needed: Selenium Manager fetches one that matches your browser.

### From the command line
```
cd hellocucumber
mvn test
```

A Chrome window opens and runs each scenario. A full run takes a minute or two.

Options (any setting in `src/test/resources/configuration.properties` can be overridden with `-D`):

| Command | Effect |
|---------|--------|
| `mvn test -Dbrowser=firefox` | Use Firefox (or `safari`) |
| `mvn test -DtitleId=tt0068646` | Test a different movie's page |
| `mvn test -Dheadless=true` | No browser window (currently blocked by IMDb, see below) |

### From IntelliJ
Open `hellocucumber/pom.xml` as a project, then run `imdbTests/RunImdbTests`, or right-click a
`.feature` file and choose **Run**.

### If IMDb asks "confirm you are human"
IMDb sometimes shows a CAPTCHA to automated browsers. The run prints a message and waits (up to 3
minutes by default) for **you** to solve it in the Chrome window; the suite never tries to get around
it. Once solved, the pass is kept in the `hellocucumber/chrome-profile/` folder, so it usually lasts
for the rest of the run and for later runs. IMDb currently refuses headless browsers ("403 Forbidden"),
so run with a visible browser.

## Viewing the test results

**In the terminal**, Maven prints a summary at the end of the run:
```
[INFO] Tests run: 30, Failures: 0, Errors: 0, Skipped: 5
[INFO] BUILD SUCCESS
```
"Skipped" counts the `@manual` scenarios.

**HTML report** (the easiest way to read the results): open it in a browser after the run.

| Report | What it shows |
|--------|---------------|
| `hellocucumber/target/cucumber-reports/imdb.html` | The IMDb scenarios: each scenario and step, pass/fail and timings. A failed scenario includes a screenshot of the browser and the page URL. |
| `hellocucumber/target/cucumber-reports/hellocucumber.html` | The "Is it Friday yet?" tutorial |

On a Mac you can open it from the terminal:
```
open hellocucumber/target/cucumber-reports/imdb.html
```

**For CI tools:** JUnit XML and text summaries are in `hellocucumber/target/surefire-reports/`.

Each run replaces the previous reports. `target/` is not stored in git.

## More information
- [`hellocucumber/README.md`](hellocucumber/README.md): detailed setup, configuration and the project's key files
- [`hellocucumber/WIKI.md`](hellocucumber/WIKI.md): how the suite works (also as [`WIKI.mediawiki`](hellocucumber/WIKI.mediawiki))
- `hellocucumber/javaDoc/index.html`: class documentation
