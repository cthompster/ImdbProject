Feature: Title Display Page

  The "Title Display Page" shows one movie's title, summary, ratings, cast and details.
  Users can rate it, add it to their watchlist and share it. The movie comes from
  "titleId" in configuration.properties.

  Scenarios tagged @manual need real IMDB, Amazon, Facebook or Google accounts, and
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

  Passive checks of what a normal visitor's browser receives: the plain http:// address
  redirects to HTTPS and the page loads no scripts, styles or images over plain HTTP.

    Given I open the "Title Display page" through its plain HTTP address
    Then the browser should be redirected to the page's HTTPS address
    And the page should not load any content over plain HTTP


  Scenario: The title page loads within a reasonable time

  Measured in the browser with the Navigation Timing API (time to DOMContentLoaded). The limit
  is generous so that only a large slowdown fails; the timings are printed on every run.

    Given I load the "Title Display page"
    Then the page should be ready within 10 seconds


  @manual
  Scenario: Create a new IMDB account

  Create a new IMDB account and add the movie to the watchlist.

    Given I load the "Title Display page"
    And I am not logged in
    When I add the movie to my watchlist
    And I am taken to the "Sign In page"
    And I click on the "Create an account" button
    And I enter my "Full Name"
    And I enter my "Email"
    And I enter my "Password"
    And I enter my "Password Confirm"
    And I click on the "Continue" button
    Then my new IMDB account is created and the movie is added to "My Watchlist"


  @manual
  Scenario: Login with an existing IMDB account

  Sign in with an existing IMDB account and add the movie to the watchlist.

    Given I load the "Title Display page"
    And I am not logged in
    When I add the movie to my watchlist
    And I am taken to the "Sign In page"
    And I click on the "Sign in with IMDb" button
    And I enter my "Email"
    And I enter my "Password"
    And I click on the "Sign In" button
    Then I am successfully logged in and the movie is added to "My Watchlist"


  @manual
  Scenario: Login with Amazon account

  Sign in with an Amazon test account and add the movie to the watchlist.

    Given I load the "Title Display page"
    And I am not logged in
    When I add the movie to my watchlist
    And I am taken to the "Sign In page"
    And I click on the "Sign in with Amazon" button
    And I enter my "Email"
    And I enter my "Password"
    And I click on the "Sign In" button
    Then I am successfully logged in and the movie is added to "My Watchlist"


  @manual
  Scenario: Login with Facebook account

  Sign in with a Facebook test account and add the movie to the watchlist.

    Given I load the "Title Display page"
    And I am not logged in
    When I add the movie to my watchlist
    And I am taken to the "Sign In page"
    And I click on the "Sign in with Facebook" button
    And I enter my "Email"
    And I enter my "Password"
    And I click on the "Log In" button
    Then I am successfully logged in and the movie is added to "My Watchlist"


  @manual
  Scenario: Login with Google account

  Sign in with a Google test account and add the movie to the watchlist.

    Given I load the "Title Display page"
    And I am not logged in
    When I add the movie to my watchlist
    And I am taken to the "Sign In page"
    And I click on the "Sign in with Google" button
    And I enter my "Email"
    And I click on the "Next" button
    And I enter my "Password"
    And I click on the "Next" button
    And I enter my "2-Step Verification code"
    And I click on the "Next" button
    And I click on the "Create your IMDb account" button
    Then my IMDB account is created and the movie is added to "My Watchlist"
