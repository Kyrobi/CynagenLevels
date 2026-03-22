package me.kyrobi.cynagenlevels;

import com.earth2me.essentials.Essentials;
import com.gmail.nossr50.api.ChatAPI;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.util.player.UserManager;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.ListenerPriority;
import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.api.events.DiscordGuildMessageReceivedEvent;
import github.scarsz.discordsrv.dependencies.jda.api.JDA;
import github.scarsz.discordsrv.dependencies.jda.api.entities.*;
import github.scarsz.discordsrv.util.DiscordUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import javax.annotation.Nullable;
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

    private final ArrayList<Pattern> filteredChat = new ArrayList<>();
    private final ArrayList<String> whitelistedWords = new ArrayList<>();
    private final HashMap<Integer, Role> levelRoles = new HashMap<>();

    // Use UUID instead of player name to survive name changes, and to allow quit cleanup
    private final Set<UUID> recentlyJoined = new HashSet<>();
    private final HashMap<UUID, Integer> timesSaidWelcome = new HashMap<>();

    final int timeToSayWelcome = 35;
    final int expToGive = 150;

    public ChatHandler(CynagenLevels plugin) {
        this.plugin = plugin;
        this.ess = (Essentials) Bukkit.getServer().getPluginManager().getPlugin("Essentials");
        this.jda = DiscordUtil.getJda();
        guild = jda.getGuildById("415873891857203212");

        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        filteredChat.add(Pattern.compile(
                "[a-zA-Z0-9\\-\\.\\*]+\\s?(\\.|\\*|dot|\\(dot\\)|-|\\(\\*\\)|;|:|,)\\s?(c(| +)o(| +)m|o(| +)r(| +)g|n(| +)e(| +)t|(?<! )c(| +)z|(?<! )c(| +)o|(?<! )u(| +)k|(?<! )s(| +)k|b(| +)i(| +)z|(?<! )m(| +)o(| +)b(| +)i|(?<! )x(| +)x(| +)x|(?<! )e(| +)u|(?<! )m(| +)e|(?<! )i(| +)o|(?<! )o(| +)n(| +)l(| +)i(| +)n(| +)e|(?<! )x(| +)y(| +)z|(?<! )f(| +)r|(?<! )b(| +)e|(?<! )d(| +)e|(?<! )c(| +)a|(?<! )a(| +)l|(?<! )a(| +)i|(?<! )d(| +)e(| +)v|(?<! )a(| +)p(| +)p|(?<! )i(| +)n|(?<! )i(| +)s|(?<! )g(| +)g|(?<! )t(| +)o|(?<! )p(| +)h|(?<! )n(| +)l|(?<! )i(| +)d|(?<! )i(| +)n(| +)c|(?<! )u(| +)s|(?<! )p(| +)w|(?<! )p(| +)r(| +)o|(?<! )t(| +)v|(?<! )c(| +)x|(?<! )m(| +)x|(?<! )f(| +)m|(?<! )c(| +)c|(?<! )v(| +)i(| +)p|(?<! )f(| +)u(| +)n|(?<! )i(| +)c(| +)u)\\b"
                , Pattern.CASE_INSENSITIVE));

        whitelistedWords.add("https://discordapp.com/invite/B5JW7qp");

        levelRoles.put(10,  guild.getRoleById(750227892822212759L));
        levelRoles.put(20,  guild.getRoleById(750228204832292885L));
        levelRoles.put(30,  guild.getRoleById(750229923100229693L));
        levelRoles.put(40,  guild.getRoleById(750228369924423730L));
        levelRoles.put(50,  guild.getRoleById(750228746161618955L));
        levelRoles.put(60,  guild.getRoleById(750228484072407060L));
        levelRoles.put(70,  guild.getRoleById(750230251811897385L));
        levelRoles.put(80,  guild.getRoleById(750229654803185725L));
        levelRoles.put(90,  guild.getRoleById(750230843062222888L));
        levelRoles.put(100, guild.getRoleById(750231871744376833L));

        plugin.getLogger().info("Guild: " + guild.getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!player.hasPlayedBefore()) {
            recentlyJoined.add(uuid);
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    recentlyJoined.remove(uuid), 20L * timeToSayWelcome);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        // Clean up welcome tracking and cooldown on disconnect
        timesSaidWelcome.remove(uuid);
        LevelHandler.removeFromCooldown(uuid.toString());
    }

    /*
     * Listens to when a player chats ingame.
     * AsyncPlayerChatEvent already fires off the main thread — no need to
     * spawn a second async task from inside it.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChatIngame(AsyncPlayerChatEvent e) {
        if (e.isCancelled()) return;

        Player player = e.getPlayer();
        UUID uuid = player.getUniqueId();
        String uuidStr = uuid.toString();

        // Don't process party chats
        McMMOPlayer mcMMOPlayer = UserManager.getPlayer(player);
        if (mcMMOPlayer != null && ChatAPI.isUsingPartyChat(player)) return;

        // Reward players who say welcome to new joiners
        if (isWelcome(e.getMessage())) {
            if (shouldRewardWelcome(uuid) && !ChatAPI.isUsingPartyChat(player)) {
                giveEXPAmount(uuidStr, expToGive);
                player.sendMessage(ChatColor.GREEN + "+" + expToGive
                        + ChatColor.GOLD + " Chat EXP "
                        + ChatColor.GRAY + "(/level) ");
            }
        }

        if (isOnCooldown(uuidStr)) return;

        int levelUpFlag = giveEXP(uuidStr);
        if (levelUpFlag > 0) {
            User discordUser = getDiscordUser(uuid);
            if (discordUser != null) {
                tryToGiveRankOnDiscord(discordUser, levelUpFlag);
            }

            if (levelUpFlag > 9) {
                Bukkit.broadcastMessage(ChatColor.DARK_AQUA + player.getName()
                        + " advanced to chat level " + ChatColor.AQUA + levelUpFlag);
            } else {
                player.sendMessage(ChatColor.DARK_AQUA + player.getName()
                        + " advanced to chat level " + ChatColor.AQUA + levelUpFlag);
            }
            player.sendMessage(ChatColor.GRAY + "For more info, use /level");
        }
    }

    /*
     * Listens to when a player chats through Discord.
     */
    @Subscribe(priority = ListenerPriority.MONITOR)
    public void discordMessageReceived(DiscordGuildMessageReceivedEvent e) {
        if (e.getChannel().getName().equals("spam")) return;

        // Fetch once and reuse — avoids the duplicate getMinecraftUser call further down
        OfflinePlayer ofp = getMinecraftUser(e.getAuthor().getId());
        if (ofp == null || !ofp.hasPlayedBefore()) return;

        String minecraftUUID = ofp.getUniqueId().toString();
        tryToGiveRankOnDiscord(e.getAuthor(), (int) getCurrentLevel(minecraftUUID));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (e.getChannel().getName().equals("server-chat") && isWelcome(e.getMessage().getContentRaw())) {
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(
                        DiscordSRV.getPlugin().getAccountLinkManager().getUuid(e.getMember().getId()));
                if (shouldRewardWelcome(offlinePlayer.getUniqueId())) {
                    giveEXPAmount(minecraftUUID, expToGive);
                    e.getMessage().reply(e.getMember().getAsMention() + "+" + expToGive + " Chat EXP (`/level`)").queue();
                }
            }

            if (isOnCooldown(minecraftUUID)) return;

            User discordUser = getDiscordUser(UUID.fromString(minecraftUUID));
            int levelUpFlag = giveEXP(minecraftUUID);
            if (levelUpFlag > 0 && discordUser != null) {
                TextChannel txt = DiscordSRV.getPlugin().getJda().getTextChannelById("448488708883218442");
                if (txt != null) {
                    txt.sendMessage(discordUser.getAsMention() + " advanced to chat level " + levelUpFlag).queue();
                }
                tryToGiveRankOnDiscord(discordUser, levelUpFlag);
            }
        });
    }

    @Nullable
    public static User getDiscordUser(UUID playerUUID) {
        if (playerUUID == null) return null;
        String id = DiscordSRV.getPlugin().getAccountLinkManager().getDiscordId(playerUUID);
        if (id == null) return null;
        return DiscordUtil.getJda().getUserById(id);
    }

    @Nullable
    public static OfflinePlayer getMinecraftUser(String discordID) {
        if (discordID == null) return null;
        UUID uuid = DiscordSRV.getPlugin().getAccountLinkManager().getUuid(discordID);
        if (uuid == null) return null;
        return Bukkit.getOfflinePlayer(uuid);
    }

    private int tryToGiveRankOnDiscord(User discordUser, int newLevel) {
        Member member = guild.getMemberById(discordUser.getId());
        if (member == null) return -1;

        List<Role> rolesOfUser = member.getRoles();

        // Iterate milestones in descending order — first match wins.
        // This replaces the brittle 10-branch if/else-if chain.
        List<Integer> milestones = new ArrayList<>(levelRoles.keySet());
        milestones.sort(Collections.reverseOrder());

        for (int milestone : milestones) {
            if (newLevel >= milestone) {
                Role role = levelRoles.get(milestone);
                if (!rolesOfUser.contains(role)) {
                    removeRoles(discordUser);
                    guild.addRoleToMember(member, role).queueAfter(200, TimeUnit.MILLISECONDS);
                    return milestone;
                }
                break; // already has the correct highest role
            }
        }

        return -1;
    }

    private boolean shouldRewardWelcome(UUID uuid) {
        if (recentlyJoined.isEmpty()) return false;

        int timesSaid = timesSaidWelcome.getOrDefault(uuid, 0);
        if (timesSaid < recentlyJoined.size()) {
            timesSaidWelcome.put(uuid, timesSaid + 1);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                int decayed = timesSaidWelcome.getOrDefault(uuid, 1) - 1;
                if (decayed <= 0) {
                    timesSaidWelcome.remove(uuid);
                } else {
                    timesSaidWelcome.put(uuid, decayed);
                }
            }, 20L * (timeToSayWelcome + 1));

            return true;
        }

        return false;
    }

    private void removeRoles(User discordUser) {
        Member member = guild.getMemberById(discordUser.getId());
        if (member == null) return;
        for (Role r : member.getRoles()) {
            if (levelRoles.containsValue(r)) {
                guild.removeRoleFromMember(member, r).queue();
            }
        }
    }

    public static boolean isWelcome(String str) {
        Pattern pattern = Pattern.compile("(?i)^welcome[!?,\\s]*.*");
        Matcher matcher = pattern.matcher(str);
        return matcher.matches();
    }
}