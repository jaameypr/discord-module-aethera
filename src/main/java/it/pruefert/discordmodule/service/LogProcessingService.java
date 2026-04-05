package it.pruefert.discordmodule.service;

import it.pruefert.discordmodule.bot.DiscordBotService;
import it.pruefert.discordmodule.model.ServerDiscordConfig;
import it.pruefert.discordmodule.repository.ServerDiscordConfigRepository;
import net.dv8tion.jda.api.EmbedBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Processes incoming Minecraft server log lines and dispatches Discord notifications
 * based on the server's configured channel mappings.
 *
 * <p>This service uses flexible regexes that work with vanilla Minecraft, Paper, Spigot,
 * Purpur, and Forge/Fabric. Chat regex works for <strong>default chat formatting only</strong>;
 * plugins that override chat format (e.g. EssentialsChat, ChatControl) may produce
 * false negatives or false positives.</p>
 */
@Service
public class LogProcessingService {

    private static final Logger log = LoggerFactory.getLogger(LogProcessingService.class);

    // -- Regex patterns -------------------------------------------------------

    /** Vanilla/Paper default chat: <PlayerName> message */
    private static final Pattern CHAT_PATTERN =
            Pattern.compile("<([a-zA-Z0-9_]{2,16})>\\s+(.+)");

    /** Player joined the game */
    private static final Pattern JOIN_PATTERN =
            Pattern.compile("([a-zA-Z0-9_]{2,16}) joined the game");

    /** Player left the game */
    private static final Pattern LEAVE_PATTERN =
            Pattern.compile("([a-zA-Z0-9_]{2,16}) left the game");

    /**
     * Whitelist rejection patterns — three variants:
     * <ol>
     *   <li>Old vanilla: {@code PlayerName[/IP] logged in but they are not on the white-list}</li>
     *   <li>Modern vanilla/Paper: {@code PlayerName (/IP:port) lost connection: You are not white-listed}</li>
     *   <li>Alternate modern: {@code Disconnecting PlayerName (/IP:port): You are not white-listed}</li>
     * </ol>
     */
    private static final Pattern WHITELIST_OLD_PATTERN =
            Pattern.compile("([a-zA-Z0-9_]{2,16})\\[/[\\S]+\\] logged in but they are not on the white-list");

    /** Matches: "PlayerName (/ip:port) lost connection: You are not white-listed on this server" */
    private static final Pattern WHITELIST_LOST_CONN_PATTERN =
            Pattern.compile("([a-zA-Z0-9_]{2,16}) \\(/[^)]+\\) lost connection: You are not white-listed on this server",
                    Pattern.CASE_INSENSITIVE);

    /** Matches: "Disconnecting PlayerName (/ip:port): You are not white-listed on this server" */
    private static final Pattern WHITELIST_DISCONNECTING_PATTERN =
            Pattern.compile("Disconnecting ([a-zA-Z0-9_]{2,16}) \\(/[^)]+\\).*?You are not white-listed on this server",
                    Pattern.CASE_INSENSITIVE);

    /** Matches: "UUID of player PlayerName is <uuid>" — used to correlate UUIDs across log lines */
    private static final Pattern UUID_LINE_PATTERN =
            Pattern.compile("UUID of player ([a-zA-Z0-9_]{2,16}) is ([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})",
                    Pattern.CASE_INSENSITIVE);

    // Per-server UUID cache: playerName (lowercase) -> UUID, bounded to 200 entries
    private final Map<String, Map<String, String>> uuidCache = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------

    private final DiscordBotService bot;
    private final ServerDiscordConfigRepository configRepo;
    private final WhitelistService whitelistService;

    public LogProcessingService(DiscordBotService bot,
                                 ServerDiscordConfigRepository configRepo,
                                 WhitelistService whitelistService) {
        this.bot             = bot;
        this.configRepo      = configRepo;
        this.whitelistService = whitelistService;
    }

    /**
     * Processes a single log line for a given server.
     * Checks against all configured event channels and dispatches Discord messages.
     */
    public void processLine(String serverId, String line) {
        // Always cache UUID lines so whitelist rejection can correlate them
        cacheUuidIfPresent(serverId, line);

        ServerDiscordConfig config = configRepo.findById(serverId).orElse(null);
        if (config == null || config.getGuildId() == null) {
            log.warn("[log-processor] no config for server {} — skipping line: {}", serverId, line);
            return;
        }

        tryChat(config, line);
        tryJoinLeave(config, line);
        tryWhitelistRejection(config, serverId, line);
    }

