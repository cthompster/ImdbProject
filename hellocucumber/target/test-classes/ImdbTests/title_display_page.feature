Feature: Title Display Page

  The "Title Display Page" displays the title, a summary, and a list of the details and attributes
  about one movie. The user can rate the movie, add to watchlist, or rent it via Amazon Prime Video.

  Scenario: Add Movie to watchlist

  On the Title display page the main function is to add the movie to the users watchlist and provide
  information about the movie details and summary (i.e., rating, release date, writers, directors, cast,
  genres, and length of movie in hours and minutes. The user can also share on social media and read previews.

    Given I load the "Title Display page"
    And I am not logged in
    And I add the movie to "My Watchlist"
    Then I should be taken to the "Sign In page"


  Scenario: Create a new IMDB account

  Create a new IMDB account and add movie to Watchlist

    Given I load the "Title Display page"
    And I am not logged in
    And I add a movie to my watchlist
    And I am taken to the "Sign In" page
    And I load the new "IMDB Account" page
    And I Click on the "<createButton>"
    And I enter "Full Name"
    And I enter "Email"
    And I enter "Password"
    And I enter "Password Confirm"
    And I click on the "Continue Button"
    Then my new IMDB account is created and "Movie" is added to "My Watchlist"


  Scenario: Login with a Existing account

  Complete Login with an existing IMDB account and add movie to Watchlist

    Given I load the "Title Display page"
    And I am not logged in
    And I add a movie to my watchlist
    And I am taken to the "Sign In" page
    And I click on the "Sign In with IMDB" button
    And I enter my "Email"
    And I enter my "Password"
    And Click on the "Sign In" button
    Then I am successfully logged in, movie is added to "My Watchlist"


  Scenario: Login with Amazon account

  Complete the Login using an Amazon Test account and add movie to Watchlist

    Given I load the "Title Display page"
    And I am not logged in
    And I add a movie to my watchlist
    And I am taken to the "Sign In" page
    And I click on the "Sign In with Amazon account" button
    And I enter my "Email"
    And I enter my "Password"
    And Click on the "Sign In" button
    Then I am successfully logged in, movie is added to "My Watchlist"


  Scenario: Login with Facebook account

  Complete the Login using a Facebook test account and add movie to Watchlist

    Given I load the "Title Display page"
    And I am not logged in
    And I add a movie to my watchlist
    And I am taken to the "Sign In" page
    And I click on the "Sign In with Facebook account" button
    And I enter my "Email"
    And I enter my "Password"
    And Click on the "Sign In" button
    Then I am successfully logged in, movie is added to "My Watchlist"


  Scenario: Login with Google Account

  Complete the IMDB login using a Google Test account and add movie to Watchlist

    Given I load the "Title Display page"
    And I am not logged in
    And I add a movie to my watchlist
    And I am taken to the "Sign In" page
    And I click on the "Sign In with Google account" button
    And I enter my "Email"
    And I click the "Next" button
    And I enter my "Password"
    And I clcik the "Next" button
    And I enter the "2-Step Verification code"
    And Click on the "Next" button
    And click on the "Create Your IMDB account" button
    Then IMDB account is created and movie is added to "My Watchlist"


  Scenario Outline: Rate this movie

  Rate movie as 1 star, Rate movie as 5 stars

    Given I load the "<page>"
    And I click on the "<link>"
    And I click on "<rating>"
    Then the movie should show a new "<rating>" rating

    Examples:
    | page          | link      | rating |
    | title display | Rate This | 1 star |
    | title display | Rate This | 5 star |


  Scenario Outline: Share this Movie on Social Media
    Facebook, Twitter, Email, IMDB Check In, Copy Link

    Given I load the "<page>"
    And I click on the "<link>" link
    And I click on "<socialMediaLink>" pop-up
    Then a login pop-up window is displayed to login to "<socialMedia>" and share a link to the movie

  Examples:
  | page          | link  | socialMediaLink | socialMedia    |
  | title display | SHARE | Facebook link   | Facebook Login |
  | title display | SHARE | Twitter link    | Twitter Login  |
  | title display | SHARE | Email link      | Email          |
  | title display | SHARE | IMDB Check In   | IMDB Login     |
  | title display | SHARE | Copy Link       |                |