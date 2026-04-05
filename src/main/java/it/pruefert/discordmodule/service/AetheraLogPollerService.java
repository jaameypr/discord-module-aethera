package it.pruefert.discordmodule.service;

import it.pruefert.discordmodule.model.ServerDiscordConfig;
import it.pruefert.discordmodule.repository.ServerDiscordConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scheduled service that polls Aethera for new server log lines and forwards them
 * to {@link LogProcessingService} for Discord notification dispatching.
 *
 * <p>Runs every {@code AETHERA_LOG_POLL_INTERVAL_MS} milliseconds (default: 3 000 ms).</p>
 */
@Service
public class AetheraLogPollerService {

    private static final Logger log = LoggerFactory.getLogger(AetheraLogPollerService.class);

    /** Per-server last-seen timestamp (epoch ms). */
    private final Map<String, Long> lastSeenTimestamps = new ConcurrentHashMap<>();

    private final ServerDiscordConfigRepository configRepo;
    private final AetheraCallbackService        callback;
    private final LogProcessingService          processor;

    @Value("${aethera.log-poll-interval-ms:3000}")
    private long pollIntervalMs;

    public AetheraLogPollerService(ServerDiscordConfigRepository configRepo,
                                    AetheraCallbackService callback,
                                    LogProcessingService processor) {
        this.configRepo = configRepo;
        this.callback   = callback;
        this.processor  = processor;
    }

    @Scheduled(fixedDelayString = "${aethera.log-poll-interval-ms:3000}")
    public void poll() {
        List<ServerDiscordConfig> configs = configRepo.findAll();
        if (configs.isEmpty()) return;

        for (ServerDiscordConfig config : configs) {
            if (config.getGuildId() == null) continue;

            boolean anyChannelEnabled =
                    config.getPlayerChat().isEnabled() ||
                    config.getPlayerEvents().isEnabled() ||
                    config.getWhitelistRequests().isEnabled();

            if (!anyChannelEnabled) continue;

            String serverId = config.getServerId();
            long since = lastSeenTimestamps.getOrDefault(serverId, System.currentTimeMillis() - 10_000L);

            try {
                String[] lines = callback.fetchRecentLogs(serverId, since);
                if (lines.length > 0) {
                    // Advance the cursor BEFORE processing to avoid the race window
                    // where new log lines land between fetch and timestamp update.
                    long fetchedAt = System.currentTimeMillis();
                    for (String line : lines) {
                        processor.processLine(serverId, line);
                    }
                    lastSeenTimestamps.put(serverId, fetchedAt);
                }
            } catch (Exception e) {
                log.debug("[log-poller] Error polling server {}: {}", serverId, e.getMessage());
            }
        }
    }
}
