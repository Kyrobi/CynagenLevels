package me.kyrobi.cynagenlevels.Commands;

import github.scarsz.discordsrv.api.ListenerPriority;
import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.api.events.DiscordGuildMessageReceivedEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import me.kyrobi.cynagenlevels.CynagenLevels;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.*;
import java.util.ArrayList;
import java.util.UUID;

import static me.kyrobi.cynagenlevels.LevelHandler.*;

public class CommandLeaderboard implements CommandExecutor {

    CynagenLevels plugin;
    static ArrayList<String[]> leaderboard = new ArrayList<>();
    static long lastLeaderboardUpdate = 0;
    static final int leaderboardRefreshDelayInSeconds = 60 * 5;

    public CommandLeaderboard(CynagenLevels plugin){
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String label, String[] args){

        if(args.length > 1){
            commandSender.sendMessage(ChatColor.RED + "Usage: /leveltop [page]");
            return false;
        }


        new BukkitRunnable(){
            public void run(){

                if((System.currentTimeMillis() - lastLeaderboardUpdate) >= (leaderboardRefreshDelayInSeconds * 1000)){
                    loadLeaderboard();
                    lastLeaderboardUpdate = System.currentTimeMillis();
                }

                StringBuilder leaderboardBuilder = new StringBuilder();

                int namesPerPage = 25;
                int requestedPage = 1;
                int maxPagePossible;

                maxPagePossible = (int) Math.ceil((float)leaderboard.size() / (float)namesPerPage);

                if(args.length == 1){
                    try{
                        requestedPage = Integer.parseInt(args[0]);
                        if(requestedPage < 1){
                            requestedPage = 1;
                        }

                        if(requestedPage > maxPagePossible){
                            commandSender.sendMessage(ChatColor.RED + "There are only " + maxPagePossible + " pages available");
                            return;
                        }

                    } catch (NumberFormatException e){
                        commandSender.sendMessage(ChatColor.RED + args[0] + " is an invalid page number");
                        return;
                    }
                }

                leaderboardBuilder.append(ChatColor.DARK_AQUA + "Level Leaderboard" + "\n" + ChatColor.GRAY + "---------------" + "\n");

                String leaderboardFooter = ChatColor.GRAY + "---------------" + "\n" + ChatColor.DARK_AQUA + "Page " + ChatColor.GRAY + "(" +
                        ChatColor.AQUA + requestedPage + ChatColor.GRAY + "/" + ChatColor.AQUA + maxPagePossible + ChatColor.GRAY + ")";
                try{
                    for(int i = ((requestedPage - 1) * namesPerPage); i < namesPerPage * requestedPage; i++){
                        String[] stats = leaderboard.get(i);

                        String rank = stats[0];
                        String name = stats[1];
                        String level = stats[2];

                        leaderboardBuilder.append(ChatColor.AQUA + rank + ". " + ChatColor.WHITE + name + ChatColor.DARK_AQUA + " Level: " +  ChatColor.AQUA + level + "\n");
                    }

                    leaderboardBuilder.append(
                            leaderboardFooter);
                    commandSender.sendMessage(leaderboardBuilder.toString());
                } catch (IndexOutOfBoundsException e){
                    leaderboardBuilder.append(
                            leaderboardFooter);
                    commandSender.sendMessage(leaderboardBuilder.toString());
                }

            }
        }.runTaskAsynchronously(plugin);

        return true;

    }

