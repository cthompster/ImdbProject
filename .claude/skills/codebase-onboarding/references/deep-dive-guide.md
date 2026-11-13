# Deep-dive guide: how the suite really works

`architecture-guide.md` is the map: what exists and what depends on what. This guide covers the mechanisms
that are hard to see from a quick read: how they work, why they were built that way, and how they fail.
`CLAUDE.md` holds the rules.

Code is cited by **class and method name** rather than line number, so the guide stays accurate as
files change. Java paths are under `hellocucumber/src/test/java/`. Each chapter ends with a short
exercise you can do by reading, without running anything.

---

## 1. One scenario, call by call

The thread is "Sorting Movies List by Release Date" in `top_rated_movies.feature`:

```gherkin
Given I load the "Top Rated Movies page"
When I sort the list by "Release date" in <order> order
Then all 250 movies should be listed in <order> order of release year
```

**Start-up**
1. `mvn test` → Surefire → JUnit Platform finds the `@Suite` class `imdbTests.RunImdbTests`. It runs
   the `cucumber` engine with glue `imdbTests` and writes the report to
   `target/cucumber-reports/imdb.html`, plus a numbered copy in `test-history/` through the
   `reporting.RunHistoryReport` plugin. `junit-platform.properties` drops `@manual` scenarios.
2. For this scenario PicoContainer creates **one** `TestContext`, which holds a `WebDriverManager` (no
   browser yet) and a `PageObjectManager`. Every step class and `Hooks` that the scenario touches gets
   that same instance through its constructor.

**`Given I load the "Top Rated Movies page"`**
3. `CommonSteps.iLoadThe` → `PageName.fromLabel("Top Rated Movies page")` → `TOP_RATED_MOVIES`.
4. `PageObjectManager.getPage` → `getTopRatedPage()` → `webDriverManager.getDriver()`. **This is where
   the browser starts:** `createDriver` → `createChromeDriver` → `startSignedOut` → implicit wait 0
   (chapter 4).
5. `BasePage.load()` → `open(url)` → `driver.get("https://www.imdb.com" + "/chart/top/")`. With EAGER
   loading this returns once the HTML has been parsed.
6. `afterNavigation()`: 403 check → CAPTCHA check → consent banner (chapter 3).
7. `TopRatedPage.waitUntilLoaded()` → `visible(LIST_ITEMS)`: the step finishes only when list rows
   exist.

**`When I sort the list by "Release date" in ascending order`**
8. `ImdbStepdefs` is created, and its constructor calls `getTopRatedPage()`. That returns the
   **same** page object step 4 created, because `PageObjectManager` caches it.
9. `SortOrder.fromText("ascending")` → `TopRatedPage.sortBy("Release date", ASCENDING)`:
   - remembers `firstTitle()`;
   - `selectSortField`: finds the option whose text is "Release date", sets the hidden `<select>`
     through JavaScript, then waits for `sort=release_date` in the URL (chapter 5);
   - reads `getSortOrder()` from the order button's `aria-label`; if it's wrong, clicks the button and
     waits for `%2Casc` in the URL;
   - `waitForFirstTitleToChangeFrom(firstTitle)`. A timeout here is swallowed, because the URL
     already proves the sort was applied.

**`Then all 250 movies should be listed in ascending order of release year`**
10. `getTotalTitles()` reads the "250 Titles" label → `getReleaseYears(250)` → `readRows`, which
    waits until 250 year cells exist (chapter 2), then parses each with the `YEAR` pattern.
11. The step sorts a copy of the list and compares it with `assertEquals`. On failure, JUnit's message
    shows both lists.

**Tear-down**
12. `Hooks.tearDown`: if the scenario failed, it attaches a PNG and logs "URL at failure"; then
    `quitDriver()` in a `finally` block, so the browser always closes.

> **Exercise:** the "Movies are numbered in ranking order" scenario has no `When` step. Which page
> object methods run for it, and which wait in `readRows` protects it?

