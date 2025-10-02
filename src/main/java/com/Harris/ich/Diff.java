package com.Harris.ich;

import java.util.*;


/**
 * Represents the differences between two snapshots of guideline data.
 * <p>
 * Contains lists of added items, removed items, and items whose titles have changed.
 */
public class Diff {

    /** List of guideline items that were added in the current snapshot. */
    public final List<Snapshot.SnapshotItem> added = new ArrayList<>();

    /** List of guideline items that were removed compared to the previous snapshot. */
    public final List<Snapshot.SnapshotItem> removed = new ArrayList<>();

    /** List of title changes for items that retained the same code but had different titles. */
    public final List<TitleChange> titleChanged = new ArrayList<>();
}
