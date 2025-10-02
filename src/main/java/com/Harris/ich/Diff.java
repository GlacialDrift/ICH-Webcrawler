package com.Harris.ich;

import java.util.*;

public class Diff {
    public final List<Snapshot.SnapshotItem> added = new ArrayList<>();
    public final List<Snapshot.SnapshotItem> removed = new ArrayList<>();
    public final List<TitleChange> titleChanged = new ArrayList<>();
}