---

## 2. Waiting on a live React site

**The setup.** `WebDriverManager.createLocalDriver` sets implicit wait to **0**. Mixing implicit and
explicit waits makes timeouts add up unpredictably. Every `BasePage` gets one `WebDriverWait` with
`explicitWait` (15 s) that **ignores `StaleElementReferenceException`**. React redraws lists, for
example after a re-sort, so an element read mid-redraw goes stale; the wait simply polls again.
EAGER loading means `driver.get` doesn't wait for ads, so page objects must wait for the elements
they need.

**The toolkit** (all in `BasePage`):

| Method | What it does | Watch out for |
|---|---|---|
| `waitFor(condition)` | `wait.until`; on timeout, **if a CAPTCHA is showing**, waits for a person and retries **once** | The one place to wait. Everything else builds on it |
| `visible(locator)` | waits until displayed, returns the element | |
| `isPresent(locator)` | `visible` → true/false | a "false" always costs the **full 15 s** |
| `click(locator)` | `visible` → `scrollToCenter` → `elementToBeClickable` → click | |
| `scrollToCenter(el)` | instant `scrollIntoView`, then waits until the element's centre is inside the window | IMDb turns on smooth scrolling, so a normal scroll returns mid-motion and the click misses |
| `waitUntilLoaded()` | hook called by `open()`; pages override it | `TopRatedPage` → list rows, `TitlePage` → hero title |

**The pattern: wait for the effect, never for time.** There is no `Thread.sleep` anywhere. After an
action, the code waits for evidence that it worked:

- **URL changes:** `refineByGenre` waits for `/search/title/`, and `selectSortField` waits for `sort=…`.
- **Content changes:** `waitForFirstTitleToChangeFrom`. The URL changing doesn't mean the list has
  been redrawn, so reading it straight after the URL wait could return the *old* order.
- **Enough content:** `readRows` waits for `>= expectedRows` elements, because IMDb redraws the list
  in batches.
- **Any of several outcomes:** `SignInPage.choose` (chapter 6).

**Deliberate swallowed timeouts.** Two places catch `TimeoutException` on purpose:
- `readRows` carries on, so the **assertion** reports "expected 250, found 187". That says far more
  than "timed out waiting for condition".
- `sortBy` carries on, because some sorts keep the same movie first (rating, highest first, from the
  default ranking). That case costs the full 15 s, as the Javadoc on `sortBy` says.

**Plain `wait.until` without CAPTCHA handling** is used in two places:
- `BasePage.isCurrent()` only checks the URL. `SignInPage.isCurrent` adds `afterNavigation()`
  itself.
- `SignInPage.waitForSecureDestination` for sites other than IMDb, where IMDb's CAPTCHA detection
  doesn't apply.

> **Exercise:** `GenreSearchResultsPage.isFilteredByGenre` uses `isPresent`. In a failing run, how
> long does that step take before it reports false, and why?

---

## 3. Bot protection, end to end

IMDb sits behind AWS WAF. It can show a "confirm you are human" CAPTCHA or refuse with "403
Forbidden". The suite's stance is **detect, explain, and let a person act**. It never evades the
protection: no stealth flags, no user-agent tricks, no solvers. IMDb's terms forbid robots that do.

**`BasePage.afterNavigation()`** runs after every IMDb page load and does three things in order:
1. **`failIfAccessDenied`:** if the tab title contains "403 Forbidden", throws at once. The message
   depends on whether the run is headless. Without this check, the run would time out 15 s later on
   some unrelated element, with a confusing error.
2. **`waitForHumanVerificationIfShown`:** if the title contains "confirm you are human" (compared in
   lower case with `Locale.ROOT`):
   - records `captchaShownDuringLoad = true`;
   - when headless, throws, because nobody can solve it;
   - otherwise prints `>>> IMDB is asking you to confirm you are human…` and waits up to
     `captchaWaitSeconds` (180 s) with a **separate** `WebDriverWait` until the title changes.
