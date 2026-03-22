package me.kyrobi.cynagenlevels;

import github.scarsz.discordsrv.DiscordSRV;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;

import java.io.File;
import java.sql.*;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static me.kyrobi.cynagenlevels.ChatHandler.getMinecraftUser;
import static me.kyrobi.cynagenlevels.Commands.CommandLeaderboard.getPlayerRank;

/*
Reference:
Amount of EXP to give: https://wiki.mee6.xyz/en/plugins/levels
Leveling formula: https://github.com/Mee6/Mee6-documentation/blob/master/docs/levels_xp.md
*/
public class LevelHandler {

    private final CynagenLevels plugin;
    public File folderDirectory;
    public static String url;

    static final int messageCooldownSeconds = 60;

    // Private — access through the controlled methods below
    private static final Map<String, Long[]> userCache = new HashMap<>();
    private static final Map<String, Long> usersOnCooldown = new HashMap<>();

    // Single shared connection, reopened if closed
    private static Connection sharedConnection;
    private static final Object dbLock = new Object();

    public LevelHandler(CynagenLevels plugin) {
        this.plugin = plugin;
        File dbFile = new File(plugin.getDataFolder(), "");
        folderDirectory = new File(dbFile.getPath());
        url = "jdbc:sqlite:" + folderDirectory + File.separator + "data.db";

        String createTableQuery = "CREATE TABLE IF NOT EXISTS PlayerData ("
                + "UUID TEXT PRIMARY KEY,"
                + "level INTEGER,"
                + "exp INTEGER"
                + ");";

        try {
            sharedConnection = DriverManager.getConnection(url);
            try (Statement stmt = sharedConnection.createStatement()) {
                stmt.execute(createTableQuery);
            }
        } catch (SQLException error) {
            plugin.getLogger().severe("Failed to initialize database: " + error.getMessage());
        }
    }

    private static Connection getConnection() throws SQLException {
        synchronized (dbLock) {
            if (sharedConnection == null || sharedConnection.isClosed()) {
                sharedConnection = DriverManager.getConnection(url);
            }
            return sharedConnection;
        }
    }

