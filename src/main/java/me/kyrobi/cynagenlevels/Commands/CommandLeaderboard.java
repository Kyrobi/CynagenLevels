package me.kyrobi.cynagenlevels.Commands;

import github.scarsz.discordsrv.api.ListenerPriority;
import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.api.events.DiscordGuildMessageReceivedEvent;
import me.kyrobi.cynagenlevels.CynagenLevels;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.*;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static me.kyrobi.cynagenlevels.LevelHandler.*;

public class CommandLeaderboard implements CommandExecutor {

    CynagenLevels plugin;

    // Volatile list reference — swap the whole list atomically instead of clearing and re-adding
    private static volatile ArrayList<String[]> leaderboard = new ArrayList<>();

    // AtomicLong so the staleness check is thread-safe without a full synchronized block
    private static final AtomicLong lastLeaderboardUpdate = new AtomicLong(0);
    static final int leaderboardRefreshDelayInSeconds = 60 * 5;

    // Lock to prevent two concurrent refreshes from running simultaneously
    private static final Object leaderboardLock = new Object();

    public CommandLeaderboard(CynagenLevels plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String label, String[] args) {
        if (args.length > 1) {
            commandSender.sendMessage(ChatColor.RED + "Usage: /leveltop [page]");
            return false;
        }

        new BukkitRunnable() {
            public void run() {
                refreshIfStale();
                commandSender.sendMessage(buildPage(args.length == 1 ? args[0] : null, commandSender));
            }
        }.runTaskAsynchronously(plugin);

        return true;
    }

    @Subscribe(priority = ListenerPriority.MONITOR)
    public void discordMessageReceived(DiscordGuildMessageReceivedEvent e) {
        if (e.getAuthor().isBot()) return;

        String[] args = e.getMessage().getContentRaw().split(" ");
        if (!args[0].equals("!leveltop")) return;

        if (args.length > 2) {
            e.getMessage().reply("Usage: !leveltop [page]").queue();
            return;
        }

        new BukkitRunnable() {
            public void run() {
                refreshIfStale();

                int namesPerPage = 25;
                int requestedPage = 1;
                ArrayList<String[]> snap = leaderboard;
                int maxPagePossible = (int) Math.ceil((float) snap.size() / (float) namesPerPage);

                if (args.length == 2) {
                    try {
                        requestedPage = Integer.parseInt(args[1]);
                        if (requestedPage < 1) requestedPage = 1;
                        if (requestedPage > maxPagePossible) {
                            e.getMessage().reply("There are only " + maxPagePossible + " pages available").queue();
                            return;
                        }
                    } catch (NumberFormatException nfe) {
                        e.getMessage().reply(args[1] + " is an invalid page number").queue();
                        return;
                    }
                }

                StringBuilder sb = new StringBuilder("Level Leaderboard\n---------------\n");
                String footer = "---------------\nPage (" + requestedPage + "/" + maxPagePossible + ")";

                int start = (requestedPage - 1) * namesPerPage;
                int end   = Math.min(start + namesPerPage, snap.size());
                for (int i = start; i < end; i++) {
                    String[] stats = snap.get(i);
                    sb.append("**").append(stats[0]).append("**. `")
                            .append(stats[1]).append("` **Level**: ").append(stats[2]).append("\n");
                }
                sb.append(footer);
                e.getMessage().reply(sb.toString()).queue();
            }
        }.runTaskAsynchronously(plugin);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static void refreshIfStale() {
        long now = System.currentTimeMillis();
        if (now - lastLeaderboardUpdate.get() < leaderboardRefreshDelayInSeconds * 1000L) return;

        synchronized (leaderboardLock) {
            // Double-checked locking — re-test inside the lock in case another thread just refreshed
            if (now - lastLeaderboardUpdate.get() < leaderboardRefreshDelayInSeconds * 1000L) return;

            loadLeaderboard();
            lastLeaderboardUpdate.set(System.currentTimeMillis());
        }
    }

    /**
     * Builds the leaderboard page string for in-game use.
     * pageArg may be null (defaults to page 1).
     */
    private static String buildPage(String pageArg, CommandSender sender) {
        int namesPerPage = 25;
        int requestedPage = 1;
        ArrayList<String[]> snap = leaderboard; // local ref — thread-safe snapshot
        int maxPagePossible = (int) Math.ceil((float) snap.size() / (float) namesPerPage);

        if (pageArg != null) {
            try {
                requestedPage = Integer.parseInt(pageArg);
                if (requestedPage < 1) requestedPage = 1;
                if (requestedPage > maxPagePossible) {
                    return ChatColor.RED + "There are only " + maxPagePossible + " pages available";
                }
            } catch (NumberFormatException e) {
                return ChatColor.RED + pageArg + " is an invalid page number";
            }
        }

        StringBuilder sb = new StringBuilder(
                ChatColor.DARK_AQUA + "Level Leaderboard\n" + ChatColor.GRAY + "---------------\n");
        String footer = ChatColor.GRAY + "---------------\n" + ChatColor.DARK_AQUA + "Page "
                + ChatColor.GRAY + "(" + ChatColor.AQUA + requestedPage
                + ChatColor.GRAY + "/" + ChatColor.AQUA + maxPagePossible + ChatColor.GRAY + ")";

        // Use explicit bounds instead of catching IndexOutOfBoundsException
        int start = (requestedPage - 1) * namesPerPage;
        int end   = Math.min(start + namesPerPage, snap.size());
        for (int i = start; i < end; i++) {
            String[] stats = snap.get(i);
            sb.append(ChatColor.AQUA).append(stats[0]).append(". ")
                    .append(ChatColor.WHITE).append(stats[1])
                    .append(ChatColor.DARK_AQUA).append(" Level: ")
                    .append(ChatColor.AQUA).append(stats[2]).append("\n");
        }
        sb.append(footer);
        return sb.toString();
    }

    private static void loadLeaderboard() {
        saveCacheToSQL();

        final String LEADERBOARD_QUERY =
                "SELECT UUID, level, EXP FROM PlayerData ORDER BY level DESC, EXP DESC";

        ArrayList<String[]> fresh = new ArrayList<>();

        try (Connection connection = DriverManager.getConnection(url);
             PreparedStatement ps = connection.prepareStatement(LEADERBOARD_QUERY);
             ResultSet rs = ps.executeQuery()) {

            int rank = 1;
            while (rs.next()) {
                String uuid  = rs.getString("UUID");
                String level = rs.getString("level");
                String name  = Bukkit.getOfflinePlayer(UUID.fromString(uuid)).getName();
                fresh.add(new String[]{String.valueOf(rank), name, level});
                rank++;
            }

        } catch (SQLException e) {
            Bukkit.getLogger().severe("loadLeaderboard error: " + e.getMessage());
        }

        // Swap the whole list atomically — no window where it's half-cleared
        leaderboard = fresh;
    }

    public static int getPlayerRank(String minecraftUUID) {
        saveCacheToSQL();
        final String PLAYER_QUERY =
                "SELECT rank FROM (SELECT RANK() OVER (ORDER BY level DESC, EXP DESC) as rank, UUID FROM PlayerData) WHERE UUID = ?";

        try (Connection connection = DriverManager.getConnection(url);
             PreparedStatement ps = connection.prepareStatement(PLAYER_QUERY)) {

            ps.setString(1, minecraftUUID);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("rank");
            }

        } catch (SQLException e) {
            Bukkit.getLogger().severe("getPlayerRank error: " + e.getMessage());
        }

        return -1;
    }
}