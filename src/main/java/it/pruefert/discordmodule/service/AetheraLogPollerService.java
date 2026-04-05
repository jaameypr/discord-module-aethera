package it.pruefert.discordmodule.service;

import it.pruefert.discordmodule.model.ServerDiscordConfig;
import it.pruefert.discordmodule.repository.ServerDiscordConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scheduled service that polls Aethera for new server log lines and forwards them
 * to {@link LogProcessingService} for Discord notification dispatching.
 *
 * <p>Runs every {@code AETHERA_LOG_POLL_INTERVAL_MS} milliseconds (default: 3 000 ms).</p>
 *
 * <p>Because Docker log entries frequently have {@code null} timestamps, lines are
 * deduplicated in-memory (per server, last {@value MAX_DEDUP_SIZE} lines) so that
 * older lines repeated in the tail do not produce duplicate Discord messages.</p>
 */
@Service
public class AetheraLogPollerService {

    private static final Logger log = LoggerFactory.getLogger(AetheraLogPollerService.class);

    /** Maximum number of recently-seen line hashes to retain per server. */
    private static final int MAX_DEDUP_SIZE = 200;

    /** Per-server last-seen timestamp (epoch ms). */
    private final Map<String, Long> lastSeenTimestamps = new ConcurrentHashMap<>();

    /** Per-server dedup set — prevents re-posting lines with null Docker timestamps. */
    private final Map<String, LinkedHashSet<String>> seenLines = new ConcurrentHashMap<>();

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
        if (configs.isEmpty()) {
            log.info("[log-poller] no server configs in DB — save a Discord config in the server detail view first");
            return;
        }

        log.info("[log-poller] polling {} configured server(s)", configs.size());

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
                log.info("[log-poller] server={} fetched={} lines since={}", serverId, lines.length, since);

                if (lines.length > 0) {
                    long fetchedAt = System.currentTimeMillis();
                    LinkedHashSet<String> seen = seenLines.computeIfAbsent(serverId, k -> new LinkedHashSet<>());

                    for (String line : lines) {
                        if (line == null || line.isBlank()) continue;
                        if (!seen.add(line)) {
                            log.debug("[log-poller] skipping duplicate: {}", line);
                            continue;
                        }

                        // Trim dedup set to avoid unbounded memory growth
                        if (seen.size() > MAX_DEDUP_SIZE) {
                            seen.remove(seen.iterator().next());
                        }

                        log.info("[log-poller] processing: {}", line);
                        processor.processLine(serverId, line);
                    }
                    lastSeenTimestamps.put(serverId, fetchedAt);
                }
            } catch (Exception e) {
                log.warn("[log-poller] Error polling server {}: {}", serverId, e.getMessage());
            }
        }
    }
}