    @Subscribe(priority = ListenerPriority.MONITOR)
    public void discordMessageReceived(DiscordGuildMessageReceivedEvent e) {
        if(e.getAuthor().isBot()){ return; }


        String[] args = e.getMessage().getContentRaw().split(" ");

        if(!args[0].equals("!leveltop")){
            return;
        }

        if(args.length > 2){
            e.getMessage().reply("Usage: !leveltop [page]").queue();
            return;
        }


        new BukkitRunnable(){
            public void run(){

                if((System.currentTimeMillis() - lastLeaderboardUpdate) >= (leaderboardRefreshDelayInSeconds * 1000)){
                    loadLeaderboard();
                    lastLeaderboardUpdate = System.currentTimeMillis();
                }

                StringBuilder leaderboardBuilder = new StringBuilder();

                int namesPerPage = 25;
                int requestedPage = 1;
                int maxPagePossible;

                maxPagePossible = (int) Math.ceil((float)leaderboard.size() / (float)namesPerPage);

                if(args.length == 2){
                    try{
                        requestedPage = Integer.parseInt(args[1]);
                        if(requestedPage < 1){
                            requestedPage = 1;
                        }

                        if(requestedPage > maxPagePossible){
                            e.getMessage().reply("There are only " + maxPagePossible + " pages available").queue();
                            return;
                        }

                    } catch (NumberFormatException nfe){
                        e.getMessage().reply(args[1] + " is an invalid page number").queue();
                        return;
                    }
                }

                leaderboardBuilder.append("Level Leaderboard" + "\n" + "---------------" + "\n");

                String leaderboardFooter = "---------------" + "\n" + "Page " + "(" +
                        requestedPage + "/" + maxPagePossible + ")";
                try{
                    for(int i = ((requestedPage - 1) * namesPerPage); i < namesPerPage * requestedPage; i++){
                        String[] stats = leaderboard.get(i);

                        String rank = stats[0];
                        String name = stats[1];
                        String level = stats[2];

                        leaderboardBuilder.append("**" + rank + "**. `" + name + "` **Level**: " +  level + "\n");
                    }

                    leaderboardBuilder.append(
                            leaderboardFooter);
                    e.getMessage().reply(leaderboardBuilder.toString()).queue();
                } catch (IndexOutOfBoundsException ioob){
                    leaderboardBuilder.append(
                            leaderboardFooter);
                    e.getMessage().reply(leaderboardBuilder.toString()).queue();
                }

            }
        }.runTaskAsynchronously(plugin);
    }

    private static void loadLeaderboard(){
        saveCacheToSQL();
        leaderboard.clear();
        // final String LEADERBOARD_QUERY = "SELECT RANK() OVER (ORDER BY level DESC, EXP DESC) as rank, UUID, level FROM PlayerData ORDER BY level DESC, EXP DESC";
        final String LEADERBOARD_QUERY = "SELECT UUID, level, EXP FROM PlayerData ORDER BY level DESC, EXP DESC";

        try (Connection connection = DriverManager.getConnection(url)) {

            PreparedStatement getAmount = connection.prepareStatement(LEADERBOARD_QUERY);

            ResultSet rs = getAmount.executeQuery();

            int rank = 1;
            while (rs.next()) {
                String uuid = rs.getString("UUID");
                String level = rs.getString("level");

                String playerName = Bukkit.getOfflinePlayer(UUID.fromString(uuid)).getName();
                String[] userData = {String.valueOf(rank), playerName, level};

                // Bukkit.getLogger().info("Rank: " + rank + " " + playerName + " Level: " + level);
                leaderboard.add(userData);
                rank++;
            }

        } catch (SQLException e) {
            e.printStackTrace(); // Handle or log the exception
        }
    }

    public static int getPlayerRank(String minecraftUUID){
        saveCacheToSQL();
        // final String LEADERBOARD_QUERY = "SELECT RANK() OVER (ORDER BY level DESC, EXP DESC) as rank, UUID, level FROM PlayerData ORDER BY level DESC, EXP DESC";
        final String PLAYER_QUERY = "SELECT rank FROM (SELECT RANK() OVER (ORDER BY level DESC, EXP DESC) as rank, UUID FROM PlayerData) WHERE UUID = ?";
        int playerRank = -1;

        try (Connection connection = DriverManager.getConnection(url)) {

            PreparedStatement getAmount = connection.prepareStatement(PLAYER_QUERY);

            getAmount.setString(1, minecraftUUID);

            ResultSet rs = getAmount.executeQuery();

            if (rs.next()) {
                playerRank = rs.getInt("rank");
            }

            rs.close();

        } catch (SQLException e) {
            e.printStackTrace(); // Handle or log the exception
        }

        return playerRank;
    }
}
