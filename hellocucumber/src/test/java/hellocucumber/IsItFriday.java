package hellocucumber;

/** The code under test in Cucumber's "Is it Friday yet?" tutorial. */
class IsItFriday {
    static String isItFriday(String today) {
        return switch (today) {
            case "Friday" -> "TGIF";
            case "Saturday" -> "It's Saturday Dude!";
            case "Sunday" -> "Naw.. Sunday, man!";
            case "Monday" -> "Moody Monday!";
            case "Tuesday" -> "Tutu Tuesday!";
            case "Wednesday" -> "Wha wha, Wednesday!";
            case "Thursday" -> "Thirsty Thursday";
            default -> "Invalid Day Bro' !";
        };
    }
}
