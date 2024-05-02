package me.kyrobi.cynagenlevels;

import com.earth2me.essentials.Essentials;
import com.gmail.nossr50.api.ChatAPI;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.ListenerPriority;
import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.api.events.DiscordGuildMessageReceivedEvent;
import github.scarsz.discordsrv.dependencies.jda.api.JDA;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Role;
import github.scarsz.discordsrv.dependencies.jda.api.entities.*;
import io.papermc.paper.event.player.AsyncChatEvent;

import net.ess3.api.MaxMoneyException;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import github.scarsz.discordsrv.dependencies.jda.api.entities.User;
import github.scarsz.discordsrv.util.DiscordUtil;
import org.bukkit.event.player.PlayerJoinEvent;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static me.kyrobi.cynagenlevels.LevelHandler.*;

public class ChatHandler implements Listener {

    CynagenLevels plugin;
    JDA jda;
    Guild guild;
    Essentials ess;

    private ArrayList<Pattern> filteredChat = new ArrayList<>();
    private ArrayList<String> whitelistedWords = new ArrayList<>();

    private HashMap<Integer, Role> levelRoles = new HashMap<>();

    private Set<String> recentlyJoined = new HashSet<>();
    private HashMap<String, Integer> timesSaidWelcome = new HashMap<>();

    final int timeToSayWelcome = 35; // 20 seconds
    final int expToGive = 600;
    final int moneyToGive = 150;

    public ChatHandler(CynagenLevels plugin){
        this.plugin = plugin;
        this.ess = (Essentials) Bukkit.getServer().getPluginManager().getPlugin("Essentials");
        this.jda = DiscordUtil.getJda();
        guild = jda.getGuildById("415873891857203212");

        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        filteredChat.add(Pattern.compile(
                "[a-zA-Z0-9\\-\\.\\*]+\\s?(\\.|\\*|dot|\\(dot\\)|-|\\(\\*\\)|;|:|,)\\s?(c(| +)o(| +)m|o(| +)r(| +)g|n(| +)e(| +)t|(?<! )c(| +)z|(?<! )c(| +)o|(?<! )u(| +)k|(?<! )s(| +)k|b(| +)i(| +)z|(?<! )m(| +)o(| +)b(| +)i|(?<! )x(| +)x(| +)x|(?<! )e(| +)u|(?<! )m(| +)e|(?<! )i(| +)o|(?<! )o(| +)n(| +)l(| +)i(| +)n(| +)e|(?<! )x(| +)y(| +)z|(?<! )f(| +)r|(?<! )b(| +)e|(?<! )d(| +)e|(?<! )c(| +)a|(?<! )a(| +)l|(?<! )a(| +)i|(?<! )d(| +)e(| +)v|(?<! )a(| +)p(| +)p|(?<! )i(| +)n|(?<! )i(| +)s|(?<! )g(| +)g|(?<! )t(| +)o|(?<! )p(| +)h|(?<! )n(| +)l|(?<! )i(| +)d|(?<! )i(| +)n(| +)c|(?<! )u(| +)s|(?<! )p(| +)w|(?<! )p(| +)r(| +)o|(?<! )t(| +)v|(?<! )c(| +)x|(?<! )m(| +)x|(?<! )f(| +)m|(?<! )c(| +)c|(?<! )v(| +)i(| +)p|(?<! )f(| +)u(| +)n|(?<! )i(| +)c(| +)u)\\b"
                , Pattern.CASE_INSENSITIVE));

        whitelistedWords.add("https://discordapp.com/invite/B5JW7qp");

        // levelRoles.put("VIP", guild.getRoleById(469284766240210944L) ); // VIP
        levelRoles.put(10, guild.getRoleById(750227892822212759L) ); // Level 10
        levelRoles.put(20, guild.getRoleById(750228204832292885L) ); // Level 20
        levelRoles.put(30, guild.getRoleById(750229923100229693L) ); // Level 30
        levelRoles.put(40, guild.getRoleById(750228369924423730L) ); // Level 40
        levelRoles.put(50, guild.getRoleById(750228746161618955L) ); // Level 50
        levelRoles.put(60, guild.getRoleById(750228484072407060L) ); // Level 60
        levelRoles.put(70, guild.getRoleById(750230251811897385L) ); // Level 70
        levelRoles.put(80, guild.getRoleById(750229654803185725L) ); // Level 80
        levelRoles.put(85, guild.getRoleById(750230843062222888L) ); // Level 85
        levelRoles.put(90, guild.getRoleById(750230496075579444L) ); // Level 90
        levelRoles.put(95, guild.getRoleById(750231302711672832L) ); // Level 95
        levelRoles.put(100, guild.getRoleById(750231871744376833L) ); // Level 100

        System.out.println("Guild: " + guild.getName());

    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();

        if(!player.hasPlayedBefore()){
            recentlyJoined.add(player.getName());

            // Remove that player from the list 10 seconds later
            Bukkit.getScheduler().runTaskLater(plugin, ()->{
                recentlyJoined.remove(player.getName());
            }, 20L * timeToSayWelcome);
        }
    }

