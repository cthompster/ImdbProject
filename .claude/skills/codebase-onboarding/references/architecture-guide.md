# Architecture: resources and dependencies

A map of how the suite is put together. It is a starting point, not a substitute for the source:
open the files before citing them. Paths are relative to `hellocucumber/src/test/` unless they start
with `hellocucumber/`. The rules behind many of these choices are in `CLAUDE.md`.

## 1. Overview

- One Maven module, `hellocucumber/` (`hellocucumber/pom.xml`). Everything is **test code**: there is
  no `src/main`, and nothing is packaged or deployed. The "product" is the test run and its report.
- It tests the **live** site https://www.imdb.com (and, for sign-in links, Amazon, Google and Apple)
  through a real browser.
- It also contains Cucumber's "Is it Friday yet?" tutorial (package `hellocucumber`), which is
  independent of the IMDb code.

## 2. Layers

```
  resources/imdbTests/*.feature        Gherkin: what is tested, in plain language
              │  matched by Cucumber expressions
              ▼
  java/imdbTests/*Steps*.java          Step definitions: glue, assertions (JUnit 5)
              │  ask TestContext for page objects
              ▼
  java/pageObjects/                    Page objects: selectors + page actions
     BasePage ◄── TopRatedPage, GenreSearchResultsPage, TitlePage, SignInPage
              │  WebDriver calls, explicit waits
              ▼
  java/managers/WebDriverManager       Starts/quits the browser, Chrome profile, cookie reset
              │
              ▼
  Selenium 4 WebDriver ─► Chrome / Firefox / Safari ─► imdb.com (+ amazon.com, Google, Apple)
```

Rules that keep the layers clean:
- Selectors live **only** in page objects. Steps never touch `WebDriver` or `By`.
- Waiting, CAPTCHA detection and 403 handling live **only** in `BasePage`.
- Configuration is read through `FileReaderManager` → `ConfigFileReader`, never directly from files.

## 3. Components

| Package (`java/`) | Classes | Responsibility |
|---|---|---|
| `imdbTests` | `CommonSteps`, `ImdbStepdefs`, `TitleDisplayStepDefs`, `SignInStepDefs` | Step definitions. `CommonSteps` holds page-loading, signed-out, HTTPS, mixed-content, load-time and "taken to page" steps; the others hold steps for one page each. |
| | `Hooks` | `@After`: screenshot + URL on failure, then always quit the browser. |
| | `TestContext` | Per-scenario state: one `WebDriverManager` + one `PageObjectManager`. |
| | `RunImdbTests` | JUnit Platform `@Suite` for the IMDb features. |
| `pageObjects` | `BasePage` (abstract) | `load()` / `loadOverPlainHttp()`, `path()`, `waitUntilLoaded()`, `waitFor` / `visible` / `isPresent` / `click` / `scrollToCenter`, CAPTCHA wait, 403 check, navigation timings, mixed-content check. |
| | `TopRatedPage` | `/chart/top/`: list rows, genre refine, sort. |
| | `GenreSearchResultsPage` | `/search/title/`: created by `TopRatedPage.refineByGenre`, not by `PageObjectManager`. |
| | `TitlePage` | `/title/<titleId>/`: watchlist, rating stars, share menu, accessibility checks. |
| | `SignInPage` | `/registration/signin`: the options and where they lead (address only). |
| `managers` | `WebDriverManager` | Lazy browser start, options per browser, Chrome profile lock message, `startSignedOut` cookie reset. |
| | `PageObjectManager` | Lazily creates page objects; `getPage(PageName)` maps feature-file names to them. |
| | `FileReaderManager` | Singleton that reads `configuration.properties` once. |
| `dataProviders` | `ConfigFileReader` | Typed getters; each key's `-D` system property overrides the file. |
| `enums` | `PageName`, `DriverType`, `EnvironmentType`, `SortOrder` | Allowed values, with clear errors for unknown ones. |
| `hellocucumber` | `IsItFriday`, `Stepdefs`, `RunCucumberTest` | The tutorial. |

