package xyz.bobkinn.webwhitelist;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;

@Getter
public final class Main extends JavaPlugin {
    public static final Logger LOGGER = LoggerFactory.getLogger("WebWhitelist");
    public static final LegacyComponentSerializer COMPONENT_SERIALIZER = LegacyComponentSerializer.builder()
            .hexColors().extractUrls().character('&').build();
    public static final TypeToken<Map<String, Object>> DATA_TYPETOKEN = new TypeToken<>(){};
    public static final Gson GSON = new Gson();

    private WhitelistHandler handler = null;
    private PluginClient ws = null;
    private final RollingList<ActionLog> logs = new RollingList<>(15);
    private DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM HH:mm:ss");
    private ZoneId timeZone = ZoneId.systemDefault();

    public static String formatMillis(long millis, DateTimeFormatter formatter, ZoneId zoneId) {
        // Create an Instant from the milliseconds
        Instant instant = Instant.ofEpochMilli(millis);
        // Create a ZonedDateTime from the Instant and ZoneId
        ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(instant, zoneId);
        // Format the ZonedDateTime to the desired format
        return zonedDateTime.format(formatter);
    }

    public String formatMillis(long millis) {
        return formatMillis(millis, dateTimeFormatter, timeZone);
    }

    public void addLog(ActionLog.Action action, Set<String> players){
        logs.add(new ActionLog(System.currentTimeMillis(), action, players));
    }

    public static String replaceArgs(String text, Object... args) {
        StringBuilder result = new StringBuilder();
        int argIndex = 0;
        int i = 0;

        while (i < text.length()) {
            if (text.charAt(i) == '\\' && i + 1 < text.length() && text.charAt(i + 1) == '{') {
                result.append('{');
                i += 2; // Skip escaped brace
            } else if (text.charAt(i) == '{' && i + 1 < text.length() && text.charAt(i + 1) == '}') {
                if (argIndex < args.length) {
                    result.append(args[argIndex++]);
                } else {
                    result.append("{}"); // If no arguments left, leave as is
                }
                i += 2; // Skip braces
            } else {
                result.append(text.charAt(i));
                i++;
            }
        }

        return result.toString();
    }

    public Component getTranslate(String key, Object... args){
        var msg = getConfig().getString("messages."+key, "{"+key+"}");
        var rl = replaceArgs(msg, args);
        return COMPONENT_SERIALIZER.deserialize(rl);
    }

    public String getRawTranslate(String key){
        return getConfig().getString("messages."+key, "{"+key+"}");
    }

    public Component getTranslate(String key){
        var msg = getConfig().getString("messages."+key, "{"+key+"}");
        return COMPONENT_SERIALIZER.deserialize(msg);
    }

    public void reload(){
        saveDefaultConfig();
        reloadConfig();
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
        try {
            dateTimeFormatter = DateTimeFormatter.ofPattern(getConfig().getString("time-format", "dd.MM HH:mm:ss"));
        } catch (Exception e) {
            dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM HH:mm:ss");
        }
        try {
            timeZone = ZoneId.of(getConfig().getString("time-zone", ZoneId.systemDefault().toString()));
        } catch (Exception e) {
            timeZone = ZoneId.systemDefault();
        }
        var timeout = getConfig().getInt("timeout", 5);
        var reconnectDelay = getConfig().getInt("reconnect-delay", 60);
        var reconnectMultiplier = Math.max(getConfig().getDouble("reconnect-multiplier", 1.3d), 1d);
        ws = new PluginClient(this, uri, handler, timeout, reconnectDelay, reconnectMultiplier);
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
        if (ws != null) ws.connect();
    }

    @Override
    public void onDisable() {
        if (ws != null && ws.isOpen()) {
            try {
                ws.closeBlocking();
            } catch (Exception e) {
                LOGGER.error("Failed to close websocket connection", e);
            }
        }
    }
}