    /*
    Listens to when a player chats ingame
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChatIngame(AsyncPlayerChatEvent e){

        if(e.isCancelled()){ return; }

//        if(!e.getPlayer().getName().equals("Kyrobi")){
//            return;
//        }

        Player player = e.getPlayer();

        // Don't process party chats
        if(ChatAPI.isUsingPartyChat(player)){
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            /*If the player is only on the server, then still allow them to gain exp*/
//            User user = getDiscordUser(player.getUniqueId());
//            if(user == null){ return;}

            String uuid = player.getUniqueId().toString();

            //Try to give EXP to people who say welcome
            if(isWelcome(e.getMessage())){
                if(shouldRewardWelcome(player.getName()) && !ChatAPI.isUsingPartyChat(e.getPlayer())){
                    giveEXPAmount(uuid, expToGive);
                    player.sendMessage(ChatColor.GREEN + "Welcoming new player reward: \n" + ChatColor.GOLD + "+" +expToGive + "Chat EXP " + ChatColor.GRAY  + "(/level) " + ChatColor.GREEN + "and" + ChatColor.GOLD + " $" + moneyToGive);
                    try {
                        ess.getUser(e.getPlayer()).giveMoney(new BigDecimal(moneyToGive));
                    } catch (MaxMoneyException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }

            // Don't do anything if on cooldown
            if(isOnCooldown(uuid)){
                return;
            }

            int levelUpFlag = giveEXP(uuid);
            if(levelUpFlag > 0){

                User discordUser = getDiscordUser(UUID.fromString(uuid));
                if(discordUser != null){
                    tryToGiveRankOnDiscord(discordUser, levelUpFlag);
                }

                // To prevent spam, only show in server chat if they reach lvl 10+
                if(levelUpFlag > 9){
                    Bukkit.broadcastMessage(ChatColor.DARK_AQUA + player.getName() + " advanced to chat level " + ChatColor.AQUA + levelUpFlag);
                    player.sendMessage(ChatColor.GRAY + "For more info, use /level");
                }
                else{
                    player.sendMessage(ChatColor.DARK_AQUA + player.getName() + " advanced to chat level " + ChatColor.AQUA + levelUpFlag);
                    player.sendMessage(ChatColor.GRAY + "For more info, use /level");
                }
            }

            long currentLevel = getCurrentLevel(uuid);
            long currentEXP = getCurrentEXP(uuid);
            long total = getEXPNeededUntilNextLevel(currentLevel, currentEXP) + currentEXP;


//            System.out.println("\n"+
//                    "Current Level: " + currentLevel + "\n"+
//                    "Current EXP: " + currentEXP + "\n"+
//                    "EXP Bar: " + "[ " + currentEXP + " / " + total + "] \n"
//            );

        });

    }

    /*
    Listens to when a player chats through Discord
     */
    @Subscribe(priority = ListenerPriority.MONITOR)
    public void discordMessageReceived(DiscordGuildMessageReceivedEvent e) {
        if(e.getChannel().getName().equals("spam")){ return; }
        // if(!e.getMember().getId().equals("559428414709301279")){ return; }

        // We will try to give the user the role upon a message send. If a role was sucessfully given,
        String minecraftUUID = String.valueOf(getMinecraftUser(e.getAuthor().getId()).getUniqueId());
        if(minecraftUUID == null){
            return;
        }
        int currentLevel = (int) getCurrentLevel(minecraftUUID);
        tryToGiveRankOnDiscord(e.getAuthor(), currentLevel);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            OfflinePlayer player = getMinecraftUser(e.getMember().getId());
            if(player == null){ return; }

            String uuid = String.valueOf(player.getUniqueId());

            //Try to give EXP to people who say welcome
            if(e.getChannel().getName().equals("server-chat")){
                if(isWelcome(e.getMessage().getContentRaw())){
                    OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer( DiscordSRV.getPlugin().getAccountLinkManager().getUuid(e.getMember().getId()));

                    System.out.println(DiscordSRV.getPlugin().getAccountLinkManager().getUuid(e.getMember().getId()).toString());

                    System.out.println("Offline player " + offlinePlayer.getName());
                    if(shouldRewardWelcome(offlinePlayer.getName())){
                        giveEXPAmount(uuid, expToGive);

                        // TextChannel txtChan = e.getGuild().getTextChannelById("562807748341923840");
                        e.getMessage().reply(e.getMember().getAsMention() + "\n**Welcoming new player reward:**\n" + "+" +expToGive + " Chat EXP " + "(`/level`) " + "and" + " $" + moneyToGive).queue();
                        // player.sendMessage(ChatColor.GREEN + "Welcoming new player reward: \n" + ChatColor.GOLD + "+" +amountToGive + "Chat EXP " + ChatColor.GRAY  + "(/level) " + ChatColor.GREEN + "and" + ChatColor.GOLD + " $100");
                        try {
                            ess.getUser(offlinePlayer.getPlayer()).giveMoney(new BigDecimal(moneyToGive));
                        } catch (MaxMoneyException ex) {
                            throw new RuntimeException(ex);
                        }
                    }
                }
            }

            // Don't do anything if on cooldown
            if(isOnCooldown(uuid)){
                return;
            }


            User discordUser = getDiscordUser(UUID.fromString(uuid));
            int levelUpFlag = giveEXP(player.getUniqueId().toString());
            if(levelUpFlag > 0){
                TextChannel txt = DiscordSRV.getPlugin().getJda().getTextChannelById("448488708883218442");
                txt.sendMessage( discordUser.getAsMention() + " advanced to chat level " + levelUpFlag).queue();
                tryToGiveRankOnDiscord(discordUser, levelUpFlag);
            }

//            long currentLevel = getCurrentLevel(uuid);
//            long currentEXP = getCurrentEXP(uuid);
//            long total = getEXPNeededUntilNextLevel(currentLevel, currentEXP) + currentEXP;


//            System.out.println("\n"+
//                    "Current Level: " + currentLevel + "\n"+
//                    "Current EXP: " + currentEXP + "\n"+
//                    "EXP Bar: " + "[ " + currentEXP + " / " + total + "] \n"
//            );

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

    private int tryToGiveRankOnDiscord(User discordUser, int newLevel){
        Member member = guild.getMemberById(discordUser.getId());

        if(member == null){
            return -1;
        }

        List<Role> rolesOfUser = member.getRoles();

        // Level 100
        if(newLevel >= 100){
            if(!rolesOfUser.contains(levelRoles.get(100))){
                removeRoles(discordUser);
                guild.addRoleToMember(member, levelRoles.get(100)).queueAfter(200, TimeUnit.MILLISECONDS);
                return 100;
            }
        }

        else if(newLevel >= 95){
            if(!rolesOfUser.contains(levelRoles.get(95))){
                removeRoles(discordUser);
                guild.addRoleToMember(member, levelRoles.get(95)).queueAfter(200, TimeUnit.MILLISECONDS);
                return 95;
            }
        }

        else if(newLevel >= 90){
            if(!rolesOfUser.contains(levelRoles.get(90))){
                removeRoles(discordUser);
                guild.addRoleToMember(member, levelRoles.get(90)).queueAfter(200, TimeUnit.MILLISECONDS);
                return 90;
            }
        }

        else if(newLevel >= 85){
            if(!rolesOfUser.contains(levelRoles.get(85))){
                removeRoles(discordUser);
                guild.addRoleToMember(member, levelRoles.get(85)).queueAfter(200, TimeUnit.MILLISECONDS);
                return 85;
            }
        }

        else if(newLevel >= 80){
            if(!rolesOfUser.contains(levelRoles.get(80))){
                removeRoles(discordUser);
                guild.addRoleToMember(member, levelRoles.get(80)).queueAfter(200, TimeUnit.MILLISECONDS);
                return 80;
            }
        }

        else if(newLevel >= 70){
            if(!rolesOfUser.contains(levelRoles.get(70))){
                removeRoles(discordUser);
                guild.addRoleToMember(member, levelRoles.get(70)).queueAfter(200, TimeUnit.MILLISECONDS);
                return 70;
            }
        }

        else if(newLevel >= 60){
            if(!rolesOfUser.contains(levelRoles.get(60))){
                removeRoles(discordUser);
                guild.addRoleToMember(member, levelRoles.get(60)).queueAfter(200, TimeUnit.MILLISECONDS);
                return 60;
            }
        }

        else if(newLevel >= 50){
            if(!rolesOfUser.contains(levelRoles.get(50))){
                removeRoles(discordUser);
                guild.addRoleToMember(member, levelRoles.get(50)).queueAfter(200, TimeUnit.MILLISECONDS);
                return 50;
            }
        }

        else if(newLevel >= 40){
            if(!rolesOfUser.contains(levelRoles.get(40))){
                removeRoles(discordUser);
                guild.addRoleToMember(member, levelRoles.get(40)).queueAfter(200, TimeUnit.MILLISECONDS);
                return 40;
            }
        }

        else if(newLevel >= 30){
            if(!rolesOfUser.contains(levelRoles.get(30))){
                removeRoles(discordUser);
                guild.addRoleToMember(member, levelRoles.get(30)).queueAfter(200, TimeUnit.MILLISECONDS);
                return 30;
            }
        }

        else if(newLevel >= 20){
            if(!rolesOfUser.contains(levelRoles.get(20))){
                removeRoles(discordUser);
                guild.addRoleToMember(member, levelRoles.get(20)).queueAfter(200, TimeUnit.MILLISECONDS);
                return 20;
            }
        }

        else if(newLevel >= 10){
            if(!rolesOfUser.contains(levelRoles.get(10))){
                removeRoles(discordUser);
                guild.addRoleToMember(member, levelRoles.get(10)).queueAfter(200, TimeUnit.MILLISECONDS);
                return 10;
            }
        }

        return -1;
    }

    // Checks to see if EXP should be rewarded for players that say welcome to new joiners
    private boolean shouldRewardWelcome(String name){
        if(!recentlyJoined.isEmpty()){
            int timesSaid = timesSaidWelcome.getOrDefault(name, 0);

            if(timesSaid < recentlyJoined.size()){

                timesSaid++;
                timesSaidWelcome.put(name, timesSaid);

                Bukkit.getScheduler().runTaskLater(plugin, ()->{
                    int timeSaidDecay = timesSaidWelcome.get(name) - 1;
                    timesSaidWelcome.put(name, timeSaidDecay);
                }, 20L * (timeToSayWelcome + 1));

                return true;
            }

            return false;
        }

        return false;
    }

    private void removeRoles(User discordUser){
        Member member = guild.getMemberById(discordUser.getId());
        List<Role> userRoles = member.getRoles();

        for(Role r: userRoles){
            if(levelRoles.containsValue(r)){
                guild.removeRoleFromMember(member, r).queue();
            }
        }
    }

    public static boolean isWelcome(String str) {
        // Regex pattern to match "welcome" at the beginning of the string,
        // followed by optional punctuation and whitespace, ignoring case
        String regex = "(?i)^welcome[!?,\\s]*.*";

        // Compile the pattern
        Pattern pattern = Pattern.compile(regex);

        // Match the pattern against the input string
        Matcher matcher = pattern.matcher(str);

        // Check if the pattern is found in the string
        return matcher.matches();
    }


}
