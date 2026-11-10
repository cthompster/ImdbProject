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
