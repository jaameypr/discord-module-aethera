package it.pruefert.discordmodule.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Sends callbacks to the Aethera main application.
 *
 * <p>Uses the same {@code AETHERA_API_KEY} that Aethera uses to call this module,
 * so the internal callback endpoint can validate the request against the stored module config.</p>
 */
@Service
public class AetheraCallbackService {

    private static final Logger log = LoggerFactory.getLogger(AetheraCallbackService.class);

    @Value("${aethera.callback-url:http://aethera-app:3000}")
    private String callbackBaseUrl;

    @Value("${aethera.api-key:}")
    private String apiKey;

    private final RestClient restClient;

    public AetheraCallbackService() {
        this.restClient = RestClient.builder()
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Tells Aethera to execute {@code whitelist add <playerName>} on the given server.
     *
     * @return {@code true} if the command was accepted
     */
    public boolean executeWhitelistAdd(String serverId, String playerName) {
        String url = callbackBaseUrl + "/api/discord/callback/command";

        Map<String, String> body = Map.of(
                "serverId", serverId,
                "command", "whitelist add " + playerName
        );

        try {
            var request = restClient.post().uri(url).body(body);
            if (apiKey != null && !apiKey.isBlank()) {
                request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
            }
            request.retrieve().toBodilessEntity();
            log.info("[aethera-callback] Whitelist add '{}' executed on server {}", playerName, serverId);
            return true;
        } catch (Exception e) {
            log.error("[aethera-callback] Failed to execute whitelist add for {} on server {}: {}",
                    playerName, serverId, e.getMessage());
            return false;
        }
    }

    /**
     * Fetches recent server log lines from Aethera (internal polling endpoint).
     *
     * @param serverId      the Aethera server ID
     * @param sinceEpochMs  only return lines after this epoch-millisecond timestamp
     * @return array of log line strings, or empty array on failure
     */
    public String[] fetchRecentLogs(String serverId, long sinceEpochMs) {
        String url = callbackBaseUrl
                + "/api/discord/internal/servers/" + serverId + "/logs"
                + "?since=" + sinceEpochMs;

        try {
            var request = restClient.get().uri(url);
            if (apiKey != null && !apiKey.isBlank()) {
                request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
            }
            String[] lines = request.retrieve().body(String[].class);
            log.info("[aethera-callback] fetchLogs server={} url={} returned={} lines",
                    serverId, url, lines != null ? lines.length : 0);
            return lines != null ? lines : new String[0];
        } catch (Exception e) {
            log.warn("[aethera-callback] Log fetch failed for server {}: {}", serverId, e.getMessage());
            return new String[0];
        }
    }
}
