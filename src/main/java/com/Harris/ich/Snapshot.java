package com.Harris.ich;

import java.util.List;


/**
 * Represents a snapshot of guideline data fetched at a specific time.
 * <p>
 * A snapshot contains a timestamp and a list of guideline items,
 * each identified by a code and title.
 */
public class Snapshot {

    /** ISO 8601 formatted timestamp indicating when the snapshot was fetched. */
    public String fetchedAtIso;

    /** List of guideline items included in the snapshot. */
    public List<SnapshotItem> items;


    /**
     * Default constructor for deserialization or manual instantiation.
     */
    public Snapshot(){}


    /**
     * Constructs a Snapshot with a timestamp and a list of items.
     *
     * @param fetchedAtIso ISO 8601 formatted timestamp
     * @param items list of guideline items
     */
    public Snapshot( String fetchedAtIso, List<SnapshotItem> items){
        this.fetchedAtIso = fetchedAtIso;
        this.items = items;
    }


    /**
     * Represents a single guideline item within a snapshot.
     * <p>
     * Each item includes a unique code and a title.
     */
    public static class SnapshotItem {

        /** The unique code identifying the guideline item. */
        public String code;

        /** The title of the guideline item. */
        public String title;


        /**
         * Default constructor for deserialization or manual instantiation.
         */
        public SnapshotItem() {}


        /**
         * Constructs a SnapshotItem with a code and title.
         *
         * @param code the unique identifier for the item
         * @param title the title of the item
         */
        public SnapshotItem(String code, String title){
            this.code = code;
            this.title = title;
        }
    }
}