3. **Consent banner:** clicks "Accept" if a cookie banner is visible. This is best effort, because
   none appears in the US.

**A CAPTCHA that appears late**, in the middle of a step, is caught by `waitFor`: the wait times out,
`isCaptchaShown()` is true, so it waits for the person and retries the condition once.

**Timing data during a CAPTCHA.** `CommonSteps.thePageShouldBeReadyWithin` checks
`wasCaptchaShownDuringLoad()` and skips the timing assertion. The browser's timing data would then
describe the challenge page, and a person's solving time isn't page speed.

**Keeping the pass.** A solved CAPTCHA sets the `aws-waf-token` cookie. The Chrome profile in
`chrome-profile/` keeps it between scenarios and between runs, and `startSignedOut` deliberately
keeps that one cookie (chapter 4).

**When it goes wrong:**
- **CAPTCHA on every scenario:** IMDb is throttling this machine. Wait a few hours rather than
  retrying, because retries make it worse.
- **403 headless:** expected. As of November 2026 IMDb refuses headless browsers on every page the
  suite uses.

> **Exercise:** trace what happens if a CAPTCHA appears while `TitlePage.rate` is waiting for the
> rating prompt. Which method notices, and how many times is the condition tried?

---

## 4. The browser and the Chrome profile

**Lazy start.** `WebDriverManager.getDriver()` creates the browser the first time any page object is
requested. `Hooks` checks `hasDriver()` so it never starts a browser just to take a screenshot.

**Options** (`chromeOptions`):
- `PageLoadStrategy.EAGER`
- `--disable-notifications`, `--lang=en-US` (so text matches the feature files),
  `--window-size=1400,1000`
- `--user-data-dir=<chromeProfileDir>`
- `--headless=new` when headless

Firefox gets EAGER loading and `en-US` too. Safari has no headless mode, so `isHeadless()` is always
false for it.

**Starting signed out without losing the pass.** The profile keeps *all* cookies, including any
session from a manual sign-in. `startSignedOut` uses Chrome DevTools commands:
1. `Storage.getCookies`
2. keep only `aws-waf-token`
3. `Storage.clearCookies`
4. `Storage.setCookies` for the kept cookie

`toCookieParam` converts the cookie's format between the two commands, and drops `expires` for
session cookies. The result: every scenario starts signed out and doesn't depend on earlier ones, yet
the CAPTCHA pass survives.

**Never leak a browser.** One profile can be used by only one Chrome at a time:
- If setup fails after the browser started (maximise, timeouts, cookie reset), `quitQuietly` closes
  it. It isn't stored in `driver` yet, so the `@After` hook couldn't close it.
- `Hooks.tearDown` quits in `finally`.
- If Chrome still finds the profile in use, `createChromeDriver` checks for the `SingletonLock` file
  and throws a message saying to close the leftover Chrome or delete the lock.

That is also why scenarios run **one at a time**.

**A throwaway profile:** pass an empty `-DchromeProfileDir=`. `getChromeProfileDir()` then returns
null, Chrome uses a temporary profile, and the cookie reset is skipped. You will usually get a
CAPTCHA.

> **Exercise:** suppose `startSignedOut` throws. Follow the code: who quits the browser, and what
> would happen to the next scenario if nobody did?

---

## 5. Coping with IMDb's markup

**Choosing selectors.** In order of preference: `data-testid` (most of `TitlePage`, the chart total),
`id` (`sort-by-selector`, `swap-sort-order-button`), ARIA (`aria-label='Rate 7'`), then stable
`ipc-…` component classes. The hashed `sc-…` classes are avoided because they change on every IMDb
deploy.

**Scope your selectors.** `TopRatedPage.ROW_TITLES` is limited to
`ul.ipc-metadata-list > li…` because the page heading uses the same `.ipc-title__text` class. An
unscoped selector would read the heading as "row 1".

