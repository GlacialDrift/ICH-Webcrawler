package com.Harris.ich;

import java.util.*;


/**
 * Utility class for computing differences between two guideline snapshots.
 * <p>
 * Identifies added, removed, and title-changed items between a previous and current snapshot.
 */
public class DiffSnapshots {


    /**
     * Default constructor.
     * <p>
     * This class is stateless and primarily used via its static methods.
     */
    public DiffSnapshots(){}


    /**
     * Computes the differences between two snapshots.
     * <p>
     * Determines which items were added, removed, or had title changes
     * based on normalized code keys and titles.
     *
     * @param prev the previous snapshot
     * @param curr the current snapshot
     * @return a {@link Diff} object containing the differences
     */
    public static Diff performDiff(Snapshot prev, Snapshot curr){
        Diff diff = new Diff();

        Set<String> prevKeys = new LinkedHashSet<>();
        for(Snapshot.SnapshotItem it: prev.items){
            prevKeys.add(key(it));
        }

        Set<String> currKeys = new LinkedHashSet<>();
        for(Snapshot.SnapshotItem it: curr.items){
            currKeys.add(key(it));
        }

        for(Snapshot.SnapshotItem it: curr.items){
            if(!prevKeys.contains(key(it))) diff.added.add(it);
        }
        for(Snapshot.SnapshotItem it: prev.items){
            if(!currKeys.contains(key(it))) diff.removed.add(it);
        }


        Map<String, Set<String>> prevTitlesByCode = titlesByCode(prev.items);
        Map<String, Set<String>> currTitlesByCode = titlesByCode(curr.items);
        for(String code: currTitlesByCode.keySet()){
            if(prevTitlesByCode.containsKey(code)){
                Set<String> oldTitles = prevTitlesByCode.get(code);
                Set<String> newTitles = currTitlesByCode.get(code);

                if(!oldTitles.equals(newTitles)){
                    for(String nt : newTitles){
                        if(!oldTitles.contains(nt)){

                            String old = oldTitles.stream().findFirst().orElse("");
                            diff.titleChanged.add(new TitleChange(code, old, nt));
                        }
                    }
                }
            }
        }
        return diff;
    }


    /**
     * Builds a mapping from normalized guideline codes to a set of normalized titles.
     *
     * @param items list of snapshot items
     * @return map of code keys to sets of title keys
     */
    private static Map<String, Set<String>> titlesByCode(List<Snapshot.SnapshotItem> items){
        Map<String, Set<String>> map = new LinkedHashMap<>();
        for(Snapshot.SnapshotItem it: items){
            String codeKey = Main.normalizeCodeKey(it.code);
            String titleKey = Main.normalizeCodeKey(it.title);
            map.computeIfAbsent(codeKey, k-> new LinkedHashSet<>()).add(titleKey);
        }
        return map;
    }


    /**
     * Generates a normalized key for a snapshot item based on its code.
     *
     * @param it the snapshot item
     * @return normalized code key
     */
    private static String key(Snapshot.SnapshotItem it){
        return Main.normalizeCodeKey(it.code);
    }
}
