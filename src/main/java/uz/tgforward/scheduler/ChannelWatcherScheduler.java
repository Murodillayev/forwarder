package uz.tgforward.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uz.tgforward.repository.ForwardConfigRepository;
import uz.tgforward.repository.ProcessedPostRepository;
import uz.tgforward.service.PostParserService;
import uz.tgforward.service.TelegramPublisherService;
import uz.tgforward.tdlight.RecentMediaBuffer;

import java.time.LocalDateTime;

// ─────────────────────────────────────────────────────────
// ChannelWatcherScheduler
// ─────────────────────────────────────────────────────────
@Component
@Slf4j
@RequiredArgsConstructor
public class ChannelWatcherScheduler {

    private final ForwardConfigRepository configRepo;
    private final ProcessedPostRepository processedRepo;
    private final TelegramChannelReader channelReader;
    private final PostParserService parser;
    private final TelegramPublisherService publisher;
    private final RecentMediaBuffer mediaBuffer;

    @Value("${scheduler.watcher.max-messages-per-run:10}")
    private int maxMessages;

    @Scheduled(fixedDelayString = "${scheduler.watcher.interval-ms:30000}")
    public void watch() {
        // TDLight event-driven ishlaydi — bu scheduler hozircha bo'sh
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void cleanup() {
        processedRepo.deleteByProcessedAtBefore(LocalDateTime.now().minusDays(3));
        log.info("Eski processed_posts tozalandi");
    }

    // Har 10 daqiqada media buffer ni tozalash
    @Scheduled(fixedDelay = 600_000)
    public void cleanupMediaBuffer() {
        mediaBuffer.cleanup();
    }
}
