package it.pruefert.discordmodule.service;

import it.pruefert.discordmodule.bot.DiscordBotService;
import it.pruefert.discordmodule.dto.ChannelDto;
import it.pruefert.discordmodule.dto.GuildDto;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Provides guild/channel information from the connected Discord bot.
 */
@Service
public class GuildService {

    private final DiscordBotService bot;

    @Value("${discord.client-id:}")
    private String clientId;

    public GuildService(DiscordBotService bot) {
        this.bot = bot;
    }

    public List<GuildDto> getGuilds() {
        return bot.getGuilds().stream()
                .map(g -> new GuildDto(
                        g.getId(),
                        g.getName(),
                        g.getIconUrl()
                ))
                .toList();
    }

    public List<ChannelDto> getChannels(String guildId) {
        return bot.getTextChannels(guildId).stream()
                .map(c -> new ChannelDto(c.getId(), c.getName()))
                .toList();
    }

    /**
     * Creates a Discord server invite via the first available text channel in the guild.
     */
    public String createInvite(String guildId) {
        Guild guild = bot.getGuild(guildId);
        if (guild == null) return null;

        List<TextChannel> channels = guild.getTextChannels();
        if (channels.isEmpty()) return null;

        return bot.createInvite(channels.get(0).getId());
    }

    public String getBotInviteUrl() {
        return bot.getBotInviteUrl(clientId);
    }

    public boolean isBotReady() {
        return bot.isReady();
    }
}
