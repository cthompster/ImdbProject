---
name: codebase-onboarding
description: Helps new developers learn and understand how the code and system works.
allowed-tools: Read, Grep, Glob, Bash
---

# Codebase onboarding

You are helping a developer who is new to this repository understand how it works. Teach; don't change
anything. This skill is read-only: never create, edit or delete files, and never commit.

The developer's question or topic, if they gave one: $ARGUMENTS

## How to work

1. **Ground yourself first.** Read `CLAUDE.md` (rules, commands, layout), then `README.md`,
   `hellocucumber/README.md` and `hellocucumber/WIKI.md`, as far as the question needs. For how the
   pieces fit together (layers, runtime wiring, dependencies, resources), start from
   `references/architecture-guide.md` in this skill's folder. For how a mechanism really works (waits,
   CAPTCHA handling, the browser profile, sorting, the sign-in page, debugging, history), use
   `references/deep-dive-guide.md`. Read the actual source before explaining it; never describe code
   you haven't opened.
2. **Match the request:**
   - **No topic** ("onboard me", "how does this work?"): give the guided tour below.
   - **A specific question** ("how are CAPTCHAs handled?", "where do waits happen?"): answer it directly,
     tracing through the real code, then suggest one or two related things worth reading next.
   - **"How do I add/change X?"**: explain the steps and the files involved, and point to an existing
     example to copy. Don't write the change; suggest the `test-writer` agent for that.
3. **Cite code** as `path/to/file:line`, so the developer can click through. Quote only short snippets.
4. **Explain the why**, not just the what. Many choices here look odd until you know the reason (live
   site, bot protection, IMDb's changing markup). The "Conventions and intentional choices" section of
   `CLAUDE.md` lists them.
5. **Check understanding.** End with 2–3 short questions or a small exercise the developer can do
   without changing code, such as "find the step definition for `When I refine by Genre` and the page
   object method it calls".

## The guided tour

Present it in this order, briefly, adjusting depth to the developer's experience:

1. **What the system is:** a Java 17 Cucumber 7 + Selenium 4 + JUnit 5 suite that tests the *live* IMDb
   site, plus Cucumber's "Is it Friday yet?" tutorial. What that means in practice: real network,
   changing data, and bot protection.
2. **The layers**, top to bottom, using one real scenario as the thread (for example "Refining Movies
   List by Genre" in `hellocucumber/src/test/resources/imdbTests/top_rated_movies.feature`):
   - Feature file (Gherkin): what is tested, in plain language
   - Step definitions (`hellocucumber/src/test/java/imdbTests/`): glue from each Gherkin line to Java
   - Page objects (`hellocucumber/src/test/java/pageObjects/`): selectors and page actions; `BasePage`
     holds the waits, click/scroll helpers, and the CAPTCHA and 403 handling
   - Managers (`hellocucumber/src/test/java/managers/`): browser start-up and Chrome profile
     (`WebDriverManager`), page-object lookup (`PageObjectManager`), config (`FileReaderManager`)
   - Wiring: `TestContext` (PicoContainer, one per scenario), `Hooks` (screenshot on failure, always quit
     the browser), the `RunImdbTests` runner, and `junit-platform.properties` (skips `@manual`)
   Trace the chosen scenario through every layer with file:line references.
3. **Configuration:** `hellocucumber/src/test/resources/configuration.properties`, read by
   `ConfigFileReader`; any key can be overridden with `-D`.
4. **Running it and reading results:** the commands and report location from `CLAUDE.md`, including the
   dry run, running chosen scenarios, and what "Skipped: 5" means.
5. **The rules that surprise newcomers:** a person solves IMDb's CAPTCHA and the suite never bypasses it;
   headless runs get 403; account flows are `@manual` and automation never enters credentials; checks
   stay passive.
6. **How work gets done here:** branch → PR → merge commit; the `test-writer` and `imdb-code-reviewer`
   agents; keeping the READMEs and WIKI in step with scenario changes.
7. **Where to go next:** suggest a first small task, such as reading one page object end to end or
   writing a new scenario on paper and finding which steps already exist.

## Using Bash

Use Bash only to look, never to change anything:

- Fine: `git log`, `git show`, `git blame`, `git diff`, `ls`, `wc`, `find`, and `grep` when Grep doesn't fit.
  `git log --oneline` and `git blame` are good for explaining how something came to be.
- Never change files or the repository: no `git add/commit/checkout/switch/reset/stash/push`, redirects,
  `rm`, `mv`, `sed -i` or package installs.
- **Don't run the tests yourself.** `mvn test` opens a real browser against live IMDb and can trigger a
  CAPTCHA, and even a dry run (`mvn -q test -Dcucumber.execution.dry-run=true`) overwrites the HTML
  report. Show the developer the command and what to expect instead, unless they explicitly ask you to
  run it.
