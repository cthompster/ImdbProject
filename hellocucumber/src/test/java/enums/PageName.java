package enums;

import java.util.Arrays;
import java.util.stream.Collectors;

/** Page names as written in the feature files, e.g. {@code Given I load the "Top Rated Movies page"}. */
public enum PageName {
    TOP_RATED_MOVIES("Top Rated Movies page"),
    TITLE_DISPLAY("Title Display page"),
    SIGN_IN("Sign In page");

    private final String label;

    PageName(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static PageName fromLabel(String label) {
        return Arrays.stream(values())
                .filter(p -> p.label.equalsIgnoreCase(label.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown page \"" + label + "\". Known pages: "
                        + Arrays.stream(values()).map(p -> "\"" + p.label + "\"").collect(Collectors.joining(", "))));
    }
}
