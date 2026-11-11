# ImdbProject

**ImdbProject** is an automated test suite that checks the behaviour of the live [IMDb](https://www.imdb.com) website. It is written as Behaviour-Driven Development (BDD) scenarios with [Cucumber](https://cucumber.io) and drives a real web browser with [Selenium](https://www.selenium.dev).

## Overview

The suite lives in the `hellocucumber` folder. Plain-English feature files describe what a visitor should be able to do on IMDb; step definitions turn each line into browser actions through page objects; and the results are written to an HTML report.

- **Language and build:** Java 17, Maven
- **Libraries:** Cucumber 7, Selenium 4, JUnit 5 (JUnit Platform Suite), PicoContainer
- **Browsers:** Google Chrome (default), Firefox and Safari
- **Driver setup:** none needed; Selenium Manager downloads a matching browser driver automatically

## What is tested

### Top 250 Movies chart (`top_rated_movies.feature`)

| Scenario | What it checks |
|----------|----------------|
| Refining the list by genre | Choosing *Comedy*, *Action* or *Drama* opens IMDb's search filtered by that genre, and it returns titles |
| Sorting by release date | After sorting by release date, ascending or descending, all 250 movies are listed in that order of release year |
| Sorting by IMDb rating | After sorting by IMDb rating, lowest first, every rating is 1 to 10 and none is lower than the one above it (ascending, because the default ranking is already close to highest-first) |
| Numbered in ranking order | The rank badges run #1, #2, ... up to the total shown above the list |
| Served only over HTTPS | Opening the plain `http://` address ends up on HTTPS (Chrome may upgrade it itself), and nothing on the page loads over plain HTTP, including `srcset` images |
| Loads within a reasonable time | The page is ready (DOMContentLoaded) within 10 seconds; timings are printed on each run (skipped if a CAPTCHA was shown) |

### Movie title page (`title_display_page.feature`)

The movie is set in configuration (default: *The Shawshank Redemption*, `tt0111161`). All automated scenarios run while signed out.

| Scenario | What it checks |
|----------|----------------|
| Add to Watchlist | A signed-out visitor is sent to the sign-in page |
| Rate the movie (1 and 10 stars) | A signed-out visitor is sent to the sign-in page |
| Share on social media | The share menu offers Facebook, Twitter and Email, each linking to this movie |
| Copy a link | Choosing *Copy link* shows the message "Link copied" |
| Name, release year and rating | The heading matches the browser tab, the year is plausible and the IMDb rating is 1 to 10 |
| Usable with a screen reader | One main heading, a declared page language, accessible names on Add to Watchlist, Rate and Share, and alt text on the poster |
| Served only over HTTPS | Opening the plain `http://` address ends up on HTTPS (Chrome may upgrade it itself), and nothing on the page loads over plain HTTP, including `srcset` images |
| Loads within a reasonable time | The page is ready (DOMContentLoaded) within 10 seconds; timings are printed on each run (skipped if a CAPTCHA was shown) |

Security and performance checks are passive: they only look at what a normal visitor's browser receives. There is no load testing or attack testing against IMDb.

### Manual scenarios

Creating an account and signing in with IMDb, Amazon, Facebook or Google need real accounts, CAPTCHAs and 2-step verification. They are kept in the feature file as documentation, tagged `@manual`, and skipped in automated runs.

The project also contains Cucumber's "Is it Friday yet?" tutorial (8 scenarios) as a simple example.

## How it works

- **Feature files** (`src/test/resources/imdbTests`) describe the behaviour in Gherkin (*Given / When / Then*).
- **Step definitions** (`src/test/java/imdbTests`) map each step to code. `Hooks` takes a screenshot when a scenario fails and always closes the browser.
- **Page objects** (`src/test/java/pageObjects`): `TopRatedPage`, `GenreSearchResultsPage`, `TitlePage` and `SignInPage` hold all selectors. They share `BasePage`, which provides explicit waits, scrolling and clicking helpers, and IMDb bot-protection handling.
- **Managers** (`src/test/java/managers`) start and stop the browser and create page objects. **Configuration** comes from `configuration.properties`, and any value can be overridden with `-D`.
- Each scenario gets a fresh browser and starts signed out.

## IMDb bot protection

IMDb sometimes shows a "confirm you are human" CAPTCHA to automated browsers. The suite does **not** try to get around it:

- With a visible browser, it prints a message and waits (default 180 seconds) for a person to solve the CAPTCHA.
- Chrome uses its own profile folder (`chrome-profile/`, not in git) and keeps only the CAPTCHA-pass cookie between scenarios, so one solve usually lasts for the whole run and later runs.
- Headless runs can't show a CAPTCHA, and IMDb currently refuses headless browsers with "403 Forbidden". In both cases the run stops at once with an explanation.

## Running the tests

Requirements: JDK 17 or newer, Maven 3.9 or newer, and Google Chrome.

    cd ImdbProject/hellocucumber
    mvn test

Common options:

| Command | Effect |
|---------|--------|
| `mvn test -Dbrowser=firefox` | Use Firefox (or `safari`) |
| `mvn test -DtitleId=tt0068646` | Test a different movie's page |
| `mvn test -Dheadless=true` | No browser window (currently blocked by IMDb) |

In IntelliJ, run `imdbTests/RunImdbTests`, or right-click a `.feature` file and choose *Run*.

The report is written to `target/cucumber-reports/imdb.html`. Failed scenarios include a screenshot and the page URL.

## Current status

As of 11 November 2026, all 20 automated IMDb scenarios and all 8 tutorial scenarios pass against the live site; the 5 `@manual` scenarios are skipped.

## See also

- `hellocucumber/README.md`: setup and usage
- `hellocucumber/javaDoc/index.html`: class documentation
