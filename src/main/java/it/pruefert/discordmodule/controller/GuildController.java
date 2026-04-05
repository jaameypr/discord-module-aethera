package it.pruefert.discordmodule.controller;

import it.pruefert.discordmodule.dto.ChannelDto;
import it.pruefert.discordmodule.dto.GuildDto;
import it.pruefert.discordmodule.service.GuildService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST endpoints for Discord guild and channel information.
 */
@RestController
@RequestMapping("/api/guilds")
public class GuildController {

    private final GuildService guildService;

    public GuildController(GuildService guildService) {
        this.guildService = guildService;
    }

    /** GET /api/guilds — list all guilds the bot is in. */
    @GetMapping
    public ResponseEntity<?> listGuilds() {
        if (!guildService.isBotReady()) {
            return ResponseEntity.status(503)
                    .body(Map.of("error", "Discord bot is not connected. Check DISCORD_BOT_TOKEN."));
        }
        List<GuildDto> guilds = guildService.getGuilds();
        return ResponseEntity.ok(guilds);
    }

    /** GET /api/guilds/{guildId}/channels — list text channels in a guild. */
    @GetMapping("/{guildId}/channels")
    public ResponseEntity<?> listChannels(@PathVariable String guildId) {
        if (!guildService.isBotReady()) {
            return ResponseEntity.status(503)
                    .body(Map.of("error", "Discord bot is not connected."));
        }
        List<ChannelDto> channels = guildService.getChannels(guildId);
        return ResponseEntity.ok(channels);
    }

    /** POST /api/guilds/{guildId}/invite — create a Discord invite for the guild. */
    @PostMapping("/{guildId}/invite")
    public ResponseEntity<?> createInvite(@PathVariable String guildId) {
        if (!guildService.isBotReady()) {
            return ResponseEntity.status(503)
                    .body(Map.of("error", "Discord bot is not connected."));
        }
        String url = guildService.createInvite(guildId);
        if (url == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Could not create invite. Make sure the bot has permission to create invites."));
        }
        return ResponseEntity.ok(Map.of("url", url));
    }

    /** GET /api/bot/invite — get the bot OAuth2 invite URL. */
    @GetMapping("/bot-invite")
    public ResponseEntity<?> botInviteUrl() {
        String url = guildService.getBotInviteUrl();
        return ResponseEntity.ok(Map.of("url", url));
    }
}
