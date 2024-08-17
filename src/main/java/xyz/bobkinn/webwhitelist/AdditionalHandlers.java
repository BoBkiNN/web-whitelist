package xyz.bobkinn.webwhitelist;

import com.google.gson.JsonElement;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
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
        var message = readComponent(data.getObject("message", null));
        if (message == null) {
            throw new IllegalArgumentException("message is not a component");
        }
        var player = Bukkit.getPlayer(playerName);
        if (player == null || !player.isOnline()) {
            throw new IllegalArgumentException("Player not found or offline");
        }
        player.kick(message);
        ws.send(DataHolder.ofSuccess(msg));
    }

    public static final GsonComponentSerializer GSON_COMPONENT_SERIALIZER = GsonComponentSerializer.gson();

    public static Component readComponent(JsonElement e){
        if (e == null) return null;
        try {
            return GSON_COMPONENT_SERIALIZER.deserializeFromTree(e);
        } catch (Exception ex) {
            return null;
        }
    }

}