**The sort dropdown.** IMDb draws its own dropdown over a transparent native `<select>`. Selenium's
`Select` refuses to use an invisible element, so `selectSortField`:
1. reads the options with `Select.getOptions()`, which works fine on a hidden element;
2. finds the option `value` whose text matches;
3. runs `SELECT_OPTION_JS`, which calls the **native** `HTMLSelectElement` value setter and then
   dispatches a bubbling `change` event.

A plain `select.value = x` would change the DOM, but React keeps track of the value itself and would
ignore it, so the list would never re-sort.

**Rating stars.** The star buttons sit under a transparent touch layer, and `WebElement.click()`
refuses an element that is covered. `TitlePage.rate` uses `new Actions(driver).moveToElement(star).click()`,
which acts like a real mouse.

**Reading values safely:**
- `getDomProperty("textContent")` instead of `getText()` for row data. `getText()` returns only text
  Selenium considers visible, while `textContent` is the raw text, whatever the layout.
- Regex patterns with clear errors: `YEAR`, `RANK` (`#1`), `LEADING_RATING` (`9.3` from
  `9.3 (3.3M)`), and `parseCount` for "1-50 of 2,640".
- `TitlePage.TAB_TITLE_YEAR` handles both "Movie (1994)" and "Fargo (TV Series 2014– )".
- `xpathLiteral` quotes text containing `'` correctly, for example a genre such as "Children's".
- Case conversion always uses `Locale.ROOT`. In a Turkish locale, `"TITLE".toLowerCase()` produces a
  dotless ı.

**Live data.** Assertions check *shape*, not *values*: "at least 1 title", "sorted", "ratings within
1–10". The only fixed values are the configured movie (`titleId`) and the chart's own "250".

> **Exercise:** why does `selectSortField` return `false` when the option is already selected, and
> what would `sortBy` wait for if it didn't?

---

## 6. The sign-in page, the trickiest page

**What the page does** (checked live on 2026-11-11):
- `/registration/signin` first offers **"Create an account"** and **"Sign in to an existing
  account"**.
- The second one doesn't navigate anywhere. It **reveals more options on the same page**: "Sign in
  with Amazon / IMDb / Google / Apple".
- Each of those leads to amazon.com/ap/signin, imdb.com/ap/signin, accounts.google.com or
  appleid.apple.com.

**Matching options.** `SignInPage.option(text)` builds a case-insensitive XPath on links and buttons,
using `translate()` because XPath 1.0 has no `lower-case()`. `ALL_OPTIONS` is deliberately
**broader**. It is used only to list what the page *does* show in a failure message.
`getOptions()` returns an empty list instead of throwing if the page changes mid-read, so a stale
element can't hide the real failure.

**`choose(option)`: how do you know a click worked?** One click can have three different effects, so
`choose` waits for **any** of them:
1. a new window opened, in which case it switches to that window;
2. the URL no longer contains `/registration/signin`;
3. the option is gone from the page (`isGone`), meaning it revealed new options in place.

`isGone` re-runs the **locator** on every poll rather than holding on to the clicked element. If
React re-rendered the button just before the click, the old element would look "gone" even though
nothing happened. A stale element during the check counts as "not gone yet".

**`waitForSecureDestination(host, pathPrefix)`: checking a URL that a lookalike can't fool.**
- The scheme must be `https`.
- **Host:** `isHostOrSubdomain` accepts `amazon.com` or `*.amazon.com`, but **not** `evilamazon.com`.
  That is why it checks `endsWith("." + host)` rather than `endsWith(host)`.
- **Path:** `isUnderPath` accepts `/ap/signin` and `/ap/signin/…`, but not `/ap/signinXYZ`.
- **Parsing:** `java.net.URL`, not `URI`. Some third-party redirects contain characters such as `|`
  or `{` that `URI` rejects.
