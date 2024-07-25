package xyz.bobkinn.webwhitelist;

import java.util.Set;

@SuppressWarnings("unused")
public abstract class WhitelistHandler {
    public abstract boolean add(String player);

    /**
     * @param player username
     * @return true if this player was in whitelist and it successfully removed
     */
    @SuppressWarnings("UnusedReturnValue")
    public abstract boolean remove(String player);

    public abstract boolean clear();

    public abstract Set<String> asList();

    public abstract boolean isWhitelisted(String player);

    public void handleDiff(WhitelistDiff diff){
        var added = diff.added();
        var removed = diff.removed();
        added.forEach(n -> {
            if (n.isBlank()) return;
            if (!add(n)) Main.LOGGER.info("Failed to add '{}'", n);
        });
        removed.forEach(n -> {
            if (n.isBlank()) return;
            if (!remove(n)) Main.LOGGER.info("Failed to remove '{}'", n);
        });
    }
}
