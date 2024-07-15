package me.kyrobi.cynagenlevels;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.JDA;
import github.scarsz.discordsrv.util.DiscordUtil;
import me.kyrobi.cynagenlevels.Commands.CommandAddPlayer;
import me.kyrobi.cynagenlevels.Commands.CommandLeaderboard;
import me.kyrobi.cynagenlevels.Commands.CommandLevel;
import me.kyrobi.cynagenlevels.Commands.CommandLevelDiscord;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.plugin.java.JavaPlugin;

import static me.kyrobi.cynagenlevels.LevelHandler.saveCacheToSQL;

public final class CynagenLevels extends JavaPlugin {

    private ChatHandler discordsrvListener;
    private CommandLevelDiscord discordsrvListenerCommands;
    private CommandLeaderboard discordsrvListenerCommandLeaderboard;

    @Override
    public void onEnable() {
        new LevelHandler(this);

        Bukkit.getScheduler().runTaskLater(this, () -> {

            discordsrvListener = new ChatHandler(this);
            DiscordSRV.api.subscribe(discordsrvListener);

            discordsrvListenerCommands = new CommandLevelDiscord();
            DiscordSRV.api.subscribe(discordsrvListenerCommands);

            discordsrvListenerCommandLeaderboard = new CommandLeaderboard(this);
            DiscordSRV.api.subscribe(discordsrvListenerCommandLeaderboard);

            this.getCommand("addplayer").setExecutor((CommandExecutor)new CommandAddPlayer(this));
            this.getCommand("level").setExecutor((CommandExecutor)new CommandLevel(this));
            this.getCommand("leveltop").setExecutor((CommandExecutor)discordsrvListenerCommandLeaderboard);
        }, 20L * 10);

    }

    @Override
    public void onDisable() {
        DiscordSRV.api.unsubscribe(discordsrvListener);
        DiscordSRV.api.unsubscribe(discordsrvListenerCommands);
        DiscordSRV.api.unsubscribe(discordsrvListenerCommandLeaderboard);
        saveCacheToSQL();
    }
}