    /** Store "UUID of player X is Y" lines in a per-server bounded cache. */
    @SuppressWarnings("unchecked")
    private void cacheUuidIfPresent(String serverId, String line) {
        Matcher m = UUID_LINE_PATTERN.matcher(line);
        if (!m.find()) return;

        String playerName = m.group(1);
        String uuid       = m.group(2);

        // Bounded LinkedHashMap (evicts eldest on overflow)
        Map<String, String> cache = uuidCache.computeIfAbsent(serverId, k ->
                new LinkedHashMap<>(64, 0.75f, false) {
                    @Override protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                        return size() > 200;
                    }
                });

        cache.put(playerName.toLowerCase(), uuid);
        log.debug("[log-processor] cached UUID for {}: {}", playerName, uuid);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void tryChat(ServerDiscordConfig config, String line) {
        ServerDiscordConfig.ChannelConfig cc = config.getPlayerChat();
        if (!cc.isEnabled()) return;
        if (cc.getChannelId() == null) {
            log.debug("[log-processor] chat enabled but no channel selected — skipping line");
            return;
        }

        Matcher m = CHAT_PATTERN.matcher(line);
        if (!m.find()) {
            log.debug("[log-processor] chat: no match on: {}", line);
            return;
        }

        String player  = m.group(1);
        String message = m.group(2);
        log.info("[log-processor] chat match: player={} message={}", player, message);
        bot.sendMessage(cc.getChannelId(), "**" + player + "** » " + message);
    }

    private void tryJoinLeave(ServerDiscordConfig config, String line) {
        ServerDiscordConfig.ChannelConfig cc = config.getPlayerEvents();
        if (!cc.isEnabled()) return;
        if (cc.getChannelId() == null) {
            log.debug("[log-processor] events enabled but no channel selected — skipping line");
            return;
        }

        Matcher join  = JOIN_PATTERN.matcher(line);
        Matcher leave = LEAVE_PATTERN.matcher(line);

        if (join.find()) {
            String player = join.group(1);
            log.info("[log-processor] join match: player={}", player);
            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(Color.decode("#22c55e"))
                    .setDescription("🟢 **" + player + "** joined the server")
                    .setTimestamp(Instant.now());
            bot.sendEmbed(cc.getChannelId(), embed);
        } else if (leave.find()) {
            String player = leave.group(1);
            log.info("[log-processor] leave match: player={}", player);
            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(Color.decode("#ef4444"))
                    .setDescription("🔴 **" + player + "** left the server")
                    .setTimestamp(Instant.now());
            bot.sendEmbed(cc.getChannelId(), embed);
        } else {
            log.debug("[log-processor] join/leave: no match on: {}", line);
        }
    }

    private void tryWhitelistRejection(ServerDiscordConfig config, String serverId, String line) {
        ServerDiscordConfig.ChannelConfig cc = config.getWhitelistRequests();
        if (!cc.isEnabled()) return;
        if (cc.getChannelId() == null) {
            log.debug("[log-processor] whitelist enabled but no channel selected — skipping line");
            return;
        }

        String playerName = null;

        Matcher lostConn      = WHITELIST_LOST_CONN_PATTERN.matcher(line);
        Matcher disconnecting = WHITELIST_DISCONNECTING_PATTERN.matcher(line);
        Matcher oldStyle      = WHITELIST_OLD_PATTERN.matcher(line);

        if (lostConn.find()) {
            playerName = lostConn.group(1);
        } else if (disconnecting.find()) {
            playerName = disconnecting.group(1);
        } else if (oldStyle.find()) {
            playerName = oldStyle.group(1);
        }

        if (playerName == null) return;

        // Look up UUID from the cache (populated by "UUID of player X is Y" lines)
        Map<String, String> cache = uuidCache.get(serverId);
        String playerUuid = cache != null ? cache.get(playerName.toLowerCase()) : null;

        log.info("[log-processor] whitelist rejection: player={} uuid={} server={}", playerName, playerUuid, serverId);
        whitelistService.handleRejection(serverId, playerName, playerUuid, cc.getChannelId(), cc.getRequiredRoleId());
    }
}