- **IMDb vs others:** for imdb.com it uses `waitFor`, then `afterNavigation()` *after* the try. That
  way an unsolved CAPTCHA fails with its own message, not as "wrong address". Amazon, Google and Apple
  get a plain `wait.until`.

**Why only the address?** Amazon may refuse automated browsers, so the page content isn't reliable.
More importantly, **the automation never types or submits anything**. Real sign-in and account
creation are `@manual` scenarios, because they need real credentials and 2-step verification, and
automating them would mean handling credentials.

> **Exercise:** list three URLs that should fail `waitForSecureDestination("amazon.com", "/ap/signin")`
> and say which check rejects each one.

---

## 7. Passive security and performance checks

These checks look only at what a normal visitor's browser receives. There is no load testing and no
probing.

- **Ending on HTTPS:** `BasePage.loadOverPlainHttp` opens `http://www.imdb.com/…`. The step then
  checks that the browser is on the right page **and** on an `https://` address. Chrome may upgrade
  to HTTPS by itself (HTTPS-Upgrades or HSTS) without asking the server, so this checks *the visitor's
  result*, not the server's redirect. The Javadoc says so.
- **Mixed content:** `getInsecureResources` runs one script that collects `http:` URLs from:
  - the Resource Timing list;
  - `src`/`href` on images, scripts, iframes, media, stylesheets and preloads;
  - every candidate in `srcset`.
  Ordinary `<a href>` links don't count. **Limits:** the timing buffer keeps the first 250 entries,
  and images that load lazily after the check are missed.
- **Load time:** `getNavigationTimings` waits until `domContentLoadedEventEnd` is set, then returns
  `responseStart` (time to first byte) and `domContentLoaded`. The step prints them on every run, so
  you can compare runs, and fails only above the threshold in the feature file (10 s). It is skipped
  when a CAPTCHA appeared during the load (chapter 3).

> **Exercise:** why is a single-script check for mixed content better than looping over
> `findElements` for every `<img>`? Think about how many WebDriver round trips each one makes.

---

## 8. Configuration and Cucumber gotchas

**Config precedence** (`ConfigFileReader.get`): a non-blank **`-D` system property** wins, otherwise
the value in `configuration.properties`; blank counts as unset. Surefire passes `-D` from the `mvn`
command line through to the tests. `FileReaderManager` reads the file **once**, on first use.

**Typed getters fail loudly:**
- `explicitWait=abc` → "must be a whole number"
- `browser=edge` → "Use chrome, firefox or safari"
- `environment=remote` → `UnsupportedOperationException` (not implemented)

