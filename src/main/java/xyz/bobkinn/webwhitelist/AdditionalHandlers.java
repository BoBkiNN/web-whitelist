package xyz.bobkinn.webwhitelist;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import xyz.bobkinn.indigodataio.gson.GsonData;

public class AdditionalHandlers {
    private final PluginClient ws;

    public AdditionalHandlers(PluginClient ws) {
        this.ws = ws;
    }

    private void register(String id, MessageHandler handler) {
        ws.registerHandler(id, handler);
    }

    public void registerAll(){
        register("kick", this::kick);
    }

    public void kick(GsonData data, MessageInfo msg){
        String playerName = data.getString("player");
        if (playerName == null) {
            throw new IllegalArgumentException("player field not found");
        }
        var player = Bukkit.getPlayer(playerName);
        if (player == null || !player.isOnline()) {
            throw new IllegalArgumentException("Player not found or offline");
        }
        var messageFormat = data.getString("message_format", "minimessage");
        final Component message;
        if (messageFormat.equalsIgnoreCase("minimessage")) {
            message = readMiniMessage(data.getString("message"));
        } else {
            var text = data.getString("message");
            if (text != null) {
                message = Main.COMPONENT_SERIALIZER.deserialize(text);
            } else message = null;
        }
        Bukkit.getScheduler().runTaskLater(ws.getPlugin(), () -> {
            if (message != null) player.kick(message);
            else player.kick();
        }, 0);
        ws.send(DataHolder.ofSuccess(msg));
    }

    public static final MiniMessage MM_COMPONENT_SERIALIZER = MiniMessage.miniMessage();

    public static Component readMiniMessage(String s){
        if (s == null) return null;
        try {
            return MM_COMPONENT_SERIALIZER.deserialize(s);
        } catch (Exception ex) {
            return null;
        }
    }

}
