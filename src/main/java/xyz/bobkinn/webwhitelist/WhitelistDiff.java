package xyz.bobkinn.webwhitelist;

import java.util.Set;

public record WhitelistDiff(Set<String> added, Set<String> removed) {

    public boolean isEmpty(){
        return added.isEmpty() && removed.isEmpty();
    }

    @Override
    public String toString() {
        return "WhitelistDiff{" +
                "added=" + added.size() +
                ", removed=" + removed.size() +
                '}';
    }
}
