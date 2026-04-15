package it.pruefert.discordmodule.dto;

/**
 * Payload for POST /api/servers/{serverId}/event.
 * Sent by Aethera when a server lifecycle event occurs.
 */
public record ServerEventRequest(
        /** One of: SERVER_STARTED, SERVER_STOPPED, SERVER_ERROR, BACKUP_COMPLETED, BACKUP_FAILED */
        String eventType,
        /** Human-readable server name */
        String serverName,
        /** Optional detail message (e.g. exit code, backup filename) */
        String details
) {}