## 4. Runtime wiring: one scenario, start to finish

```
mvn test
 └─ Surefire 3 ─► JUnit Platform
     ├─ RunImdbTests   (@Suite, engine "cucumber", glue imdbTests,
     │                  plugins: pretty + html:target/cucumber-reports/imdb.html)
     │   reads junit-platform.properties  → cucumber.filter.tags=not @manual
     │
     │   for each scenario:
     │     PicoContainer creates ONE TestContext
     │       ├─ WebDriverManager   (no browser yet)
     │       └─ PageObjectManager(WebDriverManager)
     │     and injects it into every step class + Hooks that the scenario uses
     │
     │     first page request ─► WebDriverManager.getDriver()
     │        → new ChromeDriver(EAGER, --user-data-dir=chrome-profile, …)
     │        → startSignedOut: clear all cookies except aws-waf-token (via CDP)
     │        → implicit wait = 0
     │     steps run ─► page objects ─► BasePage waits / CAPTCHA wait / 403 check
     │     Hooks.tearDown ─► screenshot if failed ─► quitDriver()  (always)
     │
     └─ RunCucumberTest (@Suite, glue hellocucumber → target/cucumber-reports/hellocucumber.html)
```

Notes:
- The browser starts **lazily**, so a scenario that fails before its first page never opens one.
- One shared Chrome profile means scenarios run **one at a time**. A second Chrome on the same profile
  fails, and `WebDriverManager` explains how to clear the `SingletonLock`.
- IntelliJ's right-click Run uses Cucumber's CLI runner, which reads `cucumber.properties` instead of
  `junit-platform.properties`. The two files must stay in step.

## 5. Internal dependencies

```
step classes ──► TestContext ──► PageObjectManager ──► page objects ──► BasePage
     │                 └───────► WebDriverManager ◄──────┘ (driver passed in)
     └──► enums (PageName, SortOrder)
Hooks ──► TestContext ──► WebDriverManager
BasePage, WebDriverManager ──► FileReaderManager ──► ConfigFileReader ──► enums (DriverType, EnvironmentType)
```

There are no cycles. The `hellocucumber` tutorial depends on none of this.

## 6. External dependencies (`hellocucumber/pom.xml`)

All dependencies are `test` scope.

| Dependency | Version | Used for |
|---|---|---|
| `org.seleniumhq.selenium:selenium-java` | 4.52.0 | WebDriver, waits, `Actions`, CDP (cookie reset). Built-in **Selenium Manager** downloads the matching browser driver, so no chromedriver install or path is needed. |
| `io.cucumber:cucumber-bom` (import) | 7.34.9 | Pins the versions of the three Cucumber artifacts below |
| `cucumber-java` | (BOM) | `@Given/@When/@Then`, `@After`, `Scenario` |
| `cucumber-picocontainer` | (BOM) | Constructor injection of `TestContext`, one per scenario |
| `cucumber-junit-platform-engine` | (BOM) | Runs features on the JUnit Platform |
| `org.junit:junit-bom` (import) | 5.14.4 | Pins the versions of the JUnit artifacts below |
| `junit-platform-suite` | (BOM) | `@Suite` runners |
| `junit-jupiter-api` | (BOM) | `Assertions` in step definitions |

| Build plugin | Version | Configuration |
|---|---|---|
| `maven-compiler-plugin` | 3.16.0 | `release 17`, `-Xlint:all -Werror`: any warning fails the build |
| `maven-surefire-plugin` | 3.6.1 | Runs the suites and forwards `-Dkey=value` as system properties (how config overrides work) |
| `maven-javadoc-plugin` | 3.12.0 | `mvn javadoc:test-javadoc`, copied by hand into `hellocucumber/javaDoc/` |

