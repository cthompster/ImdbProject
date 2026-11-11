Feature: Top Rated Movies

  The "Top Rated Movies" page lists the 250 theatrical films with the highest
  ratings on IMDB: https://www.imdb.com/chart/top/

  The checks below avoid exact counts and titles, which change as people vote.

  Scenario Outline: Refining Movies List by Genre

  From the Top Rated Movies page the user can narrow the titles down to one genre.

    Given I load the "Top Rated Movies page"
    When I refine by Genre "<genre>"
    Then the results should be filtered by the "<genre>" genre
    And the results should contain at least 1 title

    Examples:
      | genre  |
      | Comedy |
      | Action |
      | Drama  |

  Scenario Outline: Sorting Movies List by Release Date

  The user can order the list by the year each movie was released, oldest or newest first.

    Given I load the "Top Rated Movies page"
    When I sort the list by "Release date" in <order> order
    Then all 250 movies should be listed in <order> order of release year

    Examples:
      | order      |
      | ascending  |
      | descending |

  Scenario: Movies are numbered in ranking order

  By default the chart is ordered by ranking, and each movie shows its rank ("#1", "#2", ...).

    Given I load the "Top Rated Movies page"
    Then the movies should be numbered from 1 to the total shown, in ranking order

  Scenario: Sorting Movies List by IMDb rating

    Given I load the "Top Rated Movies page"
    When I sort the list by "IMDb rating" in descending order
    Then all movies should be listed in descending order of IMDb rating

  Scenario: The chart is only served over HTTPS

  A visitor who types the plain http:// address is redirected to HTTPS, and the page loads
  no scripts, styles or images over plain HTTP. These are passive checks of what a normal
  visitor's browser receives.

    Given I open the "Top Rated Movies page" through its plain HTTP address
    Then the browser should be redirected to the page's HTTPS address
    And the page should not load any content over plain HTTP

  Scenario: The chart loads within a reasonable time

  Measured in the browser with the Navigation Timing API (time to DOMContentLoaded). The limit
  is generous so that only a large slowdown fails; the timings are printed on every run.

    Given I load the "Top Rated Movies page"
    Then the page should be ready within 10 seconds