**Two properties files.** `junit-platform.properties` (Maven and the `@Suite` runners) and
`cucumber.properties` (IntelliJ's right-click Run) both say `cucumber.filter.tags=not @manual`.
Change one, change both.

**The name filter matches the whole name.** `-Dcucumber.filter.name="The sign-in page leads.*"`
works, but `"The sign-in page leads"` silently runs **nothing**: every scenario is reported as
skipped, and the build still passes.

**Dry run:** `-Dcucumber.execution.dry-run=true` opens no browser and checks that every step has a
definition. It still **overwrites** `imdb.html`, as every run does, so do a full run before sharing
the report. Earlier runs aren't lost: each is kept in `test-history/`, and dry and filtered runs are
labelled `_dry-run` and `_filtered` there.

**Unknown names fail with a list.** `PageName.fromLabel`, `SortOrder.fromText`,
`SignInPage.optionLocator` and `TitlePage.getAccessibleName` all throw with the valid choices, so a
typo in a feature file explains itself.

> **Exercise:** you run `mvn test -Dtest=RunImdbTests "-Dcucumber.filter.name=Sorting"` and every
> scenario is skipped. Why, and what should the value be?

---

## 9. Debugging a failure

Open `target/cucumber-reports/imdb.html`. A failed scenario has an embedded **"Screenshot at
failure"** and a **"URL at failure"** log line. Look at those before reading the stack trace.
To tell a one-off from a pattern, open `test-history/index.html`: it lists earlier runs with their
pass/fail counts, and each run's full report, screenshots included, is one click away.

| You see | It means | Do |
|---|---|---|
| "IMDB refused the request (403 Forbidden)" | WAF blocked the browser; always happens when headless | Run with a visible browser; if already visible, wait and retry later |
| "the CAPTCHA was not solved within 180 seconds" | Nobody solved it in time | Rerun and watch the Chrome window, or raise `-DcaptchaWaitSeconds` |
| A CAPTCHA in **every** scenario | IMDb is throttling this machine | Wait a few hours; don't loop reruns |
| "Chrome could not start: the profile … is in use" | A leftover Chrome, or a stale `SingletonLock` | Close that Chrome, or delete the lock file named in the message |
| `TimeoutException` on a selector; the screenshot shows the page looking normal | IMDb changed its markup | Inspect the live page and update the locator in the page object (only there) |
| "expected 250 … but was 187" | The list hadn't finished redrawing, or IMDb changed paging | Check the screenshot; look at `readRows` and the count label |
| "Unknown page / option / control …" | A typo in a feature file | Use one of the names listed in the message |
| Every scenario skipped when using a name filter | The regex didn't match a whole name | Add `.*` |
| A compile error from a warning | `-Xlint:all -Werror` | Fix the warning; don't suppress it without a reason |

To reproduce one scenario, see the commands in `CLAUDE.md`. Run it live only once you've formed a
theory, because every live run can trigger a CAPTCHA.

---

## 10. How the code got here

`git log --oneline` tells the story; there are about 40 commits.

- **2018-11 to 2019-01:** the original assignment. Hard-coded counts, an older Selenium, and IDE
  files committed.
- **2026-11-09, PR #1:** modernised to Java 17, Selenium 4, Cucumber 7 and JUnit 5, and rewritten for
  today's IMDb site, including the wait for a person to solve the CAPTCHA. A follow-up commit
  ("handle IMDB access blocks") added the 403 fail-fast, the signed-out cookie reset, the clear
  message when the profile is locked, and EAGER loading.
- **PRs #2–#4:** stopped tracking per-user IDE files, then fixes from code review and readability
  clean-ups.
- **PRs #5–#10:** the human docs (WIKI, root README) and the project's agents (`imdb-code-reviewer`,
  `test-writer`).
- **PR #11:** eight new tests: ranking order, rating sort, title content, accessibility, HTTPS and
  mixed content, and load time. The review that followed made the rating sort go lowest first,
  because only that order proves the list re-sorted.
- **PR #12:** automated the signed-out part of the five `@manual` sign-in scenarios. A lesson in
  checking the live page: the first version assumed the provider options had been **removed**. A live
  run showed that "Sign in to an existing account" reveals them **in place**. That is why `choose()`
  waits for any of three outcomes instead of just a URL change.
- **PRs #13–#16:** tooling and docs: the reviewer's report format, `CLAUDE.md`, this onboarding skill
  and its `architecture-guide.md`.

**Choices whose reasons are recorded in the code comments:**
- ignoring stale elements: React redraws (`BasePage` constructor)
- instant scroll plus a centre check: smooth scrolling (`scrollToCenter`)
- the native `<select>` setter: React ignores a plain value change (`SELECT_OPTION_JS`)
- `Actions` for stars: a touch layer covers them (`rate`)
- `URL` instead of `URI`: unencoded third-party redirects (`isSecureDestination`)
- checking the locator in `isGone`: a re-render just before the click (`choose`)

Read `git log -p` or `git blame` on any of these to see what changed when.

> **Exercise:** run `git log --oneline -- hellocucumber/src/test/java/pageObjects/SignInPage.java`
> and `git show` the most recent commit. Which of the mechanisms in chapter 6 did it add?
