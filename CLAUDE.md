# CLAUDE.md

Guidance for Claude Code in this repository. Human-facing docs: `README.md`, `hellocucumber/README.md`
and `hellocucumber/WIKI.md` (the WIKI holds the scenario tables and the current pass/fail status).

## What this is

A Java 17 + Maven BDD suite in `hellocucumber/` that tests the **live IMDb site** with Cucumber 7,
Selenium 4 and JUnit 5. It also contains Cucumber's "Is it Friday yet?" tutorial (8 scenarios).

## Running the tests

All Maven commands run from `hellocucumber/`:

| Command | What it does |
|---|---|
| `mvn test` | Full suite. Opens a visible Chrome against live IMDb; about 2 minutes. Run it in the background. |
| `mvn test -Dtest=RunImdbTests "-Dcucumber.filter.name=<regex>"` | Only some scenarios. The regex must match the **whole** scenario name: `"The sign-in page leads.*"` works, a bare prefix silently skips everything. |
| `mvn -q test-compile` | Compile only (`-Xlint:all -Werror`, so warnings fail the build). |
| `mvn -q test -Dcucumber.execution.dry-run=true` | No browser: checks every step is defined. |

- Report: `hellocucumber/target/cucumber-reports/imdb.html`. Every run, including dry and filtered
  runs, **overwrites** it; do a full run to get a complete report. Failure screenshots are embedded in
  it as base64 PNGs.
