package xyz.bobkinn.webwhitelist;

import com.google.common.base.Charsets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Getter
public final class Main extends JavaPlugin {
    public static final Logger LOGGER = LoggerFactory.getLogger("WebWhitelist");
    public static final LegacyComponentSerializer COMPONENT_SERIALIZER = LegacyComponentSerializer.builder()
            .hexColors().extractUrls().character('&').build();
    public static final TypeToken<Map<String, Object>> DATA_TYPETOKEN = new TypeToken<>(){};
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private WhitelistHandler handler = null;
    private final Requester requester = new Requester(this);
    @Setter
    private boolean enableUpdates = true;

    public static WhitelistDiff createDiff(Set<String> n, Set<String> existing){
        var added = new HashSet<>(n);
        var removed = new HashSet<>(existing);
        added.removeAll(existing);
        removed.removeAll(n);
        return new WhitelistDiff(added, removed);
    }

    public void handleResponse(Set<String> players){
        var current = handler.asList();
        var diff = createDiff(players, current);
        if (diff.isEmpty()) return;
        try {
            handler.handleDiff(diff);
            LOGGER.info("Updated whitelist with {} addition(s) and {} deletion(s)", diff.added().size(), diff.removed().size());
        } catch (Exception e){
            LOGGER.error("Failed to handle whitelist update", e);
            throw new RuntimeException("Failed to handle whitelist update", e);
        }
    }

    public Component getTranslate(String key, Object arg){
        var msg = getConfig().getString("messages."+key, "{"+key+"}").replace("{}", String.valueOf(arg));
        return COMPONENT_SERIALIZER.deserialize(msg);
    }

    public Component getTranslate(String key){
        var msg = getConfig().getString("messages."+key, "{"+key+"}");
        return COMPONENT_SERIALIZER.deserialize(msg);
    }

    public void saveData(){
        var file = new File(getDataFolder(), "data.json");
        if (!getDataFolder().isDirectory()) if (!getDataFolder().mkdirs()) {
            LOGGER.warn("Failed to mkdir data folder");
            return;
        }
        var map = new HashMap<String, Object>();
        map.put("enableUpdates", enableUpdates);
        try (var fw = new FileWriter(file)){
            GSON.toJson(map, DATA_TYPETOKEN.getType(), fw);
        } catch (Exception e) {
            LOGGER.error("Failed to save data", e);
        }
    }

    public void reloadData(){
        var file = new File(getDataFolder(), "data.json");
        if (!file.isFile()) {
            saveData();
            return;
        }
        try (var fr = new FileReader(file, Charsets.UTF_8)){
            var map = GSON.fromJson(fr, DATA_TYPETOKEN);
            enableUpdates = (boolean) map.getOrDefault("enableUpdates", true);
        } catch (Exception e) {
            LOGGER.error("Failed to load data", e);
        }
    }

    public void reload(){
        saveDefaultConfig();
        reloadConfig();
        reloadData();
        var url = getConfig().getString("url");
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Missing 'url' value in config");
        }
        final URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e){
            throw new IllegalArgumentException("Failed to parse url");
        }
        var delay = getConfig().getInt("delay", 300);
        var timeout = getConfig().getInt("timeout", 5);
        requester.reload(uri, delay, timeout);
        if (enableUpdates && !requester.isUpdating()) {
            requester.start();
        }
    }

    @Override
    public void onEnable() {
        handler = new ComfyWhitelistHandler();
        try {
            reload();
        } catch (Exception e){
            LOGGER.error("Failed to load config: {}. Disabling..", e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        var command = getCommand("webwhitelist");
        if (command != null) {
            var ex = new CommandHandler(this);
            command.setExecutor(ex);
            command.setTabCompleter(ex);
        }
    }

    @Override
    public void onDisable() {
        requester.stop();
        saveData();
    }
}
