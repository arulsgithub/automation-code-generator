package com.autogen.indexer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class VectorStore {

    private final List<IndexEntry> entries = new ArrayList<>();

    public void add(IndexEntry entry) {
        entries.add(entry);
    }

    public int size() {
        return entries.size();
    }

    public List<Match> search(double[] query, int topK) {
        List<Match> matches = new ArrayList<>();
        for (IndexEntry entry : entries) {
            matches.add(new Match(entry, dot(query, entry.vector())));
        }
        matches.sort(Comparator.comparingDouble(Match::score).reversed());
        return matches.subList(0, Math.min(topK, matches.size()));
    }

    private double dot(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }
}