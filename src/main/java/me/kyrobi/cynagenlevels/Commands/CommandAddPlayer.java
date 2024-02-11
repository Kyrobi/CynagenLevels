package me.kyrobi.cynagenlevels.Commands;

import me.kyrobi.cynagenlevels.CynagenLevels;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import static me.kyrobi.cynagenlevels.ChatHandler.getMinecraftUser;
import static me.kyrobi.cynagenlevels.LevelHandler.userCache;

public class CommandAddPlayer implements CommandExecutor {


    private CynagenLevels plugin;

    public CommandAddPlayer(CynagenLevels plugin){
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String label, String[] args){


        if(commandSender instanceof ConsoleCommandSender){

            if(args.length < 2){
                Bukkit.getLogger().info("Not enough args");
                return false;
            }

            String discordID = args[0];
            String levels = args[1];

            OfflinePlayer player = getMinecraftUser(discordID);

            if(player == null){
                Bukkit.getLogger().info("Player isn't linked");
                return false;
            }

            Bukkit.getLogger().info("Fetched user " + player.getName() + " associated with this ID");
            String uuid = player.getUniqueId().toString();
            userCache.put(uuid, new Long[]{Long.parseLong(levels), 0L});

        }

        return false;
    }
}
