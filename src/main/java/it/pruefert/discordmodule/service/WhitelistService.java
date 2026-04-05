package it.pruefert.discordmodule.service;

import it.pruefert.discordmodule.bot.DiscordBotService;
import it.pruefert.discordmodule.model.WhitelistRequest;
import it.pruefert.discordmodule.repository.WhitelistRequestRepository;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.awt.Color;
import java.time.Instant;
import java.util.Map;

/**
 * Handles whitelist-request creation and Discord-button approval flow.
 */
@Service
public class WhitelistService {

    private static final Logger log = LoggerFactory.getLogger(WhitelistService.class);

    /** Mineatar.io face avatar URL (128px at scale=4). */
    private static final String SKIN_URL_TEMPLATE =
            "https://mineatar.io/face/%s?scale=4";

    /** Steve fallback UUID for players without a known UUID. */
    private static final String STEVE_UUID = "8667ba71b85a4004af54457a9734eed7";

    /** Mojang profiles API endpoint. */
    private static final String MOJANG_API =
            "https://api.mojang.com/users/profiles/minecraft/";

    private final WhitelistRequestRepository requestRepo;
    private final DiscordBotService bot;
    private final AetheraCallbackService callback;

    @Value("${discord.client-id:}")
    private String clientId;

    public WhitelistService(WhitelistRequestRepository requestRepo,
                             DiscordBotService bot,
                             AetheraCallbackService callback) {
        this.requestRepo = requestRepo;
        this.bot         = bot;
        this.callback    = callback;
    }

    // -----------------------------------------------------------------------
    // Create a new whitelist request
    // -----------------------------------------------------------------------

    /**
     * Called when a whitelist rejection is detected in the server logs.
     * Looks up the player's UUID (if not already known) via Mojang API, posts
     * a Discord embed with an approval button to the configured channel.
     */
    public void handleRejection(String serverId,
                                  String playerName,
                                  String playerUuid,
                                  String channelId,
                                  String requiredRoleId) {
        // De-duplicate: skip if there's already a pending request for this player
        boolean hasPending = requestRepo
                .findByServerIdAndProcessedFalseOrderByCreatedAtDesc(serverId)
                .stream()
                .anyMatch(r -> r.getPlayerName().equalsIgnoreCase(playerName));

        if (hasPending) {
            log.debug("[whitelist] Skipping duplicate pending request for {}", playerName);
            return;
        }

        // Resolve UUID via Mojang if not captured from log
        if (playerUuid == null) {
            playerUuid = lookupUuid(playerName);
        }

        String skinUrl = playerUuid != null
                ? SKIN_URL_TEMPLATE.formatted(playerUuid.replace("-", ""))
                : SKIN_URL_TEMPLATE.formatted(STEVE_UUID);

        WhitelistRequest req = new WhitelistRequest();
        req.setServerId(serverId);
        req.setPlayerName(playerName);
        req.setPlayerUuid(playerUuid);
        req.setSkinUrl(skinUrl);
        req.setChannelId(channelId);
        req.setRequiredRoleId(requiredRoleId);
        req.setCreatedAt(Instant.now());
        req = requestRepo.save(req);

        String requestId = req.getId();

        EmbedBuilder embed = buildRequestEmbed(playerName, playerUuid, skinUrl, requiredRoleId);
        String messageId   = bot.sendWhitelistRequest(channelId, embed, requestId, serverId, playerName);

        if (messageId != null) {
            req.setDiscordMessageId(messageId);
            requestRepo.save(req);
        }
    }

    // -----------------------------------------------------------------------
    // Button click approval
    // -----------------------------------------------------------------------

    /**
     * Called by the JDA button-interaction listener when a Discord user clicks
     * the "Add to Whitelist" button.
     */
    public void handleApproval(ButtonInteractionEvent event,
                                 String requestId,
                                 String serverId,
                                 String playerName) {
        // Defer reply immediately (we may need a moment for validation)
        event.deferReply(true).queue();

        WhitelistRequest req = requestRepo.findById(requestId).orElse(null);
        if (req == null || req.isProcessed()) {
            event.getHook().sendMessage("❌ This request no longer exists or was already processed.")
                    .setEphemeral(true).queue();
            return;
        }

        // Check required role
        String requiredRoleId = getRoleIdForRequest(req);
        if (requiredRoleId != null && !requiredRoleId.isBlank()) {
            Guild guild = event.getGuild();
            if (guild == null) {
                event.getHook().sendMessage("❌ Cannot verify role outside of a guild.").setEphemeral(true).queue();
                return;
            }

            boolean hasRole = event.getMember() != null &&
                    event.getMember().getRoles().stream()
                            .anyMatch(r -> r.getId().equals(requiredRoleId));

            if (!hasRole) {
                event.getHook().sendMessage("❌ You don't have the required role to approve whitelist requests.")
                        .setEphemeral(true).queue();
                return;
            }
        }

        // Execute whitelist command via Aethera callback
        String approverName = event.getUser().getName();
        boolean success = callback.executeWhitelistAdd(serverId, playerName);

        if (!success) {
            event.getHook().sendMessage("⚠️ The whitelist command could not be executed. Please add the player manually.")
                    .setEphemeral(true).queue();
            return;
        }

        // Mark as processed
        req.setProcessed(true);
        req.setApprovedBy(approverName);
        requestRepo.save(req);

        // Disable the button on the original message
        if (req.getDiscordMessageId() != null) {
            bot.disableWhitelistButton(req.getChannelId(), req.getDiscordMessageId(), approverName);
        }

        event.getHook().sendMessage("✅ **" + playerName + "** has been added to the whitelist by @" + approverName + "!")
                .setEphemeral(false).queue();

        log.info("[whitelist] {} approved by {} on server {}", playerName, approverName, serverId);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private EmbedBuilder buildRequestEmbed(String playerName, String playerUuid, String skinUrl, String requiredRoleId) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🔒 Whitelist Request")
                .setColor(Color.decode("#f59e0b"))
                .setThumbnail(skinUrl)
                .addField("Player", playerName, true)
                .setTimestamp(Instant.now());

        if (playerUuid != null) {
            embed.addField("UUID", "`" + playerUuid + "`", false);
        }

        if (requiredRoleId != null && !requiredRoleId.isBlank()) {
            embed.setFooter("Requires role <@&" + requiredRoleId + "> to approve");
        } else {
            embed.setFooter("Click the button below to approve");
        }

        return embed;
    }

    /**
     * Looks up the Mojang UUID for a given player name.
     *
     * @return the UUID string (with dashes), or {@code null} if not found
     */
    private String lookupUuid(String playerName) {
        try {
            RestClient client = RestClient.create();
            @SuppressWarnings("unchecked")
            Map<String, String> response = client.get()
                    .uri(MOJANG_API + playerName)
                    .retrieve()
                    .body(Map.class);

            if (response == null || !response.containsKey("id")) return null;

            String raw = response.get("id"); // 32-char hex without dashes
            return raw.replaceFirst(
                    "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})",
                    "$1-$2-$3-$4-$5"
            );
        } catch (Exception e) {
            log.warn("[whitelist] Could not look up UUID for {}: {}", playerName, e.getMessage());
            return null;
        }
    }

    /**
     * Retrieves the required Discord role ID for the whitelist channel of the given request's server.
     * Loads from the server config via the ServerDiscordConfig document stored in the DB.
     */
    private String getRoleIdForRequest(WhitelistRequest req) {
        return req.getRequiredRoleId();
    }
}
