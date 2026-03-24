package uz.tgforward.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.api.methods.updates.GetUpdates;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// ─────────────────────────────────────────────────────────
// TelegramChannelReader
// ─────────────────────────────────────────────────────────
@Component
@Slf4j
@RequiredArgsConstructor
public class TelegramChannelReader {

    private final OkHttpTelegramClient telegramClient;

    // Bot qabul qilgan barcha yangilanishlar uchun global offset
    private int globalOffset = 0;

    public synchronized List<ChannelPost> fetchNewPosts(String channelUsername, int limit) {
        try {
            List<Update> updates = telegramClient.execute(
                    GetUpdates.builder()
                            .offset(globalOffset)
                            .limit(limit * 3)  // ko'proq olamiz, keyin filtrlaymiz
                            .timeout(0)
                            .build()
            );

            if (updates.isEmpty()) return List.of();

            // Oxirgi update ID + 1 = keyingi offset
            globalOffset = updates.get(updates.size() - 1).getUpdateId() + 1;

            List<ChannelPost> posts = new ArrayList<>();
            for (Update u : updates) {
                if (u.getChannelPost() == null) continue;
                Message msg = u.getChannelPost();

                String chatUsername = msg.getChat().getUserName();
                if (chatUsername == null) continue;

                String normalized = "@" + chatUsername;
                if (!normalized.equalsIgnoreCase(channelUsername)) continue;

                posts.add(new ChannelPost(
                        (long) msg.getMessageId(),
                        extractText(msg),
                        extractPhotoIds(msg)
                ));
            }

            posts.sort(Comparator.comparingLong(ChannelPost::messageId));
            return posts;

        } catch (Exception e) {
            log.error("Kanal o'qishda xatolik [{}]: {}", channelUsername, e.getMessage());
            return List.of();
        }
    }

    private String extractText(Message msg) {
        if (msg.getText() != null) return msg.getText();
        if (msg.getCaption() != null) return msg.getCaption();
        return null;
    }

    private List<String> extractPhotoIds(Message msg) {
        if (msg.getPhoto() == null || msg.getPhoto().isEmpty()) return List.of();
        // Eng katta o'lcham — oxirgi element
        return List.of(msg.getPhoto().get(msg.getPhoto().size() - 1).getFileId());
    }

    public record ChannelPost(long messageId, String text, List<String> photoFileIds) {
    }
}



