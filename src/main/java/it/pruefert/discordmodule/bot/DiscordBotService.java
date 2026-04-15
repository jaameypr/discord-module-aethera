package it.pruefert.discordmodule.bot;

import it.pruefert.discordmodule.service.WhitelistService;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.util.List;
import java.util.Map;

/**
 * Manages the JDA (Java Discord API) bot lifecycle.
 * <p>
 * Responsible for:
 * <ul>
 *   <li>Starting/stopping the JDA connection</li>
 *   <li>Routing button-click interactions to {@link WhitelistService}</li>
 *   <li>Sending messages to Discord channels</li>
 * </ul>
 */
@Service
public class DiscordBotService extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(DiscordBotService.class);

    /** Prefix for the whitelist-approval button custom ID. */
    public static final String WHITELIST_APPROVE_PREFIX = "wl_approve:";

    @Value("${discord.bot-token:}")
    private String botToken;

    private JDA jda;

    @Autowired
    @Lazy
    private WhitelistService whitelistService;

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    @PostConstruct
    public void start() {
        if (botToken == null || botToken.isBlank()) {
            log.warn("[discord-bot] DISCORD_BOT_TOKEN is not configured — bot will not start");
            return;
        }

        // Start JDA on a virtual thread to avoid blocking Spring's main startup thread
        Thread.startVirtualThread(() -> {
            try {
                jda = JDABuilder.createDefault(botToken)
                        .enableIntents(
                                GatewayIntent.GUILD_MESSAGES,
                                GatewayIntent.GUILD_MEMBERS
                        )
                        .addEventListeners(this)
                        .build()
                        .awaitReady();

                log.info("[discord-bot] Connected as {} ({})", jda.getSelfUser().getName(), jda.getSelfUser().getId());
            } catch (Exception e) {
                log.error("[discord-bot] Failed to start JDA bot: {}", e.getMessage());
            }
        });
    }

    @PreDestroy
    public void stop() {
        if (jda != null) {
            jda.shutdown();
            log.info("[discord-bot] JDA bot stopped");
        }
    }

    public boolean isReady() {
        return jda != null && jda.getStatus() == JDA.Status.CONNECTED;
    }

    // -----------------------------------------------------------------------
    // Event: Button Interaction
    // -----------------------------------------------------------------------

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String customId = event.getComponentId();
        if (!customId.startsWith(WHITELIST_APPROVE_PREFIX)) return;

        // Format: wl_approve:{requestId}:{serverId}:{playerName}
        String[] parts = customId.substring(WHITELIST_APPROVE_PREFIX.length()).split(":", 3);
        if (parts.length < 3) {
            event.reply("❌ Invalid button data.").setEphemeral(true).queue();
            return;
        }

        String requestId  = parts[0];
        String serverId   = parts[1];
        String playerName = parts[2];

        // Delegate the actual approval logic (role check + command execution) to the service
        whitelistService.handleApproval(event, requestId, serverId, playerName);
    }

    // -----------------------------------------------------------------------
    // Guild helpers
    // -----------------------------------------------------------------------

    /**
     * Returns all guilds the bot is currently in.
     */
    public List<Guild> getGuilds() {
        if (jda == null) return List.of();
        return jda.getGuilds();
    }

    /**
     * Returns a guild by ID, or {@code null} if not found / bot not ready.
     */
    public Guild getGuild(String guildId) {
        if (jda == null) return null;
        return jda.getGuildById(guildId);
    }

    /**
     * Returns all text channels in a guild.
     */
    public List<TextChannel> getTextChannels(String guildId) {
        Guild guild = getGuild(guildId);
        if (guild == null) return List.of();
        return guild.getTextChannels();
    }

    // -----------------------------------------------------------------------
    // Messaging
    // -----------------------------------------------------------------------

    /**
     * Sends a plain text message to a Discord channel.
     */
    public void sendMessage(String channelId, String content) {
        if (jda == null) return;
        TextChannel ch = jda.getTextChannelById(channelId);
        if (ch == null) {
            log.warn("[discord-bot] Channel {} not found", channelId);
            return;
        }
        ch.sendMessage(content).queue(
                m  -> log.debug("[discord-bot] Sent message to #{}", ch.getName()),
                ex -> log.error("[discord-bot] Failed to send message to #{}: {}", ch.getName(), ex.getMessage())
        );
    }

    /**
     * Sends an embed message to a Discord channel.
     */
    public void sendEmbed(String channelId, net.dv8tion.jda.api.EmbedBuilder embedBuilder) {
        if (jda == null) return;
        TextChannel ch = jda.getTextChannelById(channelId);
        if (ch == null) {
            log.warn("[discord-bot] Channel {} not found", channelId);
            return;
        }
        ch.sendMessageEmbeds(embedBuilder.build()).queue(
                m  -> {},
                ex -> log.error("[discord-bot] Failed to send embed to #{}: {}", ch.getName(), ex.getMessage())
        );
    }

    /**
     * Sends an embed with an "Add to Whitelist" button and stores the message ID for later editing.
     *
     * @return the sent message ID, or {@code null} on failure
     */
    public String sendWhitelistRequest(String channelId,
                                        net.dv8tion.jda.api.EmbedBuilder embedBuilder,
                                        String requestId,
                                        String serverId,
                                        String playerName) {
        if (jda == null) return null;
        TextChannel ch = jda.getTextChannelById(channelId);
        if (ch == null) {
            log.warn("[discord-bot] Channel {} not found for whitelist request", channelId);
            return null;
        }

        String customId = WHITELIST_APPROVE_PREFIX + requestId + ":" + serverId + ":" + playerName;
        Button approveBtn = Button.success(customId, "✅ Add to Whitelist");

        var msg = new MessageCreateBuilder()
                .addEmbeds(embedBuilder.build())
                .addActionRow(approveBtn)
                .build();

        try {
            var sentMessage = ch.sendMessage(msg).complete();
            return sentMessage.getId();
        } catch (Exception e) {
            log.error("[discord-bot] Failed to send whitelist request to #{}: {}", ch.getName(), e.getMessage());
            return null;
        }
    }

    /**
     * Disables the approval button on a processed whitelist request message.
     */
    public void disableWhitelistButton(String channelId, String messageId, String approvedBy) {
        if (jda == null) return;
        TextChannel ch = jda.getTextChannelById(channelId);
        if (ch == null) return;

        ch.retrieveMessageById(messageId).queue(msg -> {
            Button disabled = Button.success("wl_done", "✅ Added by @" + approvedBy).asDisabled();
            msg.editMessageComponents(net.dv8tion.jda.api.interactions.components.ActionRow.of(disabled)).queue();
        }, ex -> log.warn("[discord-bot] Could not update whitelist message {}: {}", messageId, ex.getMessage()));
    }

    /**
     * Creates a Discord invite for a given channel.
     *
     * @return the invite URL, or {@code null} on failure
     */
    public String createInvite(String channelId) {
        if (jda == null) return null;
        TextChannel ch = jda.getTextChannelById(channelId);
        if (ch == null) return null;

        try {
            var invite = ch.createInvite().setMaxAge(0).setMaxUses(0).complete();
            return invite.getUrl();
        } catch (Exception e) {
            log.error("[discord-bot] Failed to create invite for channel {}: {}", channelId, e.getMessage());
            return null;
        }
    }

    /**
     * Builds the OAuth2 bot invite URL with all required permissions.
     */
    public String getBotInviteUrl(String clientId) {
        // Permissions: VIEW_CHANNEL(1024) + SEND_MESSAGES(2048) + EMBED_LINKS(16384)
        //              + CREATE_INSTANT_INVITE(1) + READ_MESSAGE_HISTORY(65536)
        long permissions = 1L | 1024L | 2048L | 16384L | 65536L;
        return "https://discord.com/api/oauth2/authorize"
                + "?client_id=" + clientId
                + "&permissions=" + permissions
                + "&scope=bot";
    }
}
