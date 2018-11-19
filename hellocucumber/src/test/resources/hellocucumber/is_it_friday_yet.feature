Feature: Is it Friday yet?
  Everybody wants to know when it's Friday

  Scenario Outline: Today is or is not Friday
    Given today is "<day>"
    When I ask whether it's Friday yet
    Then I should be told "<answer>"


    Examples:
    | day             | answer              |
    | Friday          | TGIF                |
    | Saturday        | It's Saturday Dude! |
    | Sunday          | Naw.. Sunday, man!  |
    | Monday          | Moody Monday!       |
    | Tuesday         | Tutu Tuesday!       |
    | Wednesday       | Wha wha, Wednesday! |
    | Thursday        | Thirsty Thursday    |
    | Wiggleday       | Invalid Day Bro' !  |