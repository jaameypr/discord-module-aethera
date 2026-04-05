package it.pruefert.discordmodule.service;

import it.pruefert.discordmodule.bot.DiscordBotService;
import it.pruefert.discordmodule.model.ServerDiscordConfig;
import it.pruefert.discordmodule.model.WhitelistRequest;
import it.pruefert.discordmodule.repository.ServerDiscordConfigRepository;
import net.dv8tion.jda.api.EmbedBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.time.Instant;
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
     * Whitelist rejection — two variants:
     * <ol>
     *   <li>Older vanilla: {@code PlayerName[/IP] logged in but they are not on the white-list}</li>
     *   <li>Modern Paper/vanilla: {@code name='PlayerName'...lost connection: You are not white-listed on this server}</li>
     * </ol>
     */
    private static final Pattern WHITELIST_OLD_PATTERN =
            Pattern.compile("([a-zA-Z0-9_]{2,16})\\[/[\\S]+\\] logged in but they are not on the white-list");

    private static final Pattern WHITELIST_NEW_PATTERN =
            Pattern.compile("name='([a-zA-Z0-9_]{2,16})'.*?lost connection: You are not white-listed on this server",
                    Pattern.CASE_INSENSITIVE);

    /** UUID extraction from modern whitelist rejection line */
    private static final Pattern UUID_PATTERN =
            Pattern.compile("id='([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})'",
                    Pattern.CASE_INSENSITIVE);

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
        ServerDiscordConfig config = configRepo.findById(serverId).orElse(null);
        if (config == null || config.getGuildId() == null) {
            log.warn("[log-processor] no config for server {} — skipping line: {}", serverId, line);
            return;
        }

        tryChat(config, line);
        tryJoinLeave(config, line);
        tryWhitelistRejection(config, serverId, line);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void tryChat(ServerDiscordConfig config, String line) {
        ServerDiscordConfig.ChannelConfig cc = config.getPlayerChat();
        if (!cc.isEnabled() || cc.getChannelId() == null) return;

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
        if (!cc.isEnabled() || cc.getChannelId() == null) return;

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
        if (!cc.isEnabled() || cc.getChannelId() == null) return;

        String playerName = null;
        String playerUuid = null;

        Matcher oldMatcher = WHITELIST_OLD_PATTERN.matcher(line);
        Matcher newMatcher = WHITELIST_NEW_PATTERN.matcher(line);

        if (oldMatcher.find()) {
            playerName = oldMatcher.group(1);
        } else if (newMatcher.find()) {
            playerName = newMatcher.group(1);
            Matcher uuidMatcher = UUID_PATTERN.matcher(line);
            if (uuidMatcher.find()) {
                playerUuid = uuidMatcher.group(1);
            }
        }

        if (playerName == null) return;

        log.info("[log-processor] Whitelist rejection for '{}' on server {}", playerName, serverId);
        whitelistService.handleRejection(serverId, playerName, playerUuid, cc.getChannelId(), cc.getRequiredRoleId());
    }
}
