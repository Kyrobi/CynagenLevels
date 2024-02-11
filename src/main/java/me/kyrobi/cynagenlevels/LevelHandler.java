package me.kyrobi.cynagenlevels;

import github.scarsz.discordsrv.DiscordSRV;
import it.unimi.dsi.fastutil.Hash;
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


/*
Reference:
Amount of EXP to give: https://wiki.mee6.xyz/en/plugins/levels
Leveling formula: https://github.com/Mee6/Mee6-documentation/blob/master/docs/levels_xp.md

 */
public class LevelHandler {

    CynagenLevels plugin;
    File dbFile = new File("");
    public File folderDirectory;
    static String url;

    final static int messageCooldownSeconds = 60;

    public static HashMap<String, Long[]> userCache = new HashMap<>();
    static HashMap<String, Long> usersOnCooldown = new HashMap<>();

    public LevelHandler(CynagenLevels plugin){
        this.plugin = plugin;
        folderDirectory = new File(dbFile.getAbsolutePath() + File.separator + "plugins" + File.separator + "CynagenLevels");
        url = "jdbc:sqlite:" + folderDirectory + File.separator +"data.db";

        String createTableQuery = "CREATE TABLE IF NOT EXISTS PlayerData ("
                + "UUID TEXT PRIMARY KEY,"
                + "level INTEGER,"
                + "exp INTEGER"
                + ");";

        try(Connection conn = DriverManager.getConnection(url)){
            Class.forName("org.sqlite.JDBC");
            Statement stmt = conn.createStatement(); // Formulate the command to execute
            stmt.execute(createTableQuery);  //Execute said command
        }
        catch (SQLException | ClassNotFoundException error){
            Bukkit.getLogger().info(error.getMessage());
        }
    }

    static void putUserIntoCache(String minecraftUUID){
        if(userCache.containsKey(minecraftUUID)){
            return;
        }

        Long[] data = new Long[2];

        // SQL query to fetch the level and EXP for the given UUID
        String query = "SELECT level, exp FROM PlayerData WHERE UUID = ?";

        try (Connection connection = DriverManager.getConnection(url);
             PreparedStatement preparedStatement = connection.prepareStatement(query)) {

            preparedStatement.setString(1, minecraftUUID);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    long level = resultSet.getLong("level");
                    long exp = resultSet.getLong("exp");
                    data[0] = level;
                    data[1] = exp;
                }
                else{
                    data[0] = 0L;
                    data[1] = 0L;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace(); // Handle or log the exception
        }

        userCache.put(minecraftUUID, data);
    }

    static long getCurrentLevel(String minecraftUUID){
        if(!userCache.containsKey(minecraftUUID)){
            putUserIntoCache(minecraftUUID);
        }
        return userCache.get(minecraftUUID)[0];
    }

    static long getCurrentEXP(String minecraftUUID){
        if(!userCache.containsKey(minecraftUUID)){
            putUserIntoCache(minecraftUUID);
        }
        return userCache.get(minecraftUUID)[1];
    }

    static long getEXPNeededUntilNextLevel(long currentLevel, long currentEXP){
        return 5 * (currentLevel * currentLevel) + (50 * currentLevel) + 100 - currentEXP;
    }

    static void giveEXP(String minecraftUUID){
        if(!userCache.containsKey(String.valueOf(minecraftUUID))){
            putUserIntoCache(String.valueOf(minecraftUUID));
        }

        long expToGive = getRandomEXPAmount();

        long currentLevel = getCurrentLevel(minecraftUUID);
        long currentEXP = getCurrentEXP(minecraftUUID);
        long EXPNeededUntilNextLevel = getEXPNeededUntilNextLevel(currentLevel, currentEXP);


        long newLevel = currentLevel;
        long newCurrentEXP = currentEXP;

        if(expToGive >= EXPNeededUntilNextLevel){
            newLevel++;
            newCurrentEXP = expToGive - EXPNeededUntilNextLevel;
        }
        else {
            newCurrentEXP += expToGive;
        }

        userCache.put(minecraftUUID, new Long[]{newLevel, newCurrentEXP});

        // Update the level logic
    }

    public static void saveCacheToSQL(){
        System.out.println("Trying to save cache");
        for (Map.Entry<String, Long[]> entry : userCache.entrySet()) {
            String minecraftUUID = entry.getKey();
            Long[] userData = entry.getValue();
            long level = userData[0];
            long exp = userData[1];

            System.out.println("UUID: " + minecraftUUID + " level: " + level + " exp: " + exp);
            writeToSQL(minecraftUUID, level, exp);
        }
    }

