package com.Harris.ich;

import java.util.List;

public class Snapshot {
    public String fetchedAtIso;
    public List<SnapshotItem> items;

    public Snapshot(){}
    public Snapshot( String fetchedAtIso, List<SnapshotItem> items){
        this.fetchedAtIso = fetchedAtIso;
        this.items = items;
    }

    public static class SnapshotItem {
        public String code;
        public String title;

        public SnapshotItem() {}
        public SnapshotItem(String code, String title){
            this.code = code;
            this.title = title;
        }
    }
}


