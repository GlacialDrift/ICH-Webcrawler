package com.Harris.ich;


/**
 * Represents a change in the title of a guideline document.
 * <p>
 * Used to track modifications between snapshots, including the code identifier,
 * the previous title, and the updated title.
 */
public class TitleChange {

    /** The unique code identifying the guideline. */
    public final String code;

    /** The previous title of the guideline. */
    public final String oldTitle;

    /** The updated title of the guideline. */
    public final String newTitle;


    /**
     * Constructs a new TitleChange instance.
     *
     * @param code the unique code of the guideline
     * @param oldTitle the previous title
     * @param newTitle the updated title
     */
    public TitleChange(String code, String oldTitle, String newTitle){
        this.code = code;
        this.oldTitle = oldTitle;
        this.newTitle = newTitle;
    }
}
