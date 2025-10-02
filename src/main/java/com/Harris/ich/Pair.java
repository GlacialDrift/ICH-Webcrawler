package com.Harris.ich;


/**
 * Represents a simple pair of values: a guideline code and its corresponding title.
 * <p>
 * Used for intermediate data collection during parsing and normalization.
 */
public class Pair {
    public final String code;
    public final String title;
    public Pair(String c, String t) {
        this.code = c;
        this.title = t;
    }
}
