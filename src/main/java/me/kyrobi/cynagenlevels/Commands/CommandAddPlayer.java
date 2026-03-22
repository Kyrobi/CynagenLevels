package me.kyrobi.cynagenlevels.Commands;

import me.kyrobi.cynagenlevels.CynagenLevels;
import me.kyrobi.cynagenlevels.LevelHandler;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import static me.kyrobi.cynagenlevels.ChatHandler.getMinecraftUser;

public class CommandAddPlayer implements CommandExecutor {

    private final CynagenLevels plugin;

    public CommandAddPlayer(CynagenLevels plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String label, String[] args) {
        if (!(commandSender instanceof ConsoleCommandSender)) {
            return false;
        }

        if (args.length < 2) {
            Bukkit.getLogger().info("Not enough args");
            return false;
        }

        String discordID = args[0];
        String levelsArg = args[1];

        OfflinePlayer player = getMinecraftUser(discordID);
        if (player == null) {
            Bukkit.getLogger().info("Player isn't linked");
            return false;
        }

        long newLevel;
        try {
            newLevel = Long.parseLong(levelsArg);
        } catch (NumberFormatException e) {
            Bukkit.getLogger().info("Invalid level value: " + levelsArg);
            return false;
        }

        Bukkit.getLogger().info("Fetched user " + player.getName() + " associated with this ID");

        String uuid = player.getUniqueId().toString();

        // Preserve existing EXP — only overwrite the level
        long existingEXP = LevelHandler.getCurrentEXP(uuid);
        LevelHandler.setPlayerData(uuid, newLevel, existingEXP);

        return false;
    }
}