package xyz.bobkinn.webwhitelist;

import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
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
            "status", this::status
    );

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



    public Component getLogText(ActionLog log){
         final String additional;
         if (log.action().getAdditionalKey() != null) {
             additional = Main.replaceArgs(plugin.getRawTranslate(log.action().getAdditionalKey()), log.players());
         } else {
             additional = "";
         }
         final String time = plugin.formatMillis(log.timestamp());
         return plugin.getTranslate("status-log-entry", time, log.action().name(), additional);
    }

    public void status(CommandSender sender){
        sender.sendMessage(plugin.getTranslate("status-state", plugin.getWs().getReadyState().name()));
        var logs = plugin.getLogs();
        if (logs.isEmpty()) return;
        for (var log : logs) {
            sender.sendMessage(getLogText(log));
        }
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
