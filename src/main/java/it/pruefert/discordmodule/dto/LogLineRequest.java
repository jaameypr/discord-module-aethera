package it.pruefert.discordmodule.dto;

/**
 * Request body for POST /api/servers/{serverId}/log
 */
public record LogLineRequest(String line) {}
