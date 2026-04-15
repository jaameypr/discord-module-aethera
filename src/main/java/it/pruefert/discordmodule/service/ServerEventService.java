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
import java.util.Map;

/**
 * Handles server lifecycle events (start, stop, error, backup) pushed by Aethera
 * and forwards them as Discord embeds to the configured serverEvents channel.
 */
@Service
public class ServerEventService {

    private static final Logger log = LoggerFactory.getLogger(ServerEventService.class);

    private static final Map<String, Color> COLOR_MAP = Map.of(
            "SERVER_STARTED",    new Color(0x22, 0xc5, 0x5e),  // green
            "SERVER_STOPPED",    new Color(0xef, 0x44, 0x44),  // red
            "SERVER_ERROR",      new Color(0xf5, 0x9e, 0x0b),  // amber
            "BACKUP_COMPLETED",  new Color(0x3b, 0x82, 0xf6),  // blue
            "BACKUP_FAILED",     new Color(0xef, 0x44, 0x44)   // red
    );

    private static final Map<String, String> EMOJI_MAP = Map.of(
            "SERVER_STARTED",    "🟢",
            "SERVER_STOPPED",    "🔴",
            "SERVER_ERROR",      "⚠️",
            "BACKUP_COMPLETED",  "💾",
            "BACKUP_FAILED",     "❌"
    );

    private final DiscordBotService               bot;
    private final ServerDiscordConfigRepository   configRepo;

    public ServerEventService(DiscordBotService bot, ServerDiscordConfigRepository configRepo) {
        this.bot        = bot;
        this.configRepo = configRepo;
    }

    /**
     * Processes a server lifecycle event and sends a Discord embed if the server has
     * a serverEvents channel configured.
     */
    public void processEvent(String serverId, String eventType, String serverName, String details) {
        ServerDiscordConfig config = configRepo.findById(serverId).orElse(null);
        if (config == null) {
            log.debug("[server-events] no config for server {} — skipping event {}", serverId, eventType);
            return;
        }

        ServerDiscordConfig.ChannelConfig cc = config.getServerEvents();
        if (cc == null || !cc.isEnabled() || cc.getChannelId() == null) {
            log.debug("[server-events] serverEvents not enabled/configured for server {} — skipping {}", serverId, eventType);
            return;
        }

        String emoji = EMOJI_MAP.getOrDefault(eventType, "🔔");
        String title = emoji + " " + eventType.replace("_", " ");
        Color  color = COLOR_MAP.getOrDefault(eventType, new Color(0x63, 0x66, 0xf1));

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(title)
                .setColor(color)
                .setFooter("Server: " + (serverName != null ? serverName : serverId))
                .setTimestamp(Instant.now());

        if (details != null && !details.isBlank()) {
            embed.setDescription(details);
        }

        log.info("[server-events] sending {} event for server {} to channel {}", eventType, serverId, cc.getChannelId());
        bot.sendEmbed(cc.getChannelId(), embed);
    }
}
