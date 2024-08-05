package xyz.bobkinn.webwhitelist;

import com.cocahonka.comfywhitelist.api.ComfyWhitelistAPI;
import com.cocahonka.comfywhitelist.api.Storage;
import org.bukkit.Bukkit;

import java.util.Set;

public class ComfyWhitelistHandler extends WhitelistHandler {
    private final Storage whitelist;

    public ComfyWhitelistHandler(){
        var provider = Bukkit.getServicesManager().getRegistration(ComfyWhitelistAPI.class);
        if (provider == null) throw new IllegalStateException("No provider found for ComfyWhitelistAPI");
        var api = provider.getProvider();
        whitelist = api.getStorage();
    }

    @Override
    public boolean add(String player) {
        if (!isWhitelisted(player)) {
            return whitelist.addPlayer(player);
        } else return false;
    }

    @Override
    public boolean remove(String player) {
        if (isWhitelisted(player)) {
            return whitelist.removePlayer(player);
        } else return false;
    }

    @Override
    public boolean clear() {
        return whitelist.clear();
    }

    @Override
    public Set<String> asList() {
        return whitelist.getAllWhitelistedPlayers();
    }

    @Override
    public boolean isWhitelisted(String player) {
        return whitelist.isPlayerWhitelisted(player);
    }
}
