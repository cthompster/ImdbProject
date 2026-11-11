Feature: Title Display Page

  The "Title Display Page" shows one movie's title, summary, ratings, cast and details.
  Users can rate it, add it to their watchlist and share it. The movie comes from
  "titleId" in configuration.properties.

  Scenarios tagged @manual need real IMDb, Amazon, Google or Apple accounts, and
  they hit CAPTCHAs and 2-step verification. They document the expected behavior and
  are run by hand. Automated runs skip them (see junit-platform.properties).

  Scenario: Add Movie to watchlist while signed out

  A signed-out user who adds a movie to the watchlist is asked to sign in first.

    Given I load the "Title Display page"
    And I am not logged in
    When I add the movie to my watchlist
    Then I should be taken to the "Sign In page"


  Scenario Outline: Rate this movie while signed out

  A signed-out user who rates a movie (1 to 10 stars) is asked to sign in first.

    Given I load the "Title Display page"
    And I am not logged in
    When I rate the movie <stars> stars
    Then I should be taken to the "Sign In page"

    Examples:
      | stars |
      | 1     |
      | 10    |


  Scenario Outline: Share this movie on social media

    Given I load the "Title Display page"
    When I open the share menu
    Then the share menu should offer "<option>"
    And the "<option>" option should share this movie via "<link>"

    Examples:
      | option     | link                                 |
      | Facebook   | https://www.facebook.com/sharer.php  |
      | Twitter    | https://twitter.com/intent/tweet     |
      | Email link | mailto:                              |


  Scenario: Copy a link to this movie

    Given I load the "Title Display page"
    When I open the share menu
    And I choose "Copy link" from the share menu
    Then I should see the message "Link copied"


  Scenario: Show the movie's name, release year and rating

  The values change between movies and over time, so the checks are on their form: the name
  matches the browser tab, the year is a plausible release year and the rating is 1 to 10.

    Given I load the "Title Display page"
    Then the page should show the movie's name, release year and IMDb rating


  Scenario: The title page is usable with a screen reader

    Given I load the "Title Display page"
    Then the movie's name should be the only main heading
    And the page should declare its language
    And these controls should have an accessible name:
      | Add to Watchlist |
      | Rate             |
      | Share            |
    And the poster image should have alternative text


  Scenario: The title page is only served over HTTPS

  Passive checks of what a normal visitor's browser receives: opening the plain http:// address
  ends up on HTTPS (Chrome may upgrade the address itself, so this checks the visitor's result
  rather than the server's redirect) and the page loads no scripts, styles or images over plain HTTP.

    Given I open the "Title Display page" through its plain HTTP address
    Then the browser should end up on the page's HTTPS address
    And the page should not load any content over plain HTTP


  Scenario: The title page loads within a reasonable time

  Measured in the browser with the Navigation Timing API (time to DOMContentLoaded). The limit
  is generous so that only a large slowdown fails; the timings are printed on every run.

    Given I load the "Title Display page"
    Then the page should be ready within 10 seconds


  Scenario: The sign-in page leads to creating an account

  The signed-out part of the @manual "Create a new account" scenario below: the sign-in page that a
  signed-out user reaches from the watchlist offers to create an account, which opens Amazon's sign-in
  page over HTTPS. Only the address is checked; nothing is typed and no form is submitted.

    Given I load the "Title Display page"
    And I am not logged in
    When I add the movie to my watchlist
    Then I should be taken to the "Sign In page"
    And the Sign In page should offer "Create an account"
    When I choose "Create an account" on the Sign In page
    Then I should be taken to an HTTPS page on "amazon.com/ap/signin"


  Scenario Outline: The sign-in page leads to the chosen way of signing in

  The signed-out part of the @manual sign-in scenarios below: choosing "Sign in to an existing account"
  reveals the ways to sign in, and each opens its provider's page over HTTPS. Only the address is
  checked, because these pages may refuse automated browsers. Nothing is typed and no form is submitted.

    Given I load the "Title Display page"
    And I am not logged in
    When I add the movie to my watchlist
    Then I should be taken to the "Sign In page"
    When I choose "Sign in to an existing account" on the Sign In page
    Then the Sign In page should offer "<option>"
    When I choose "<option>" on the Sign In page
    Then I should be taken to an HTTPS page on "<destination>"

    Examples:
      | option              | destination          |
      | Sign in with Amazon | amazon.com/ap/signin |
      | Sign in with IMDb   | imdb.com/ap/signin   |
      | Sign in with Google | accounts.google.com  |
      | Sign in with Apple  | appleid.apple.com    |


  @manual
  Scenario: Create a new account

  Create a new account and add the movie to the watchlist. Account creation happens on Amazon's pages.

    Given I load the "Title Display page"
    And I am not logged in
    When I add the movie to my watchlist
    And I am taken to the "Sign In page"
    And I click on the "Create an account" button
    And I am taken to Amazon's sign-in page
    And I create a new account by following the steps on Amazon's pages
    And I complete any CAPTCHA or verification code it asks for
    Then I am returned to IMDb signed in, and the movie is added to "My Watchlist"


  @manual
  Scenario: Login with an existing IMDb account

  Sign in with an existing IMDb account and add the movie to the watchlist.

    Given I load the "Title Display page"
    And I am not logged in
    When I add the movie to my watchlist
    And I am taken to the "Sign In page"
    And I click on the "Sign in to an existing account" button
    And I click on the "Sign in with IMDb" button
    And I enter my "Email"
    And I enter my "Password"
    And I click on the "Sign In" button
    And I complete any CAPTCHA or 2-step verification it asks for
    Then I am successfully logged in and the movie is added to "My Watchlist"


  @manual
  Scenario: Login with Amazon account

  Sign in with an Amazon test account and add the movie to the watchlist.

    Given I load the "Title Display page"
    And I am not logged in
    When I add the movie to my watchlist
    And I am taken to the "Sign In page"
    And I click on the "Sign in to an existing account" button
    And I click on the "Sign in with Amazon" button
    And I enter my "Email or mobile phone number"
    And I click on the "Continue" button
    And I enter my "Password"
    And I click on the "Sign In" button
    And I complete any CAPTCHA or 2-step verification it asks for
    Then I am successfully logged in and the movie is added to "My Watchlist"


  @manual
  Scenario: Login with Google account

  Sign in with a Google test account and add the movie to the watchlist.

    Given I load the "Title Display page"
    And I am not logged in
    When I add the movie to my watchlist
    And I am taken to the "Sign In page"
    And I click on the "Sign in to an existing account" button
    And I click on the "Sign in with Google" button
    And I enter my "Email"
    And I click on the "Next" button
    And I enter my "Password"
    And I click on the "Next" button
    And I complete any 2-step verification Google asks for
    Then I am successfully logged in and the movie is added to "My Watchlist"


  @manual
  Scenario: Login with Apple account

  Sign in with an Apple test account and add the movie to the watchlist.

    Given I load the "Title Display page"
    And I am not logged in
    When I add the movie to my watchlist
    And I am taken to the "Sign In page"
    And I click on the "Sign in to an existing account" button
    And I click on the "Sign in with Apple" button
    And I enter my "Apple Account" email or phone number
    And I enter my "Password"
    And I complete any 2-step verification Apple asks for
    Then I am successfully logged in and the movie is added to "My Watchlist"
