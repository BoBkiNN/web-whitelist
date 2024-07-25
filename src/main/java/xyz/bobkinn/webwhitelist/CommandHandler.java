package xyz.bobkinn.webwhitelist;

import lombok.RequiredArgsConstructor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class CommandHandler implements TabCompleter, CommandExecutor {
    private final Main plugin;
    private final Map<String, SubCommand> subs = Map.of(
            "reload", this::reload,
            "status", this::status,
            "switch", this::switchSub,
            "update", this::updateSub);

    public interface SubCommand {
        void execute(CommandSender sender);

        static boolean hasPerm(@NotNull CommandSender sender, String subName){
            return sender.hasPermission("webwhitelist."+subName);
        }
    }

    public void reload(CommandSender sender){
        try {
            plugin.reload();
            sender.sendMessage(plugin.getTranslate("reload-success"));
        } catch (Exception e) {
            Main.LOGGER.warn("Failed to reload", e);
            sender.sendMessage(plugin.getTranslate("reload-failed", e.getMessage()));
        }
    }

    public void status(CommandSender sender){
        if (plugin.isEnableUpdates()) {
            sender.sendMessage(plugin.getTranslate("status-enabled"));
        } else {
            sender.sendMessage(plugin.getTranslate("status-disabled"));
        }
        var lastSuccess = plugin.getRequester().lastSuccessfully();
        if (lastSuccess != -1) { // there were no successful requests
            var delta = System.currentTimeMillis() - lastSuccess;
            sender.sendMessage(plugin.getTranslate("last-successful-request",  (int) delta / 1000));
        }
    }

    public void switchSub(CommandSender sender){
        var newState = !plugin.isEnableUpdates();
        plugin.setEnableUpdates(newState);
        if (newState) {
            plugin.getRequester().start();
            sender.sendMessage(plugin.getTranslate("switch-enabled"));
        } else {
            plugin.getRequester().stop();
            sender.sendMessage(plugin.getTranslate("switch-disabled"));
        }
        plugin.saveData();
    }

    public void updateSub(CommandSender sender){
        sender.sendMessage(plugin.getTranslate("update-waiting"));
        if (plugin.getRequester() == null) return;
        plugin.getRequester().request(e -> {
            if (e == null) {
                sender.sendMessage(plugin.getTranslate("update-success"));
            } else {
                Main.LOGGER.debug("Failed to force-update", e);
                sender.sendMessage(plugin.getTranslate("update-failed", e.getMessage()));
            }
        });
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!SubCommand.hasPerm(sender, "command")) {
            sender.sendMessage(plugin.getTranslate("no-access"));
            return false;
        }
        if (args.length == 0) {
            if (!SubCommand.hasPerm(sender, "status")) {
                sender.sendMessage(plugin.getTranslate("no-access"));
                return false;
            }
            var cmd = subs.get("status");
            if (cmd != null) cmd.execute(sender);
            return true;
        }
        var sub = args[0];
        var cmd = subs.get(sub);
        var hasPerm = SubCommand.hasPerm(sender, sub);
        if (cmd == null || !hasPerm) {
            sender.sendMessage(plugin.getTranslate("no-access"));
            return false;
        }

        cmd.execute(sender);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("webwhitelist.command")) return List.of();
        if (args.length <= 1) {
            return subs.keySet().stream().filter(s -> SubCommand.hasPerm(sender, s)).toList();
        }
        return List.of();
    }
}
