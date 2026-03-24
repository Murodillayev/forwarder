package uz.tgforward.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaVideo;
import uz.tgforward.domain.ForwardConfig;
import uz.tgforward.tdlight.RecentMediaBuffer;
import uz.tgforward.tdlight.TDLightSessionManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

// ─────────────────────────────────────────────────────────
// TelegramPublisherService
// ─────────────────────────────────────────────────────────
@Service
@Slf4j
@RequiredArgsConstructor
public class TelegramPublisherService {

    private final OkHttpTelegramClient telegramClient;
    private final PostTextBuilder textBuilder;

    public void publish(ForwardConfig config, List<PostParserService.ParsedProduct> products,
                        List<TDLightSessionManager.MediaLocalFile> mediaFiles) {
        String text = textBuilder.build(products, config);
        String channel = config.getTargetChannel();

        // Mavjud fayllarni filtrlash
        List<TDLightSessionManager.MediaLocalFile> validFiles = (mediaFiles == null ? List.<TDLightSessionManager.MediaLocalFile>of() : mediaFiles)
                .stream()
                .filter(mf -> {
                    File f = new File(mf.localPath());
                    boolean ok = f.exists() && f.length() > 0;
                    if (!ok) log.warn("Fayl topilmadi: {}", mf.localPath());
                    return ok;
                })
                .toList();

        try {
            if (validFiles.isEmpty()) {
                // Faqat matn
                telegramClient.execute(SendMessage.builder()
                        .chatId(channel).text(text).parseMode("HTML").build());

            } else if (validFiles.size() == 1) {
                // Bitta media
                sendSingle(channel, text, validFiles.get(0));

            } else {
                // Album — rasm va video aralash bo'lishi mumkin
                sendAlbum(channel, text, validFiles);
            }

            log.info("Yuborildi → {} ({} mahsulot, {} media)",
                    channel, products.size(), validFiles.size());

        } catch (Exception e) {
            log.error("Yuborishda xatolik [{}]: {}", channel, e.getMessage());
            throw new RuntimeException("Telegram publish xatosi: " + e.getMessage(), e);
        }
    }

    private void sendSingle(String channel, String text, TDLightSessionManager.MediaLocalFile mf) throws Exception {
        File file = new File(mf.localPath());
        if (mf.mediaType() == RecentMediaBuffer.MediaType.VIDEO) {
            telegramClient.execute(SendVideo.builder()
                    .chatId(channel)
                    .video(new InputFile(file))
                    .caption(text).parseMode("HTML")
                    .supportsStreaming(true)
                    .build());
        } else {
            telegramClient.execute(SendPhoto.builder()
                    .chatId(channel)
                    .photo(new InputFile(file))
                    .caption(text).parseMode("HTML")
                    .build());
        }
    }

    private void sendAlbum(String channel, String text, List<TDLightSessionManager.MediaLocalFile> files) throws Exception {
        List<org.telegram.telegrambots.meta.api.objects.media.InputMedia> media = new ArrayList<>();

        for (int i = 0; i < Math.min(files.size(), 10); i++) {
            TDLightSessionManager.MediaLocalFile mf = files.get(i);
            File file = new File(mf.localPath());
            String attachName = "media" + i;
            boolean first = (i == 0);

            if (mf.mediaType() == RecentMediaBuffer.MediaType.VIDEO) {
                InputMediaVideo item = new InputMediaVideo(file, attachName);
                if (first) {
                    item.setCaption(text);
                    item.setParseMode("HTML");
                }
                media.add(item);
            } else {
                InputMediaPhoto item = new InputMediaPhoto(file, attachName);
                if (first) {
                    item.setCaption(text);
                    item.setParseMode("HTML");
                }
                media.add(item);
            }
        }

        telegramClient.execute(SendMediaGroup.builder()
                .chatId(channel).medias(media).build());
    }
}