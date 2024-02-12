package me.kyrobi.cynagenlevels.Commands;

import github.scarsz.discordsrv.api.events.DiscordChatChannelListCommandMessageEvent;
import me.kyrobi.cynagenlevels.CynagenLevels;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import static me.kyrobi.cynagenlevels.ChatHandler.getMinecraftUser;
import static me.kyrobi.cynagenlevels.LevelHandler.*;

public class CommandLevel implements CommandExecutor {


    private CynagenLevels plugin;

    public CommandLevel(CynagenLevels plugin){
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String label, String[] args){


        if(args.length == 0){
            if(commandSender instanceof ConsoleCommandSender){
                commandSender.sendMessage(ChatColor.RED + "Console doesn't have levels");
                return false;
            }

            Player player = (Player) commandSender;
            commandSender.sendMessage(getStatsForIngame(player.getUniqueId().toString()));
            return false;
        }

        else if(args.length == 1){
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(args[0]);
            if(offlinePlayer == null){
                commandSender.sendMessage(ChatColor.RED + "Player data doesn't exist");
                return false;
            }

            commandSender.sendMessage(getStatsForIngame(offlinePlayer.getUniqueId().toString()));
            return false;

        }

        else{
            commandSender.sendMessage(ChatColor.RED + "Incorrect usage. /level [name]");
            return false;
        }

    }
}
