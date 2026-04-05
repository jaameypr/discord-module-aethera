package it.pruefert.discordmodule.controller;

import it.pruefert.discordmodule.dto.ServerDiscordConfigDto;
import it.pruefert.discordmodule.model.ServerDiscordConfig;
import it.pruefert.discordmodule.model.WhitelistRequest;
import it.pruefert.discordmodule.repository.ServerDiscordConfigRepository;
import it.pruefert.discordmodule.repository.WhitelistRequestRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST endpoints for per-server Discord configuration and whitelist request listing.
 */
@RestController
@RequestMapping("/api/servers")
public class ServerConfigController {

    private final ServerDiscordConfigRepository configRepo;
    private final WhitelistRequestRepository    requestRepo;

    public ServerConfigController(ServerDiscordConfigRepository configRepo,
                                   WhitelistRequestRepository requestRepo) {
        this.configRepo  = configRepo;
        this.requestRepo = requestRepo;
    }

    /** GET /api/servers/{serverId}/config */
    @GetMapping("/{serverId}/config")
    public ResponseEntity<?> getConfig(@PathVariable String serverId) {
        ServerDiscordConfig config = configRepo.findById(serverId)
                .orElse(newEmptyConfig(serverId));
        return ResponseEntity.ok(toDto(config));
    }

    /** PUT /api/servers/{serverId}/config */
    @PutMapping("/{serverId}/config")
    public ResponseEntity<?> updateConfig(@PathVariable String serverId,
                                           @RequestBody ServerDiscordConfigDto dto) {
        ServerDiscordConfig config = configRepo.findById(serverId)
                .orElse(newEmptyConfig(serverId));

        // Apply guild info
        config.setGuildId(dto.getGuildId());
        config.setGuildName(dto.getGuildName());

        // Apply channel configs
        config.setPlayerChat(fromDto(dto.getPlayerChat()));
        config.setPlayerEvents(fromDto(dto.getPlayerEvents()));
        config.setWhitelistRequests(fromDto(dto.getWhitelistRequests()));

        configRepo.save(config);
        return ResponseEntity.ok(toDto(config));
    }

    /** DELETE /api/servers/{serverId}/config — remove the server's Discord config. */
    @DeleteMapping("/{serverId}/config")
    public ResponseEntity<Void> deleteConfig(@PathVariable String serverId) {
        configRepo.deleteById(serverId);
        return ResponseEntity.noContent().build();
    }

    /** GET /api/servers/{serverId}/whitelist-requests — pending whitelist requests. */
    @GetMapping("/{serverId}/whitelist-requests")
    public ResponseEntity<List<WhitelistRequest>> listRequests(@PathVariable String serverId) {
        List<WhitelistRequest> reqs = requestRepo
                .findByServerIdAndProcessedFalseOrderByCreatedAtDesc(serverId);
        return ResponseEntity.ok(reqs);
    }

    // -----------------------------------------------------------------------
    // Mapping helpers
    // -----------------------------------------------------------------------

    private static ServerDiscordConfig newEmptyConfig(String serverId) {
        ServerDiscordConfig c = new ServerDiscordConfig();
        c.setServerId(serverId);
        return c;
    }

    private static ServerDiscordConfigDto toDto(ServerDiscordConfig c) {
        ServerDiscordConfigDto dto = new ServerDiscordConfigDto();
        dto.setGuildId(c.getGuildId());
        dto.setGuildName(c.getGuildName());
        dto.setPlayerChat(channelToDto(c.getPlayerChat()));
        dto.setPlayerEvents(channelToDto(c.getPlayerEvents()));
        dto.setWhitelistRequests(channelToDto(c.getWhitelistRequests()));
        return dto;
    }

    private static ServerDiscordConfigDto.ChannelConfigDto channelToDto(ServerDiscordConfig.ChannelConfig c) {
        ServerDiscordConfigDto.ChannelConfigDto dto = new ServerDiscordConfigDto.ChannelConfigDto();
        dto.setEnabled(c.isEnabled());
        dto.setChannelId(c.getChannelId());
        dto.setRequiredRoleId(c.getRequiredRoleId());
        return dto;
    }

    private static ServerDiscordConfig.ChannelConfig fromDto(ServerDiscordConfigDto.ChannelConfigDto dto) {
        if (dto == null) return new ServerDiscordConfig.ChannelConfig();
        ServerDiscordConfig.ChannelConfig c = new ServerDiscordConfig.ChannelConfig();
        c.setEnabled(dto.isEnabled());
        c.setChannelId(dto.getChannelId());
        c.setRequiredRoleId(dto.getRequiredRoleId());
        return c;
    }
}
