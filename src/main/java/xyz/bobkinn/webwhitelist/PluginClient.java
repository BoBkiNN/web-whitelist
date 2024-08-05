package xyz.bobkinn.webwhitelist;

import com.google.gson.JsonSyntaxException;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class PluginClient extends WebSocketClient {

    public interface MessageHandler {
        void handle(Map<String, Object> data);
    }

    private final Map<String, MessageHandler> handlers;
    private final WhitelistHandler whitelist;
    private final Main plugin;
    private final int reconnectDelay; // seconds

    public PluginClient(Main plugin, URI uri, WhitelistHandler whitelist, int timeout, int reconnectDelay) {
        super(uri, new Draft_6455(),null, (int) TimeUnit.SECONDS.toMillis(timeout));
        this.plugin = plugin;
        this.whitelist = whitelist;
        this.reconnectDelay = reconnectDelay;
        handlers = new HashMap<>();
        handlers.put("add", this::onAdd);
        handlers.put("remove", this::onRemove);
        handlers.put("list", this::onList);
        handlers.put("info", this::onInfo);
    }

    public void onInfo(Map<String, Object> data){
        var map = new HashMap<String, Object>();
        //noinspection UnstableApiUsage
        map.put("plugin_version", plugin.getPluginMeta().getVersion());
        map.put("players_online", plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList());
        map.put("handlers", handlers.keySet());
        map.put("logs", plugin.getLogs());
        send(DataHolder.ofSuccess("info", map));
    }

    public void onList(Map<String, Object> data) {
        var players = whitelist.asList();
        send(DataHolder.ofSuccess("list", Map.of("players", players)));
    }

    public void onRemove(Map<String, Object> data) {
        final List<String> players;
        try {
            //noinspection unchecked
            players = (List<String>) data.get("players");
        } catch (Exception e) {
            throw new IllegalArgumentException("players field not found or not a list");
        }
        if (players == null) {
            throw new IllegalArgumentException("players field not found");
        }
        var success = true;
        for (var p : players) {
            if (!whitelist.remove(p)) success = false;
        }
        plugin.addLog(ModifyLog.Action.REMOVE, new HashSet<>(players));
        if (success) {
            send(DataHolder.ofSuccess("remove"));
        } else {
            throw new IllegalStateException("Failed to remove some players to whitelist");
        }
    }

    public void onAdd(Map<String, Object> data){
        final List<String> players;
        try {
            //noinspection unchecked
            players = (List<String>) data.get("players");
        } catch (Exception e) {
            throw new IllegalArgumentException("players field not found or not a list");
        }
        if (players == null) {
            throw new IllegalArgumentException("players field not found");
        }
        var success = true;
        for (var p : players) {
            if (!whitelist.add(p)) success = false;
        }
        plugin.addLog(ModifyLog.Action.ADD, new HashSet<>(players));
        if (success) {
            send(DataHolder.ofSuccess("add"));
        } else {
            throw new IllegalStateException("Failed to add some players to whitelist");
        }
    }

    public void send(DataHolder holder){
        var json = Main.GSON.toJson(holder);
        send(json);
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        Main.LOGGER.info("Connected to {}", getURI());
        plugin.addLog(ModifyLog.Action.CONNECT, new HashSet<>(0));
    }

    @Override
    public void onMessage(String message) {
        final DataHolder holder;
        try {
            holder = Main.GSON.fromJson(message, DataHolder.class);
        } catch (JsonSyntaxException e){
            send(DataHolder.ofError("unknown", e));
            Main.LOGGER.error("Failed to parse received json", e);
            return;
        }
        String type = holder.getType();
        var handler = handlers.get(type);
        if (handler == null) {
            send(DataHolder.ofError("unknown", new IllegalArgumentException("unknown type '"+type+"'")));
            Main.LOGGER.error("Unknown message type received: {}", type);
            return;
        }
        try {
            handler.handle(holder.getData());
        } catch (Exception e){
            send(DataHolder.ofError(type, e));
            Main.LOGGER.error("Failed to handle data of type '{}'", type, e);
        }
    }

    public void reconnectOnClose(){
        reconnect();
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        if (CloseFrame.NEVER_CONNECTED == code) {
            Main.LOGGER.error("Failed to connect, reconnecting in {} seconds", reconnectDelay);
            plugin.addLog(ModifyLog.Action.FAILED_TO_CONNECT, new HashSet<>(0));
        } else {
            Main.LOGGER.info("Websocket closed with code {} ({}), reconnecting in {} seconds", code, reason, reconnectDelay);
            plugin.addLog(ModifyLog.Action.DISCONNECTED, new HashSet<>(0));
        }
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, this::reconnectOnClose, reconnectDelay*20L);
    }

    @Override
    public void onError(Exception e) {
        Main.LOGGER.error("Websocket error", e);
    }
}
