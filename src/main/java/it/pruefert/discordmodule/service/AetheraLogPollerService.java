package it.pruefert.discordmodule.service;

import it.pruefert.discordmodule.model.ServerDiscordConfig;
import it.pruefert.discordmodule.repository.ServerDiscordConfigRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Polls Aethera for new server log lines and forwards them to
 * {@link LogProcessingService} for Discord notification dispatching.
 *
 * Uses a dedicated virtual thread loop instead of @Scheduled to guarantee
 * reliable startup regardless of the Spring scheduler configuration.
 */
@Service
public class AetheraLogPollerService {

    private static final Logger log = LoggerFactory.getLogger(AetheraLogPollerService.class);
    private static final int MAX_DEDUP_SIZE = 200;

    private final Map<String, Long>                    lastSeenTimestamps = new ConcurrentHashMap<>();
    private final Map<String, LinkedHashSet<String>>   seenLines          = new ConcurrentHashMap<>();

    private final ServerDiscordConfigRepository configRepo;
    private final AetheraCallbackService        callback;
    private final LogProcessingService          processor;

    @Value("${aethera.log-poll-interval-ms:3000}")
    private long pollIntervalMs;

    private volatile boolean running = false;

    public AetheraLogPollerService(ServerDiscordConfigRepository configRepo,
                                    AetheraCallbackService callback,
                                    LogProcessingService processor) {
        this.configRepo = configRepo;
        this.callback   = callback;
        this.processor  = processor;
    }

    @PostConstruct
    public void start() {
        running = true;
        log.info("[log-poller] starting — poll interval {}ms", pollIntervalMs);
        Thread.startVirtualThread(() -> {
            while (running) {
                try {
                    poll();
                } catch (Exception e) {
                    log.warn("[log-poller] unexpected error in poll loop: {}", e.getMessage());
                }
                try {
                    Thread.sleep(pollIntervalMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            log.info("[log-poller] stopped");
        });
    }

    @PreDestroy
    public void stop() {
        running = false;
    }

    private void poll() {
        List<ServerDiscordConfig> configs = configRepo.findAll();
        if (configs.isEmpty()) {
            log.info("[log-poller] no server configs in DB — configure and save the Discord tab first");
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
                        if (seen.size() > MAX_DEDUP_SIZE) {
                            seen.remove(seen.iterator().next());
                        }
                        log.info("[log-poller] processing: {}", line);
                        processor.processLine(serverId, line);
                    }
                    lastSeenTimestamps.put(serverId, fetchedAt);
                }
            } catch (Exception e) {
                log.warn("[log-poller] error polling server {}: {}", serverId, e.getMessage());
            }
        }
    }
}