- Expected result today: 25 IMDb scenarios pass, 5 `@manual` skipped, 8 tutorial scenarios pass
  (Maven's final line: `Tests run: 38, Failures: 0, Errors: 0, Skipped: 5`).
- Any key in `src/test/resources/configuration.properties` can be overridden with `-D`
  (e.g. `-Dbrowser=firefox`, `-DtitleId=tt0068646`).
- Don't run the live suite repeatedly: each run can trigger CAPTCHAs (see below). Prefer compile and
  dry runs while iterating, then one live run.

## IMDb bot protection: hard rules

- IMDb's AWS WAF may show a "confirm you are human" CAPTCHA. The suite waits (up to 180 s) for **a
  person** to solve it in the browser window. When starting a live run, tell the user to watch for it.
  The pass is kept in `hellocucumber/chrome-profile/`, which must stay git-ignored.
- **Never** add code that bypasses or evades bot protection: no stealth flags, user-agent spoofing,
  CAPTCHA solvers or similar. IMDb's terms prohibit robots.
- Headless runs get "403 Forbidden" on every page and fail fast by design.
- If every scenario hits a CAPTCHA, IMDb is throttling: suggest waiting a few hours instead of retrying.
- **Never** enter credentials, create accounts or submit sign-in/registration forms in automation.
  Account flows are `@manual` scenarios; only their signed-out part is automated (follow a sign-in
  option and check the HTTPS address it reaches).
- Security and performance checks stay passive (what a normal visitor's browser receives). No load
  or attack testing against IMDb.

## Layout (`hellocucumber/src/test/`)

- `resources/imdbTests/*.feature`: scenarios. `@manual` ones are skipped via `junit-platform.properties`
  (Maven, `RunImdbTests`) and `cucumber.properties` (IntelliJ right-click Run); keep the two in step.
- `java/imdbTests/`: step definitions (`CommonSteps`, `ImdbStepdefs`, `TitleDisplayStepDefs`,
  `SignInStepDefs`), `Hooks` (screenshot on failure, always quit the browser), `TestContext`
  (PicoContainer-injected per scenario), `RunImdbTests`.
- `java/pageObjects/`: `BasePage` (explicit waits, CAPTCHA and 403 handling, click/scroll helpers) and
  one class per page. Selectors live only here.
- `java/managers/`: `WebDriverManager` (browser start, Chrome profile, cookie reset),
  `PageObjectManager`, `FileReaderManager`.
- `java/enums/PageName`: the page names used in feature files (`"Title Display page"`, ...). A new page
  needs an entry here and in `PageObjectManager`.
- `java/hellocucumber/`: the tutorial.

## Conventions and intentional choices

- Implicit wait is 0. Wait explicitly with `BasePage.waitFor` / `visible` / `click`, which also handle a
  CAPTCHA that appears late. After anything that loads a new IMDb page, call `afterNavigation()`.
- Pages off IMDb (Amazon, Google, Apple) use a plain `wait.until`; IMDb's CAPTCHA handling doesn't apply.
- Prefer `data-testid`, `id` or ARIA selectors over IMDb's hashed `sc-…` classes. Don't assert on live
  data that changes (exact counts, titles other than the configured movie).
- `PageLoadStrategy.EAGER`; IMDb's hidden `<select>` is set through JavaScript; rating stars are
  clicked with `Actions`. These are deliberate, not bugs.
- Use `Locale.ROOT` for case conversion.
- When a scenario changes, update the READMEs and the WIKI (scenario table and status line) to match.

## Agents

Project subagents in `.claude/agents/`:

- `test-writer`: designs and writes scenarios, step definitions and page objects.
- `imdb-code-reviewer`: read-only reviewer that knows the choices above. Prefer it over a general
  code reviewer here. Its report ends with an approval status. After editing any file in
  `.claude/agents/`, check that the agent still loads: settings only between the `---` lines.

Project skills in `.claude/skills/`:

- `codebase-onboarding` (`/codebase-onboarding [topic]`): read-only guided tour or Q&A for developers
  new to the code. It traces real scenarios through the layers and doesn't run the tests. Its
  `references/architecture-guide.md` maps the components, runtime wiring, dependencies and resources, and
  `references/deep-dive-guide.md` explains the key mechanisms and how to debug them; update them when
  those change.

## Hooks

Shared hooks in `.claude/settings.json` (scripts in `.claude/hooks/`) enforce the rules above. When a hook
blocks something, it says why; fix the cause rather than working around the hook.

| Hook | When | What it does |
|---|---|---|
| `git-guard.sh` | Before Bash | **Any branch:** blocks pushes to master (`HEAD:master`, `x:master`, `push origin master`) and staging `chrome-profile` (force-adding it, or force-adding broadly such as `add -f .`, `-A` or `hellocucumber`). Also blocks a commit while any of it is staged, or while uncommitted changes to the suite (working tree, staged copy or new files) add bot-protection evasion. **On `master`:** blocks `commit`, `revert`, `cherry-pick`, `am`, `rebase`, `merge`, `pull`, `push`, `reset --hard` and `clean -f`. Still allowed there: `merge`/`pull --ff-only`, `push --delete <branch>`/`push origin :<branch>`, and pure `&&` chains (no `;`, `||`, pipe, newline or background `&`) that start by creating a branch and don't switch back (`git checkout -b x && git commit …`) |
| `bot-protection-guard.sh` | Before Edit/Write | Blocks edits to the suite's `.java`, `pom.xml`, `.properties`, `.js`, `.json`, `.sh`, `.py` and `.mvn/` files (not the generated `javaDoc/` or `target/`) that add bot-protection evasion: stealth flags, user-agent spoofing (`user-agent=`, `useragent.override`), webdriver hiding (`navigator…webdriver`, `dom.webdriver`) or CAPTCHA solvers. The patterns live in `evasion-patterns.txt` and the file list in `lib-evasion.sh`, both shared with the commit and end-of-turn checks. Comments are ignored using each file type's own syntax (`//` and `/* */` in Java, JS and JSON, with Java text blocks and JS template literals treated as strings; `#` in Python and shell; `<!-- -->` in `pom.xml`; `#`/`!` lines in `.properties`), with string literals respected, so code on the same line as a comment is still checked |
| `check-frontmatter.sh` | After Edit/Write | Checks the frontmatter of `.claude/agents/*.md` and `.claude/skills/*/SKILL.md`: only known settings, valid YAML values, `name` and `description` present |
| `stop-checks.sh` | When Claude stops | First flags bot-protection evasion in uncommitted changes to the suite (working tree, staged copy or new files), however they were made; if it finds any, it reports that at once and skips the rest. Otherwise, if uncommitted changes touch Java or `pom.xml`, runs `mvn -q test-compile` (never `mvn test`). Reminds when a `.feature` file changed without the READMEs or WIKI, or when only one of the two properties files changed. The compile result and the reminders are reported once (a failing compile isn't re-run or re-reported until the code changes again; a compile cut off by the 3-minute hook timeout is retried); evasion is reported on every turn until it's removed |

**Limits:**
- `git-guard.sh` checks the whole command text, so a heredoc that only *mentions* a blocked command is blocked too. Put such text in a file (for example `gh pr create --body-file`).
- Evasion added through Bash or an IDE isn't stopped when it's written, only when Claude commits or ends its turn. A live `mvn test` started in the same turn as such an edit would run before either check.
- Block comments (`/* */`, `<!-- -->`) are followed across lines the way the compiler reads them, so a code line that starts with `* ` is still checked. An Edit that changes only the middle of an existing comment block (a Javadoc line without its `/**`) is checked as code, so mentioning a banned term there is blocked. Reword it, or include the whole comment block in the edit. A JavaScript regex literal containing `/*` (such as `/[/*]/`) is read as opening a comment and can hide the lines after it.
- The shared hooks need `jq` and `evasion-patterns.txt`. Without either they show a "hook is disabled" (or "wasn't checked") warning and allow the action. The personal `notify.sh` just does nothing without `jq`.

**Personal hooks** live in `.claude/settings.local.json` (git-ignored). The scripts they run are committed, except `env.local.sh`:
- **SessionStart:** appends `.claude/hooks/env.local.sh` (git-ignored: `JAVA_HOME` and the Maven/gh `PATH`) to `$CLAUDE_ENV_FILE`, so `mvn` and `gh` work without exports. `stop-checks.sh` also loads that file.
- **Notification:** runs the committed `notify.sh`, which shows a macOS banner when Claude is waiting.

**To set these up on another machine:**
1. Create `.claude/hooks/env.local.sh` with your own `export JAVA_HOME=…` and `export PATH=…:$PATH` lines.
2. Add the hooks to `.claude/settings.local.json`:
   ```json
   {
     "hooks": {
       "SessionStart": [{ "hooks": [{ "type": "command", "command": "f=\"$CLAUDE_PROJECT_DIR/.claude/hooks/env.local.sh\"; [ -f \"$f\" ] && [ -n \"$CLAUDE_ENV_FILE\" ] && cat \"$f\" >> \"$CLAUDE_ENV_FILE\"; exit 0" }] }],
       "Notification": [{ "hooks": [{ "type": "command", "command": "\"$CLAUDE_PROJECT_DIR\"/.claude/hooks/notify.sh" }] }]
     }
   }
   ```

Review or disable hooks with `/hooks`.

## Git workflow

The user works one change at a time and asks for each step:
new branch from `master` → commit → push and open a PR (`gh pr create`) → merge with a merge commit
(`gh pr merge --merge`) → switch to `master` and delete the branch locally and on GitHub.

- Never commit to `master` directly, and don't commit, push, merge or delete until asked. After each
  step, offer the next one.
- PR descriptions: a short summary or table of the changes, plus the test result.
- `.idea/workspace.xml` and `.idea/libraries/` are untracked on purpose.
