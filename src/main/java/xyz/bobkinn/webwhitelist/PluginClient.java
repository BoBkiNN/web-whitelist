package xyz.bobkinn.webwhitelist;

import com.google.gson.JsonSyntaxException;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.enums.ReadyState;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ServerHandshake;

import java.net.ConnectException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class PluginClient extends WebSocketClient {

    public interface MessageHandler {
        void handle(Map<String, Object> data, MessageInfo msg);
    }

    private final Map<String, MessageHandler> handlers;
    private final WhitelistHandler whitelist;
    private final Main plugin;
    private final int baseReconnectDelay; // seconds
    private final double reconnectMultiplier;
    private int reconnectDelay;
    private int failedTimes = 0;
    private BukkitTask reconnectTask = null;
    @Getter @Setter
    private boolean stopping = false;

    public PluginClient(Main plugin, URI uri, WhitelistHandler whitelist, int timeout, int baseReconnectDelay, double reconnectMultiplier) {
        super(uri, new Draft_6455(),null, (int) TimeUnit.SECONDS.toMillis(timeout));
        this.plugin = plugin;
        this.whitelist = whitelist;
        this.baseReconnectDelay = baseReconnectDelay;
        this.reconnectMultiplier = reconnectMultiplier;
        reconnectDelay = baseReconnectDelay;
        handlers = new HashMap<>();
        handlers.put("add", this::onAdd);
        handlers.put("remove", this::onRemove);
        handlers.put("list", this::onList);
        handlers.put("info", this::onInfo);
    }

    public void onInfo(Map<String, Object> data, MessageInfo msg){
        var map = new HashMap<String, Object>();
        //noinspection UnstableApiUsage
        map.put("plugin_version", plugin.getPluginMeta().getVersion());
        map.put("players_online", plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList());
        map.put("handlers", handlers.keySet());
        map.put("logs", plugin.getLogs());
        send(DataHolder.ofSuccess(msg, map));
    }

    public void onList(Map<String, Object> data, MessageInfo msg) {
        var players = whitelist.asList();
        send(DataHolder.ofSuccess(msg, Map.of("players", players)));
    }

    public void onRemove(Map<String, Object> data, MessageInfo msg) {
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
        var failed = new ArrayList<String>(players.size());
        for (var p : players) {
            if (!whitelist.remove(p)) {
                failed.add(p);
            }
        }
        var modList = new ArrayList<>(players);
        modList.removeAll(failed);
        plugin.addLog(ActionLog.Action.REMOVED, new HashSet<>(modList));
        if (failed.isEmpty()) {
            send(DataHolder.ofSuccess(msg));
        } else {
            var e = new IllegalStateException("Failed to remove some players to whitelist");
            send(DataHolder.ofError(msg, e, Map.of("players", failed)));
            Main.LOGGER.warn("Failed to remove some players to whitelist: {}", failed);
        }
    }

    public void onAdd(Map<String, Object> data, MessageInfo msg){
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
        var failed = new ArrayList<String>(players.size());
        for (var p : players) {
            if (!whitelist.add(p)) {
                failed.add(p);
            }
        }
        var modList = new ArrayList<>(players);
        modList.removeAll(failed);
        plugin.addLog(ActionLog.Action.ADDED, new HashSet<>(modList));
        if (failed.isEmpty()) {
            send(DataHolder.ofSuccess(msg));
        } else {
            var e = new IllegalStateException("Failed to add some players to whitelist");
            send(DataHolder.ofError(msg, e, Map.of("players", failed)));
            Main.LOGGER.warn("Failed to add some players to whitelist: {}", failed);
        }
    }

    public void send(DataHolder holder){
        var json = Main.GSON.toJson(holder);
        send(json);
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        failedTimes = 0;
        reconnectDelay = baseReconnectDelay;
        Main.LOGGER.info("Connected to {}", getURI());
        plugin.addLog(ActionLog.Action.CONNECTED, new HashSet<>(0));
        onInfo(null, DataHolder.newEmpty("info"));
    }

    @Override
    public void onMessage(String message) {
        final DataHolder holder;
        try {
            holder = Main.GSON.fromJson(message, DataHolder.class);
        } catch (JsonSyntaxException e){
            Main.LOGGER.error("Failed to parse received json", e);
            return;
        }
        String type = holder.getType();
        var handler = handlers.get(type);
        if (handler == null) {
            send(DataHolder.ofError("unknown", holder.getId(), new IllegalArgumentException("unknown type '"+type+"'")));
            Main.LOGGER.error("Unknown message type received: {}", type);
            return;
        }
        try {
            handler.handle(holder.getData(), holder);
        } catch (Exception e){
            send(DataHolder.ofError(holder, e));
            Main.LOGGER.error("Failed to handle message '{}'", type, e);
        }
    }

    public void asyncReconnect(Runnable onReconnected, Consumer<Long> onFail, boolean now){
        if (reconnectTask != null && !reconnectTask.isCancelled()) reconnectTask.cancel();
        reconnectTask = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            try {
                var start = System.currentTimeMillis();
                reconnectBlocking();
                if (getReadyState() == ReadyState.OPEN && onReconnected != null) {
                    onReconnected.run();
                } else if (onFail != null) {
                    onFail.accept(System.currentTimeMillis()-start);
                }
            } catch (InterruptedException ignored) {
            }
        },  now ? 0 : reconnectDelay*20L);
    }

    private void recalculateDelay(){
        int add = (int) (baseReconnectDelay * reconnectMultiplier * (failedTimes-1));
        reconnectDelay = baseReconnectDelay + add;
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        failedTimes += 1;
        recalculateDelay();
        if (CloseFrame.NEVER_CONNECTED == code) {
            Main.LOGGER.warn("Failed to connect, reconnecting in {} seconds", reconnectDelay);
            plugin.addLog(ActionLog.Action.FAILED_TO_CONNECT, new HashSet<>(0));
        } else {
            if (reason != null && !reason.isBlank()) {
                if (stopping) {
                    Main.LOGGER.info("Websocket closed with code {} ({})", code, reason);
                } else {
                    Main.LOGGER.warn("Websocket closed with code {} ({}), reconnecting in {} seconds", code, reason, reconnectDelay);
                }
            } else {
                if (stopping) {
                    Main.LOGGER.info("Websocket closed with code {}", code);
                } else {
                    Main.LOGGER.warn("Websocket closed with code {}, reconnecting in {} seconds", code, reconnectDelay);
                }
            }
            plugin.addLog(ActionLog.Action.DISCONNECTED, new HashSet<>(0));
        }
        if (!stopping) asyncReconnect(null, null, false);
    }

    public void stop(){
        setStopping(true);
        try {
            if (reconnectTask != null) reconnectTask.cancel();
            if (!isClosed()) closeBlocking();
            setStopping(false);
        } catch (Exception e) {
            Main.LOGGER.error("Failed to close websocket", e);
        }
    }

    @Override
    public void onError(Exception e) {
        if (e instanceof ConnectException && e.getMessage().contains("getsockopt")) {
            return;
        }
        Main.LOGGER.error("Websocket error", e);
    }
}
