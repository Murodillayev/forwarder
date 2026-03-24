package uz.tgforward.tdlight;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RecentMediaBuffer — har bir kanal uchun so'nggi media (rasm/video)
 * vaqt tamg'asi bilan saqlaydi.
 */
@Component
@Slf4j
public class RecentMediaBuffer {

    private static final long MAX_AGE_MS      = 10 * 60 * 1000L;
    private static final long MATCH_WINDOW_MS =  5 * 60 * 1000L;
    private static final int  MAX_PER_CHAT    = 50;

    public enum MediaType { PHOTO, VIDEO }

    public record MediaEntry(
        String   tdFileId,       // TDLib int file ID (string formatida)
        MediaType mediaType,     // PHOTO yoki VIDEO
        String   mediaGroupId,  // album bo'lsa bir xil, aks holda null
        long     timestamp,
        long     messageId
    ) {}

    // Yuklab olingandan keyin local path saqlanadi
    // tdFileId → local path
    private final Map<String, String> localPathCache = new ConcurrentHashMap<>();

    private final Map<Long, List<MediaEntry>> buffer = new ConcurrentHashMap<>();

    // ── Qo'shish ─────────────────────────────────────────────
    public void add(long chatId, String tdFileId, MediaType mediaType,
                    String mediaGroupId, long messageId) {
        List<MediaEntry> list = buffer.computeIfAbsent(chatId, k -> new ArrayList<>());
        synchronized (list) {
            list.add(new MediaEntry(tdFileId, mediaType, mediaGroupId,
                System.currentTimeMillis(), messageId));
            if (list.size() > MAX_PER_CHAT) list.remove(0);
        }
        log.debug("Buffer: chatId={}, type={}, groupId={}", chatId, mediaType, mediaGroupId);
    }

    // ── 1-holat: oxirgi 5 daqiqadagi media ───────────────────
    public List<MediaEntry> pollRecent(long chatId) {
        List<MediaEntry> list = buffer.get(chatId);
        if (list == null) return List.of();

        long cutoff = System.currentTimeMillis() - MATCH_WINDOW_MS;
        List<MediaEntry> result = new ArrayList<>();
        synchronized (list) {
            Iterator<MediaEntry> it = list.iterator();
            while (it.hasNext()) {
                MediaEntry e = it.next();
                if (e.timestamp() >= cutoff) { result.add(e); it.remove(); }
            }
        }
        return result;
    }

    // ── 2 va 3-holat: album bo'yicha ─────────────────────────
    public List<MediaEntry> pollByMediaGroup(long chatId, String mediaGroupId) {
        if (mediaGroupId == null) return List.of();
        List<MediaEntry> list = buffer.get(chatId);
        if (list == null) return List.of();

        List<MediaEntry> result = new ArrayList<>();
        synchronized (list) {
            Iterator<MediaEntry> it = list.iterator();
            while (it.hasNext()) {
                MediaEntry e = it.next();
                if (mediaGroupId.equals(e.mediaGroupId())) { result.add(e); it.remove(); }
            }
        }
        return result;
    }

    // ── Local path cache ──────────────────────────────────────
    public void cacheLocalPath(String tdFileId, String localPath) {
        localPathCache.put(tdFileId, localPath);
    }

    public String getCachedPath(String tdFileId) {
        return localPathCache.get(tdFileId);
    }

    // ── Tozalash ─────────────────────────────────────────────
    public void cleanup() {
        long cutoff = System.currentTimeMillis() - MAX_AGE_MS;
        buffer.forEach((chatId, list) -> {
            synchronized (list) { list.removeIf(e -> e.timestamp() < cutoff); }
        });
        buffer.entrySet().removeIf(e -> {
            synchronized (e.getValue()) { return e.getValue().isEmpty(); }
        });
    }
}