To upgrade, change the three version properties at the top of the pom, then compile
(`mvn -q test-compile`) and do one live run.

## 7. Resources

**Configuration** (`resources/`)

| File | Read by | Contents |
|---|---|---|
| `configuration.properties` | `ConfigFileReader` | `environment=local`, `browser=chrome`, `headless=false`, `windowMaximize=true`, `explicitWait=15`, `url=https://www.imdb.com`, `titleId=tt0111161`, `captchaWaitSeconds=180`, `chromeProfileDir=chrome-profile`. Any key can be overridden with `-D`. |
| `junit-platform.properties` | Maven / `@Suite` runners | `cucumber.filter.tags=not @manual`, `cucumber.publish.quiet=true` |
| `cucumber.properties` | IntelliJ right-click Run | the same two keys |

**Feature files**
- `resources/imdbTests/top_rated_movies.feature` and `title_display_page.feature`
- `resources/hellocucumber/is_it_friday_yet.feature`

**State and outputs**

| Path | What | In git? |
|---|---|---|
| `hellocucumber/chrome-profile/` | Chrome profile holding the `aws-waf-token` CAPTCHA pass | No (`.gitignore`, must stay ignored) |
| `hellocucumber/target/cucumber-reports/imdb.html` | IMDb report, overwritten every run; failure screenshots embedded | No |
| `hellocucumber/target/cucumber-reports/hellocucumber.html` | Tutorial report | No |
| `hellocucumber/javaDoc/` | Published class documentation | Yes |
| `hellocucumber/WIKI.md`, `README.md` files | Scenario tables, status, how to run | Yes |

**Toolchain:** JDK 17+, Maven 3.9+, and Google Chrome (Firefox and Safari also work; `-Dbrowser=`).

## 8. External systems

| System | How the suite uses it |
|---|---|
| IMDb pages | `/chart/top/`, `/search/title/…`, `/title/<titleId>/`, `/registration/signin` |
| Sign-in destinations | amazon.com/ap/signin, imdb.com/ap/signin, accounts.google.com, appleid.apple.com. The suite checks the HTTPS address only; it never types anything. |
| IMDb's AWS WAF | May show a "confirm you are human" CAPTCHA. `BasePage` waits up to `captchaWaitSeconds` for **a person** to solve it. Headless browsers get "403 Forbidden", and `BasePage` stops with an explanation. |

Because the system under test is live and outside our control, data changes (rankings, counts) and
markup changes (hashed `sc-…` classes) are expected. That is why selectors prefer `data-testid`, `id`
or ARIA, and why assertions avoid exact live values.

## 9. Constraints that shape the design

| Constraint | Effect on the architecture |
|---|---|
| Live, React-rendered site | Implicit wait 0; every wait is explicit and goes through `BasePage`; stale elements are retried |
| Ads and trackers slow full page loads | `PageLoadStrategy.EAGER`; page objects wait for the elements they need |
| IMDb's terms forbid robots that evade protection | No stealth flags, UA spoofing or solvers; a person solves CAPTCHAs; the profile keeps the pass |
| Real accounts need credentials and 2-step verification | Account flows are `@manual`; only their signed-out part is automated |
| Be a normal visitor | Security and performance checks are passive (HTTPS, mixed content, navigation timing) |
| One machine, one profile | Serial execution; `EnvironmentType.REMOTE` throws `UnsupportedOperationException` |

## 10. Extension points

- **New page:** add a class extending `BasePage` (implement `path()`, override `waitUntilLoaded()` if
  needed), add a `PageName` label, and add a getter + `getPage` case in `PageObjectManager`.
- **New step:** put it in the step class for that page (or `CommonSteps` if it's shared), take
  `TestContext` in the constructor, and keep selectors in the page object.
- **New config key:** add it to `configuration.properties` and a typed getter to `ConfigFileReader`.
  `-D` overrides come for free.
- **New scenario:** add it to a feature file, then update the READMEs and the WIKI table and status line.
