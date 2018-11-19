package hellocucumber;

import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;

import static org.junit.Assert.assertEquals;

class IsItFriday {
    static String isItFriday(String today) {

        switch (today) {
            case "Friday":
                return "TGIF";
            case "Saturday":
                return "It's Saturday Dude!";
            case "Sunday":
                return "Naw.. Sunday, man!";
            case "Monday":
                return "Moody Monday!";
            case "Tuesday":
                return "Tutu Tuesday!";
            case "Wednesday":
                return "Wha wha, Wednesday!";
            case "Thursday":
                return "Thirsty Thursday";
        }
        return "Invalid Day Bro' !";
    }
}

public class Stepdefs {
    private String today;
    private String actualAnswer;

    @Given("^today is \"([^\"]*)\"$")
    public void today_is(String today) {
        this.today = today;
    }

    @When("^I ask whether it's Friday yet$")
    public void i_ask_whether_it_s_Friday_yet() {
        this.actualAnswer = IsItFriday.isItFriday(today);
    }

    @Then("^I should be told \"([^\"]*)\"$")
    public void i_should_be_told(String expectedAnswer) {
        assertEquals(expectedAnswer, actualAnswer);
    }

}