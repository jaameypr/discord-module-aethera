package it.pruefert.discordmodule.controller;

import it.pruefert.discordmodule.dto.LogLineRequest;
import it.pruefert.discordmodule.dto.ServerEventRequest;
import it.pruefert.discordmodule.service.LogProcessingService;
import it.pruefert.discordmodule.service.ServerEventService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Accepts individual log lines and lifecycle events pushed by Aethera for real-time processing.
 */
@RestController
@RequestMapping("/api/servers")
public class EventController {

    private final LogProcessingService processor;
    private final ServerEventService   eventService;

    public EventController(LogProcessingService processor, ServerEventService eventService) {
        this.processor    = processor;
        this.eventService = eventService;
    }

    /**
     * POST /api/servers/{serverId}/log
     * Body: { "line": "..." }
     */
    @PostMapping("/{serverId}/log")
    public ResponseEntity<Map<String, Boolean>> processLog(
            @PathVariable String serverId,
            @RequestBody LogLineRequest request) {

        if (request.line() == null || request.line().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("processed", false));
        }

        processor.processLine(serverId, request.line());
        return ResponseEntity.ok(Map.of("processed", true));
    }

    /**
     * POST /api/servers/{serverId}/event
     * Body: { "eventType": "SERVER_STARTED", "serverName": "My Server", "details": "..." }
     * <p>
     * Accepts server lifecycle events from Aethera (start, stop, error, backup) and
     * forwards them as Discord embeds to the configured serverEvents channel.
     */
    @PostMapping("/{serverId}/event")
    public ResponseEntity<Map<String, Boolean>> processEvent(
            @PathVariable String serverId,
            @RequestBody ServerEventRequest request) {

        if (request.eventType() == null || request.eventType().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("processed", false));
        }

        eventService.processEvent(serverId, request.eventType(), request.serverName(), request.details());
        return ResponseEntity.ok(Map.of("processed", true));
    }
}