    private static void writeToSQL(String minecraftUUID, long level, long exp){
        final String UPDATE_USER_QUERY = "INSERT OR REPLACE INTO PlayerData (UUID, level, exp) VALUES (?, ?, ?)";
        try (Connection connection = DriverManager.getConnection(url);
             PreparedStatement preparedStatement = connection.prepareStatement(UPDATE_USER_QUERY)) {

            // Set parameters for the query
            preparedStatement.setString(1, minecraftUUID);
            preparedStatement.setLong(2, level);
            preparedStatement.setLong(3, exp);

            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace(); // Handle or log the exception
        }
    }


    public static long getRandomEXPAmount(){
//        Random random = new Random();
//        long range = max - min + 1;
//        // Generate a random long within the full range of long values
//        long value = random.nextLong();
//        // Adjust the generated value to fit within the specified range
//        value = Math.floorMod(value, range) + min;
        long value = ThreadLocalRandom.current().nextLong(15, 25 + 1);
        return value;
    }

    public static boolean isOnCooldown(String minecraftUUID){
        if(!usersOnCooldown.containsKey(minecraftUUID)){
            usersOnCooldown.put(minecraftUUID, System.currentTimeMillis());
            return false;
        }

        long previousTime = usersOnCooldown.get(minecraftUUID);
        long currentTime = System.currentTimeMillis();

        // If X seconds has passed
        if((currentTime - previousTime) >= (messageCooldownSeconds * 1_000)){
            usersOnCooldown.put(minecraftUUID, System.currentTimeMillis());
            return false;
        }

        return true;
    }


    public static String getStatsForIngame(String minecraftUUID){
        StringBuilder string = new StringBuilder();

        long currentEXP = getCurrentEXP(minecraftUUID);
        long totalEXPNeeded = currentEXP + getEXPNeededUntilNextLevel(getCurrentLevel(minecraftUUID), currentEXP);

        string.append(ChatColor.GRAY + "---------------\n");

        string.append(ChatColor.AQUA + "Name: " + ChatColor.WHITE + Bukkit.getOfflinePlayer(UUID.fromString(minecraftUUID)).getName() + "\n");
        string.append(ChatColor.AQUA + "Level: " + ChatColor.WHITE + getCurrentLevel(minecraftUUID) + "\n");
        string.append(ChatColor.AQUA + "Progress: " + ChatColor.WHITE + insertCommasIntoNumber(currentEXP)  + ChatColor.GRAY + "/" + ChatColor.WHITE + insertCommasIntoNumber(totalEXPNeeded) + "\n");

        string.append(ChatColor.GRAY + "\n" +"Chatting will give you EXP." + "\n");
        string.append(ChatColor.GRAY + "If you have your Discord account linked, you" + "\n");
        string.append(ChatColor.GRAY + "will also get EXP from chatting on Discord." + "\n");

        string.append(ChatColor.GRAY + "---------------");

        return string.toString();
    }

    public static String getStatsForDiscord(String userID){
        StringBuilder string = new StringBuilder();

        OfflinePlayer player = getMinecraftUser(userID);
        if(player == null){
            return "User does not exist";
        }

        String minecraftUUID = player.getUniqueId().toString();

        long currentEXP = getCurrentEXP(minecraftUUID);
        long totalEXPNeeded = currentEXP + getEXPNeededUntilNextLevel(getCurrentLevel(minecraftUUID), currentEXP);

        string.append("---------------\n");

        string.append("Name: " + DiscordSRV.getPlugin().getJda().getUserById(userID).getAsTag() + "\n");
        string.append("Level: " + getCurrentLevel(minecraftUUID) + "\n");
        string.append("Progress: " + insertCommasIntoNumber(getCurrentEXP(minecraftUUID))  + "/" + insertCommasIntoNumber(totalEXPNeeded) + "\n");

        string.append("\n`Chatting will give you EXP.`" + "\n");
        string.append("`If you have your Discord account linked, you`" + "\n");
        string.append("`will also get EXP from chatting in-game.`" + "\n");

        string.append( "---------------");

        return string.toString();
    }

    public static String insertCommasIntoNumber(long number) {
        DecimalFormat formatter = (DecimalFormat) NumberFormat.getInstance(Locale.US);
        formatter.applyPattern("#,###");
        return formatter.format(number);
    }

    public static boolean isLong(String input) {
        return input.matches("^\\d+$");
    }

    public static boolean isUUID(String input) {
        return input.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    }
}
