package it.pruefert.discordmodule.controller;

import it.pruefert.discordmodule.dto.LogLineRequest;
import it.pruefert.discordmodule.service.LogProcessingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Accepts individual log lines pushed by Aethera for real-time processing.
 * Aethera may call this endpoint for each new log line instead of (or in addition to) polling.
 */
@RestController
@RequestMapping("/api/servers")
public class EventController {

    private final LogProcessingService processor;

    public EventController(LogProcessingService processor) {
        this.processor = processor;
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
}
