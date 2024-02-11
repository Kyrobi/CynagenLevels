package me.kyrobi.cynagenlevels.Commands;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.ListenerPriority;
import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.api.events.DiscordGuildMessageReceivedEvent;
import github.scarsz.discordsrv.dependencies.jda.api.entities.User;
import me.kyrobi.cynagenlevels.CynagenLevels;
import org.bukkit.event.Listener;

import static me.kyrobi.cynagenlevels.LevelHandler.getStatsForDiscord;

public class CommandLevelDiscord implements Listener {

    @Subscribe(priority = ListenerPriority.MONITOR)
    public void discordMessageReceived(DiscordGuildMessageReceivedEvent e) {

        if(e.getAuthor().isBot()){
            return;
        }

        String[] command = e.getMessage().getContentRaw().split(" ");
        if(command[0].equals("!level")){
            if(command.length == 1){
                String discordID = e.getAuthor().getId();
                e.getChannel().sendMessage(getStatsForDiscord(discordID)).queue();
            }
            else if(command.length == 2){

                User user = DiscordSRV.getPlugin().getJda().getUserById(command[1]);
                if(user == null){
                    e.getChannel().sendMessage("No data found for user").queue();
                    return;
                }

                e.getChannel().sendMessage(getStatsForDiscord(user.getId())).queue();
            }
        }
    }
}
