package me.kyrobi.cynagenlevels;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.ListenerPriority;
import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.api.events.DiscordGuildMessageReceivedEvent;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import github.scarsz.discordsrv.dependencies.jda.api.entities.User;
import github.scarsz.discordsrv.util.DiscordUtil;

import javax.annotation.Nullable;
import java.util.UUID;

import static me.kyrobi.cynagenlevels.LevelHandler.*;

public class ChatHandler implements Listener {

    CynagenLevels plugin;

    public ChatHandler(CynagenLevels plugin){
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /*
    Listens to when a player chats ingame
     */
    @EventHandler
    public void onPlayerChatIngame(AsyncChatEvent e){

//        if(!e.getPlayer().getName().equals("Kyrobi")){
//            return;
//        }
        Player player = e.getPlayer();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            /*If the player is only on the server, then still allow them to gain exp*/
//            User user = getDiscordUser(player.getUniqueId());
//            if(user == null){ return;}

            String uuid = player.getUniqueId().toString();

            // Don't do anything if on cooldown
            if(isOnCooldown(uuid)){
                return;
            }

            int levelUpFlag = giveEXP(uuid);
            if(levelUpFlag > 0){

                // To prevent spam, only show in server chat if they reach lvl 10+
                if(levelUpFlag > 9){
                    Bukkit.broadcastMessage(ChatColor.DARK_AQUA + player.getName() + " advanced to level " + ChatColor.AQUA + levelUpFlag);
                    player.sendMessage(ChatColor.GRAY + "For more info, use /level");
                }
                else{
                    player.sendMessage(ChatColor.DARK_AQUA + player.getName() + " advanced to level " + ChatColor.AQUA + levelUpFlag);
                    player.sendMessage(ChatColor.GRAY + "For more info, use /level");
                }
            }

            long currentLevel = getCurrentLevel(uuid);
            long currentEXP = getCurrentEXP(uuid);
            long total = getEXPNeededUntilNextLevel(currentLevel, currentEXP) + currentEXP;


            System.out.println("\n"+
                    "Current Level: " + currentLevel + "\n"+
                    "Current EXP: " + currentEXP + "\n"+
                    "EXP Bar: " + "[ " + currentEXP + " / " + total + "] \n"
            );

        });

    }

    /*
    Listens to when a player chats through Discord
     */
    @Subscribe(priority = ListenerPriority.MONITOR)
    public void discordMessageReceived(DiscordGuildMessageReceivedEvent e) {
        if(e.getChannel().getName().equals("spam")){ return; }
        // if(!e.getMember().getId().equals("559428414709301279")){ return; }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            OfflinePlayer player = getMinecraftUser(e.getMember().getId());
            if(player == null){ return; }

            String uuid = String.valueOf(player.getUniqueId());

            // Don't do anything if on cooldown
            if(isOnCooldown(uuid)){
                return;
            }

            User discordUser = getDiscordUser(UUID.fromString(uuid));
            int levelUpFlag = giveEXP(player.getUniqueId().toString());
            if(levelUpFlag > 0){
                TextChannel txt = DiscordSRV.getPlugin().getJda().getTextChannelById("448488708883218442");
                txt.sendMessage( discordUser.getAsMention() + " advanced to level " + levelUpFlag).queue();
            }

            long currentLevel = getCurrentLevel(uuid);
            long currentEXP = getCurrentEXP(uuid);
            long total = getEXPNeededUntilNextLevel(currentLevel, currentEXP) + currentEXP;


            System.out.println("\n"+
                    "Current Level: " + currentLevel + "\n"+
                    "Current EXP: " + currentEXP + "\n"+
                    "EXP Bar: " + "[ " + currentEXP + " / " + total + "] \n"
            );

        });
    }


    @Nullable
    public static User getDiscordUser(UUID playerUUID) {
        if (playerUUID != null) {
            String ID = DiscordSRV.getPlugin().getAccountLinkManager().getDiscordId(playerUUID);
            if(ID != null){
                return DiscordUtil.getJda().getUserById(ID);
            }
        }
        return null;
    }

    @Nullable
    public static OfflinePlayer getMinecraftUser(String DiscordID){
        if(DiscordID != null){
            UUID uuid = DiscordSRV.getPlugin().getAccountLinkManager().getUuid(DiscordID);
            if(uuid == null){
                return null;
            }
            return Bukkit.getOfflinePlayer(uuid);
        }
        return null;
    }

}
