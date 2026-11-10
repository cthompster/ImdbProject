package enums;

import java.util.Locale;

public enum SortOrder {
    ASCENDING("asc"),
    DESCENDING("desc");

    private final String urlValue;

    SortOrder(String urlValue) {
        this.urlValue = urlValue;
    }

    /** How IMDB writes the order in the page URL, e.g. {@code ?sort=release_date%2Casc}. */
    public String getUrlValue() {
        return urlValue;
    }

    public static SortOrder fromText(String text) {
        try {
            return valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown sort order \"" + text + "\". Use ascending or descending.", e);
        }
    }
}
