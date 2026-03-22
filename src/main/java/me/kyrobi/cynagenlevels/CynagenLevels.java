package me.kyrobi.cynagenlevels;

import github.scarsz.discordsrv.DiscordSRV;
import me.kyrobi.cynagenlevels.Commands.CommandAddPlayer;
import me.kyrobi.cynagenlevels.Commands.CommandLeaderboard;
import me.kyrobi.cynagenlevels.Commands.CommandLevel;
import me.kyrobi.cynagenlevels.Commands.CommandLevelDiscord;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import static me.kyrobi.cynagenlevels.LevelHandler.saveCacheToSQL;

public final class CynagenLevels extends JavaPlugin {

    private ChatHandler discordsrvListener;
    private CommandLevelDiscord discordsrvListenerCommands;
    private CommandLeaderboard discordsrvListenerCommandLeaderboard;

    @Override
    public void onEnable() {
        new LevelHandler(this);

        // Delay registration until DiscordSRV is fully initialised
        Bukkit.getScheduler().runTaskLater(this, () -> {

            discordsrvListener = new ChatHandler(this);
            DiscordSRV.api.subscribe(discordsrvListener);

            discordsrvListenerCommands = new CommandLevelDiscord();
            DiscordSRV.api.subscribe(discordsrvListenerCommands);

            discordsrvListenerCommandLeaderboard = new CommandLeaderboard(this);
            DiscordSRV.api.subscribe(discordsrvListenerCommandLeaderboard);

            this.getCommand("addplayer").setExecutor(new CommandAddPlayer(this));
            this.getCommand("level").setExecutor(new CommandLevel(this));
            this.getCommand("leveltop").setExecutor(discordsrvListenerCommandLeaderboard);

        }, 20L * 10);
    }

    @Override
    public void onDisable() {
        // Guard against the server stopping before the delayed startup task fires
        if (discordsrvListener != null) {
            DiscordSRV.api.unsubscribe(discordsrvListener);
        }
        if (discordsrvListenerCommands != null) {
            DiscordSRV.api.unsubscribe(discordsrvListenerCommands);
        }
        if (discordsrvListenerCommandLeaderboard != null) {
            DiscordSRV.api.unsubscribe(discordsrvListenerCommandLeaderboard);
        }

        saveCacheToSQL();
    }
}