    static void putUserIntoCache(String minecraftUUID) {
        if (userCache.containsKey(minecraftUUID)) {
            return;
        }

        String query = "SELECT level, exp FROM PlayerData WHERE UUID = ?";
        synchronized (dbLock) {
            try (PreparedStatement ps = getConnection().prepareStatement(query)) {
                ps.setString(1, minecraftUUID);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        userCache.put(minecraftUUID, new Long[]{rs.getLong("level"), rs.getLong("exp")});
                    } else {
                        userCache.put(minecraftUUID, new Long[]{0L, 0L});
                    }
                }
            } catch (SQLException e) {
                Bukkit.getLogger().severe("putUserIntoCache error: " + e.getMessage());
            }
        }
    }

    public static long getCurrentLevel(String minecraftUUID) {
        if (!userCache.containsKey(minecraftUUID)) {
            putUserIntoCache(minecraftUUID);
        }
        return userCache.get(minecraftUUID)[0];
    }

    public static long getCurrentEXP(String minecraftUUID) {
        if (!userCache.containsKey(minecraftUUID)) {
            putUserIntoCache(minecraftUUID);
        }
        return userCache.get(minecraftUUID)[1];
    }

    static long getEXPNeededUntilNextLevel(long currentLevel, long currentEXP) {
        return 5 * (currentLevel * currentLevel) + (50 * currentLevel) + 100 - currentEXP;
    }

    // giveEXP now delegates to giveEXPAmount to avoid duplication
    static int giveEXP(String minecraftUUID) {
        return giveEXPAmount(minecraftUUID, getRandomEXPAmount());
    }

    static int giveEXPAmount(String minecraftUUID, long amount) {
        if (!userCache.containsKey(minecraftUUID)) {
            putUserIntoCache(minecraftUUID);
        }

        long currentLevel = getCurrentLevel(minecraftUUID);
        long currentEXP = getCurrentEXP(minecraftUUID);
        long expNeeded = getEXPNeededUntilNextLevel(currentLevel, currentEXP);

        long newLevel = currentLevel;
        long newEXP = currentEXP;
        int returnValue = 0;

        if (amount >= expNeeded) {
            newLevel++;
            newEXP = amount - expNeeded;
            returnValue = (int) newLevel;
        } else {
            newEXP += amount;
        }

        userCache.put(minecraftUUID, new Long[]{newLevel, newEXP});
        return returnValue;
    }

    public static void saveCacheToSQL() {
        Bukkit.getLogger().info("Saving level cache to database...");

        // Use a single connection and a batch for the entire cache flush
        final String UPSERT = "INSERT OR REPLACE INTO PlayerData (UUID, level, exp) VALUES (?, ?, ?)";
        synchronized (dbLock) {
            try {
                Connection conn = getConnection();
                conn.setAutoCommit(false);
                try (PreparedStatement ps = conn.prepareStatement(UPSERT)) {
                    for (Map.Entry<String, Long[]> entry : userCache.entrySet()) {
                        ps.setString(1, entry.getKey());
                        ps.setLong(2, entry.getValue()[0]);
                        ps.setLong(3, entry.getValue()[1]);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                    conn.commit();
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                Bukkit.getLogger().severe("saveCacheToSQL error: " + e.getMessage());
            }
        }
    }

    public static long getRandomEXPAmount() {
        return ThreadLocalRandom.current().nextLong(15, 26);
    }

    public static boolean isOnCooldown(String minecraftUUID) {
        long now = System.currentTimeMillis();
        Long previousTime = usersOnCooldown.get(minecraftUUID);

        if (previousTime == null) {
            usersOnCooldown.put(minecraftUUID, now);
            return false;
        }

        if ((now - previousTime) >= (messageCooldownSeconds * 1_000L)) {
            usersOnCooldown.put(minecraftUUID, now);
            return false;
        }

        return true;
    }

    // Called on player quit to prevent unbounded growth of usersOnCooldown
    public static void removeFromCooldown(String minecraftUUID) {
        usersOnCooldown.remove(minecraftUUID);
    }

    public static String getStatsForIngame(String minecraftUUID) {
        StringBuilder string = new StringBuilder();

        long currentEXP = getCurrentEXP(minecraftUUID);
        long totalEXPNeeded = currentEXP + getEXPNeededUntilNextLevel(getCurrentLevel(minecraftUUID), currentEXP);

        string.append(ChatColor.GRAY + "---------------\n");
        string.append(ChatColor.AQUA + "Name: " + ChatColor.WHITE + Bukkit.getOfflinePlayer(UUID.fromString(minecraftUUID)).getName() + "\n");
        string.append(ChatColor.AQUA + "Level: " + ChatColor.WHITE + getCurrentLevel(minecraftUUID) + "\n");
        string.append(ChatColor.AQUA + "Rank: " + ChatColor.WHITE + "#" + getPlayerRank(minecraftUUID) + "\n");
        string.append(ChatColor.AQUA + "Progress: " + ChatColor.WHITE + insertCommasIntoNumber(currentEXP)
                + ChatColor.GRAY + "/" + ChatColor.WHITE + insertCommasIntoNumber(totalEXPNeeded)
                + ChatColor.AQUA + "\n[" + ChatColor.WHITE + getEXPBar(minecraftUUID) + ChatColor.AQUA + "]\n");
        string.append(ChatColor.GRAY + "\nChatting will give you EXP\n");
        string.append(ChatColor.GRAY + "---------------");

        return string.toString();
    }

    public static String getStatsForDiscord(String userID) {
        OfflinePlayer player = getMinecraftUser(userID);
        if (player == null) {
            return "User does not exist";
        }

        String minecraftUUID = player.getUniqueId().toString();
        long currentEXP = getCurrentEXP(minecraftUUID);
        long totalEXPNeeded = currentEXP + getEXPNeededUntilNextLevel(getCurrentLevel(minecraftUUID), currentEXP);

        StringBuilder string = new StringBuilder();
        string.append("---------------\n");
        string.append("**Name**: " + DiscordSRV.getPlugin().getJda().getUserById(userID).getAsTag() + "\n");
        string.append("**Level**: " + getCurrentLevel(minecraftUUID) + "\n");
        string.append("**Rank**: #" + getPlayerRank(minecraftUUID) + "\n");
        string.append("**Progress**: " + insertCommasIntoNumber(currentEXP) + "/" + insertCommasIntoNumber(totalEXPNeeded)
                + "\n**[**" + getEXPBar(minecraftUUID) + "**]**\n");
        string.append("\n`Chatting will give you EXP.`\n");
        string.append("---------------");

        return string.toString();
    }

    public static String insertCommasIntoNumber(long number) {
        DecimalFormat formatter = (DecimalFormat) NumberFormat.getInstance(Locale.US);
        formatter.applyPattern("#,###");
        return formatter.format(number);
    }

    public static String getEXPBar(String minecraftUUID) {
        final int totalBarSize = 10;
        long currentEXP = getCurrentEXP(minecraftUUID);
        long currentLevel = getCurrentLevel(minecraftUUID);
        long maxEXP = currentEXP + getEXPNeededUntilNextLevel(currentLevel, currentEXP);

        double percentageCompleted = ((double) currentEXP / maxEXP) * 100;
        int completedValue = (int) Math.floor(percentageCompleted);

        StringBuilder progressBar = new StringBuilder();
        for (int i = 0; i < totalBarSize; i++) {
            if (completedValue / 10 >= i) {
                progressBar.append("\uD83D\uDFE9"); // filled
            } else {
                progressBar.append("⬜"); // empty
            }
        }

        return progressBar.toString();
    }

    /**
     * Sets a player's level and EXP absolutely.
     * Used by CommandAddPlayer to set a level without discarding existing EXP.
     */
    public static void setPlayerData(String minecraftUUID, long level, long exp) {
        userCache.put(minecraftUUID, new Long[]{level, exp});
    }

    public static Map<String, Long[]> getUserCache() {
        return Collections.unmodifiableMap(userCache);
    }
}