package uz.tgforward.tdlight;

import it.tdlight.Init;
import it.tdlight.Log;
import it.tdlight.Slf4JLogMessageHandler;
import it.tdlight.client.*;
import it.tdlight.jni.TdApi;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.tgforward.domain.AppUser;
import uz.tgforward.domain.ProcessedPost;
import uz.tgforward.domain.UserSession;
import uz.tgforward.domain.UserSession.SessionStatus;
import uz.tgforward.exception.BusinessException;
import uz.tgforward.repository.ForwardConfigRepository;
import uz.tgforward.repository.ProcessedPostRepository;
import uz.tgforward.repository.UserSessionRepository;
import uz.tgforward.service.PostParserService;
import uz.tgforward.service.R2Service;
import uz.tgforward.service.TelegramPublisherService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
@Slf4j
@RequiredArgsConstructor
public class TDLightSessionManager {

    private final R2Service r2Service;
    @Value("${tdlight.api-id}")
    private int apiId;

    @Value("${tdlight.api-hash}")
    private String apiHash;

    @Value("${tdlight.sessions-dir}")
    private String sessionsDir;

    private final UserSessionRepository sessionRepo;
    private final ForwardConfigRepository configRepo;
    private final ProcessedPostRepository processedRepo;
    private final PostParserService parser;
    private final TelegramPublisherService publisher;
    private final RecentMediaBuffer mediaBuffer;

    private final Map<UUID, SimpleTelegramClient> clients = new ConcurrentHashMap<>();
    private final Map<UUID, List<ChatInfo>>        chatCache     = new ConcurrentHashMap<>();
    private final Map<UUID, Long>                  chatCacheTime = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 60_000;

    // FIX 1: Server yopilayotganini bilish uchun flag
    // PreDestroy da true bo'ladi — Closing/Closed event DB ni o'zgartirmasin
    private volatile boolean shuttingDown = false;

    private SimpleTelegramClientFactory clientFactory;

    // ── Init ─────────────────────────────────────────────────
    @PostConstruct
    public void init() throws Exception {
        Init.init();
        Log.setLogMessageHandler(1, new Slf4JLogMessageHandler());
        clientFactory = new SimpleTelegramClientFactory();
        Files.createDirectories(Paths.get(sessionsDir));

        // 1. R2-DAN TIKLASH (RESTORE)
        List<UserSession> allSessions = sessionRepo.findAll();
        for (UserSession s : allSessions) {
            String userId = s.getOwner().getId().toString();
            String zipName = userId + ".zip";
            Path zipPath = Paths.get(sessionsDir, zipName);
            Path userFolder = Paths.get(sessionsDir, userId);

            try {
                // R2-dan yuklab olish
                r2Service.download(zipName, zipPath);

                if (Files.exists(zipPath)) {
                    log.info("R2-dan sessiya topildi, tiklanmoqda: {}", userId);
                    if (Files.exists(userFolder)) {
                        deleteSessionFiles(s.getOwner().getId()); // Tozalab olamiz
                    }
                    Files.createDirectories(userFolder);
                    unzip(zipPath, userFolder); // Arxivdan chiqarish
                    Files.delete(zipPath); // ZIP-ni o'chirish
                }
            } catch (Exception e) {
                log.warn("Sessiya R2-da topilmadi yoki yuklashda xato: userId={}", userId);
            }
        }

        // 2. TDLight-ni tiklash (Mavjud mantiq)
        for (UserSession s : allSessions) {
            if (s.getStatus() == SessionStatus.ACTIVE) {
                try {
                    buildClient(s.getOwner().getId(), s.getPhoneNumber());
                    log.info("Session tiklandi: userId={}, phone={}", s.getOwner().getId(), s.getPhoneNumber());
                } catch (Exception e) {
                    log.error("Session tiklab bo'lmadi [{}]: {}", s.getOwner().getId(), e.getMessage());
                    s.setStatus(SessionStatus.INACTIVE);
                    sessionRepo.save(s);
                }
            } else if (s.getStatus() == SessionStatus.PENDING_CODE || s.getStatus() == SessionStatus.PENDING_2FA) {
                s.setStatus(SessionStatus.INACTIVE);
                s.setErrorMessage("Server restart bo'ldi. Qayta ulaning.");
                sessionRepo.save(s);
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        shuttingDown = true;
        log.info("TDLight shutting down and backing up to R2...");

        // 1. Clientlarni yopish
        clients.values().forEach(c -> { try { c.close(); } catch (Exception ignored) {} });

        // TDLib fayllarni diskka yozib bo'lishi uchun biroz kutamiz
        try { Thread.sleep(3000); } catch (InterruptedException ignored) {}

        // 2. R2-GA SAQLASH (BACKUP)
        clients.keySet().forEach(userId -> {
            try {
                Path userFolder = Paths.get(sessionsDir, userId.toString());
                Path zipPath = Paths.get(sessionsDir, userId + ".zip");

                if (Files.exists(userFolder)) {
                    log.info("Sessiya ZIP qilinmoqda: {}", userId);
                    zipDirectory(userFolder, zipPath);

                    log.info("R2-ga yuklanmoqda: {}.zip", userId);
                    r2Service.upload(userId + ".zip", zipPath);

                    Files.deleteIfExists(zipPath); // Lokal ZIP-ni o'chiramiz
                }
            } catch (Exception e) {
                log.error("R2-ga backup qilishda xato [{}]: {}", userId, e.getMessage());
            }
        });

        try { clientFactory.close(); } catch (Exception ignored) {}
        log.info("TDLight shutdown tugadi.");
    }

    // ── 1. Auth boshlash ──────────────────────────────────────
    public void startAuth(AppUser owner, String phone) {
        UUID userId = owner.getId();

        // DB ni avval yangilaymiz
        UserSession session = sessionRepo.findByOwner(owner).orElseGet(() ->
                UserSession.builder().owner(owner).phoneNumber(phone)
                        .status(SessionStatus.NONE).build()
        );
        session.setPhoneNumber(phone);
        session.setStatus(SessionStatus.PENDING_CODE);
        session.setErrorMessage(null);
        sessionRepo.save(session);

        // Eski clientni yopamiz
        closeClientSilent(userId);

        // FIX 3: Eski session fayllarini o'chiramiz
        // Aks holda TDLib eski session bilan tiklanib, READY holati keladi
        // va yangi telefon raqam bilan conflict bo'ladi
        deleteSessionFiles(userId);

        buildClient(userId, phone);
        log.info("Auth boshlandi: userId={}, phone={}", userId, phone);
    }

    // ── 2. SMS kod ────────────────────────────────────────────
    public void submitCode(AppUser owner, String code) {
        UUID userId = owner.getId();
        SimpleTelegramClient client = clients.get(userId);
        if (client == null) {
            throw new BusinessException("Session topilmadi. Telefon raqamni qayta kiriting.");
        }
        log.info("Kod yuborilmoqda: userId={}", userId);
        client.send(new TdApi.CheckAuthenticationCode(code.trim()), result -> {
            if (result.isError()) {
                log.error("Kod xato [{}]: {}", userId, result.getError().message);
                updateStatus(userId, SessionStatus.PENDING_CODE,
                        "Noto'g'ri kod. Qayta kiriting.");
            } else {
                log.info("Kod muvaffaqiyatli: userId={}", userId);
            }
        });
    }

    // ── 3. 2FA ────────────────────────────────────────────────
    public void submit2fa(AppUser owner, String password) {
        UUID userId = owner.getId();
        SimpleTelegramClient client = clients.get(userId);
        if (client == null) throw new BusinessException("Session topilmadi.");

        client.send(new TdApi.CheckAuthenticationPassword(password), result -> {
            if (result.isError()) {
                log.error("2FA xato [{}]: {}", userId, result.getError().message);
                updateStatus(userId, SessionStatus.PENDING_2FA,
                        "Noto'g'ri parol. Qayta kiriting.");
            }
        });
    }

    // ── 4. Session o'chirish ──────────────────────────────────
    @Transactional
    public void destroySession(AppUser owner) {
        UUID userId = owner.getId();

        // DB ni avval yangilaymiz, keyin clientni yopamiz
        sessionRepo.findByOwner(owner).ifPresent(s -> {
            s.setStatus(SessionStatus.INACTIVE);
            s.setErrorMessage("Foydalanuvchi tomonidan o'chirildi");
            sessionRepo.save(s);
        });

        closeClientSilent(userId);
        chatCache.remove(userId);
        chatCacheTime.remove(userId);

        // Session fayllarini o'chirish
        deleteSessionFiles(userId);
        log.info("Session o'chirildi: userId={}", userId);
    }

    private void deleteSessionFiles(UUID userId) {
        try {
            Path p = Paths.get(sessionsDir, userId.toString());
            if (Files.exists(p)) {
                Files.walk(p).sorted(Comparator.reverseOrder())
                        .forEach(f -> { try { Files.delete(f); } catch (Exception ignored) {} });
                log.debug("Session fayllari o'chirildi: userId={}", userId);
            }
        } catch (Exception e) {
            log.warn("Session fayllarini o'chirib bo'lmadi: {}", e.getMessage());
        }
    }

    public UserSession getSession(AppUser owner) {
        return sessionRepo.findByOwner(owner)
                .orElseThrow(() -> new BusinessException("Session topilmadi."));
    }

    public boolean isActive(AppUser owner) {
        return sessionRepo.findByOwner(owner)
                .map(s -> s.getStatus() == SessionStatus.ACTIVE)
                .orElse(false);
    }

    // ── Kanallar ──────────────────────────────────────────────
    public List<ChatInfo> fetchUserChats(AppUser owner) {
        UUID userId = owner.getId();
        SimpleTelegramClient client = clients.get(userId);
        if (client == null) throw new BusinessException("Session aktiv emas.");

        Long lastFetch = chatCacheTime.get(userId);
        if (lastFetch != null && System.currentTimeMillis() - lastFetch < CACHE_TTL_MS
                && chatCache.containsKey(userId)) {
            return chatCache.get(userId);
        }

        // Cache ni tozalaymiz — yangi yuklanadi
        chatCache.remove(userId);

        CountDownLatch latch = new CountDownLatch(1);
        client.send(new TdApi.GetChats(new TdApi.ChatListMain(), 200), result -> {
            if (result.isError()) log.error("GetChats xato: {}", result.getError().message);
            try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
            latch.countDown();
        });
        try { latch.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        List<ChatInfo> cached = new ArrayList<>(chatCache.getOrDefault(userId, List.of()));
        cached.sort(Comparator.comparing(ChatInfo::title));
        chatCache.put(userId, cached);
        chatCacheTime.put(userId, System.currentTimeMillis());
        return cached;
    }

    public record ChatInfo(long chatId, String title) {
        public String channelId() { return String.valueOf(chatId); }
    }

    // ── Private: client yaratish ──────────────────────────────
    private void buildClient(UUID userId, String phone) {
        TDLibSettings settings = buildSettings(userId);
        SimpleTelegramClientBuilder builder = clientFactory.builder(settings);

        builder.addUpdateHandler(TdApi.UpdateAuthorizationState.class, upd -> {
            var state = upd.authorizationState;

            if (state instanceof TdApi.AuthorizationStateWaitCode) {
                // FIX 4: Faqat hozirgi DB holati PENDING_CODE bo'lmasa yangilaymiz
                // (startAuth allaqachon PENDING_CODE qilib saqlagan)
                updateStatusIfNot(userId, SessionStatus.PENDING_CODE,
                        Set.of(SessionStatus.PENDING_CODE));
                log.info("WAIT_CODE: userId={}", userId);

            } else if (state instanceof TdApi.AuthorizationStateWaitPassword) {
                updateStatus(userId, SessionStatus.PENDING_2FA, null);
                log.info("WAIT_PASSWORD: userId={}", userId);

            } else if (state instanceof TdApi.AuthorizationStateReady) {
                updateStatus(userId, SessionStatus.ACTIVE, null);
                log.info("Session ACTIVE: userId={}", userId);

            } else if (state instanceof TdApi.AuthorizationStateClosing
                    || state instanceof TdApi.AuthorizationStateClosed) {
                // FIX 1: Server yopilayotganda DB ni o'zgartirmaymiz
                if (!shuttingDown) {
                    // FIX 3: startAuth da closeClientSilent chaqirilganda ham shu event keladi
                    // Agar DB da allaqachon PENDING_CODE bo'lsa — o'zgartirmaymiz
                    sessionRepo.findByOwner_Id(userId).ifPresent(s -> {
                        if (s.getStatus() != SessionStatus.PENDING_CODE
                                && s.getStatus() != SessionStatus.PENDING_2FA) {
                            s.setStatus(SessionStatus.INACTIVE);
                            s.setErrorMessage(null);
                            sessionRepo.save(s);
                            log.warn("Session yopildi: userId={}", userId);
                        }
                    });
                }
                clients.remove(userId);
            }
        });

        builder.addUpdateHandler(TdApi.UpdateNewMessage.class,
                upd -> handleNewMessage(upd.message, userId));

        builder.addUpdateHandler(TdApi.UpdateNewChat.class, upd -> {
            var chat = upd.chat;
            if (chat.type instanceof TdApi.ChatTypeSupergroup s && s.isChannel) {
                chatCache.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>())
                        .add(new ChatInfo(chat.id, chat.title));
            }
        });

        SimpleTelegramClient client = builder.build(AuthenticationSupplier.user(phone));
        clients.put(userId, client);
    }

    // ── Private: yangi xabar handler ─────────────────────────
    private void handleNewMessage(TdApi.Message msg, UUID userId) {
//        if (msg.chatId >= 0 || msg.isOutgoing) return;
        if (msg.chatId >= 0) return;

        if (msg.forwardInfo != null) {
            log.debug("Xabar forward qilingan, tashlab ketildi.");
            return;
        }

        String groupId = msg.mediaAlbumId != 0 ? String.valueOf(msg.mediaAlbumId) : null;
        long chatId = msg.chatId;
        long messageId = msg.id;

        if (msg.content instanceof TdApi.MessagePhoto photo) {
            TdApi.PhotoSize[] sizes = photo.photo.sizes;
            if (sizes == null || sizes.length == 0) return;
            int tdFileId = sizes[sizes.length - 1].photo.id;

            mediaBuffer.add(chatId, String.valueOf(tdFileId),
                    RecentMediaBuffer.MediaType.PHOTO, groupId, messageId);

            String caption = photo.caption != null ? photo.caption.text : null;
            if (caption != null && !caption.isBlank()) {
                handleMediaWithCaption(chatId, messageId, userId,
                        String.valueOf(tdFileId), groupId, caption);
            }
            return;
        }

        if (msg.content instanceof TdApi.MessageVideo video) {
            if (video.video == null) return;
            int tdFileId = video.video.video.id;

            mediaBuffer.add(chatId, String.valueOf(tdFileId),
                    RecentMediaBuffer.MediaType.VIDEO, groupId, messageId);

            String caption = video.caption != null ? video.caption.text : null;
            if (caption != null && !caption.isBlank()) {
                handleMediaWithCaption(chatId, messageId, userId,
                        String.valueOf(tdFileId), groupId, caption);
            }
            return;
        }

        if (msg.content instanceof TdApi.MessageText textMsg) {
            String text = textMsg.text.text;
            if (text.isBlank()) return;

            Thread.ofVirtual().start(() -> {
                List<RecentMediaBuffer.MediaEntry> entries = mediaBuffer.pollRecent(chatId);
                List<MediaLocalFile> localFiles = downloadEntries(entries, userId);
                processText(text, chatId, messageId, userId, localFiles);
            });
        }
    }

    private void handleMediaWithCaption(long chatId, long messageId, UUID userId,
                                        String tdFileId, String groupId, String caption) {
        Thread.ofVirtual().start(() -> {
            List<RecentMediaBuffer.MediaEntry> entries;
            if (groupId != null) {
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                entries = mediaBuffer.pollByMediaGroup(chatId, groupId);
                if (entries.isEmpty()) entries = List.of(new RecentMediaBuffer.MediaEntry(
                        tdFileId, RecentMediaBuffer.MediaType.PHOTO, groupId,
                        System.currentTimeMillis(), messageId));
            } else {
                entries = mediaBuffer.pollRecent(chatId);
                if (entries.isEmpty()) entries = List.of(new RecentMediaBuffer.MediaEntry(
                        tdFileId, RecentMediaBuffer.MediaType.PHOTO, null,
                        System.currentTimeMillis(), messageId));
            }
            List<MediaLocalFile> localFiles = downloadEntries(entries, userId);
            processText(caption, chatId, messageId, userId, localFiles);
        });
    }

    public record MediaLocalFile(String localPath, RecentMediaBuffer.MediaType mediaType) {}

    private List<MediaLocalFile> downloadEntries(
            List<RecentMediaBuffer.MediaEntry> entries, UUID userId) {
        if (entries.isEmpty()) return List.of();
        SimpleTelegramClient client = clients.get(userId);
        if (client == null) return List.of();

        List<MediaLocalFile> result = new ArrayList<>();
        for (RecentMediaBuffer.MediaEntry entry : entries) {
            try {
                int tdFileId = Integer.parseInt(entry.tdFileId());
                String path = downloadSingleFile(client, tdFileId);
                if (path != null) {
                    result.add(new MediaLocalFile(path, entry.mediaType()));
                    log.info("Fayl yuklandi: {} → {}", tdFileId, path);
                } else {
                    log.warn("Fayl yuklanmadi: fileId={}", tdFileId);
                }
            } catch (Exception e) {
                log.error("downloadEntries xatosi: {}", e.getMessage());
            }
        }
        return result;
    }

    private String downloadSingleFile(SimpleTelegramClient client, int tdFileId)
            throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        String[] result = {null};

        client.send(new TdApi.GetFile(tdFileId), getResult -> {
            if (getResult.isError()) {
                log.error("GetFile xato [{}]: {}", tdFileId, getResult.getError().message);
                latch.countDown();
                return;
            }
            TdApi.File file = getResult.get();
            if (file.local.isDownloadingCompleted && !file.local.path.isBlank()) {
                result[0] = file.local.path;
                latch.countDown();
                return;
            }
            client.addUpdateHandler(TdApi.UpdateFile.class, upd -> {
                if (upd.file.id == tdFileId && upd.file.local.isDownloadingCompleted
                        && !upd.file.local.path.isBlank()) {
                    result[0] = upd.file.local.path;
                    latch.countDown();
                }
            });
            client.send(new TdApi.DownloadFile(tdFileId, 1, 0, 0, false), dlResult -> {
                if (dlResult.isError()) {
                    log.error("DownloadFile xato [{}]: {}", tdFileId, dlResult.getError().message);
                    latch.countDown();
                    return;
                }
                TdApi.File dlFile = dlResult.get();
                if (dlFile.local.isDownloadingCompleted && !dlFile.local.path.isBlank()) {
                    result[0] = dlFile.local.path;
                    latch.countDown();
                }
            });
        });

        latch.await(30, TimeUnit.SECONDS);
        return result[0];
    }

    private void processText(String text, long chatId, long messageId,
                             UUID userId, List<MediaLocalFile> mediaFiles) {
        sessionRepo.findByOwner_Id(userId).ifPresent(session ->
                configRepo.findAllByActiveTrue().stream()
                        .filter(c -> c.getOwner().getId().equals(session.getOwner().getId()))
                        .filter(c -> matchesChannel(chatId, c.getSourceChannel()))
                        .forEach(config -> {
                            if (processedRepo.existsByTelegramMessageIdAndSourceChannel(
                                    messageId, config.getSourceChannel())) return;
                            var products = parser.parse(text, config);
                            if (products.isEmpty()) return;
                            try {
                                publisher.publish(config, products, mediaFiles);
                                log.info("Post yuborildi: {} → {} ({} media)",
                                        config.getSourceChannel(), config.getTargetChannel(), mediaFiles.size());
                            } catch (Exception e) {
                                log.error("Publish xatosi: {}", e.getMessage());
                            }
                            processedRepo.save(ProcessedPost.builder()
                                    .telegramMessageId(messageId)
                                    .sourceChannel(config.getSourceChannel())
                                    .config(config).parsed(true).build());
                        })
        );
    }

    // ── Helpers ───────────────────────────────────────────────
    private boolean matchesChannel(long chatId, String source) {
        if (source == null) return false;
        try { return chatId == Long.parseLong(source); }
        catch (NumberFormatException e) { return false; }
    }

    private TDLibSettings buildSettings(UUID userId) {
        Path base = Paths.get(sessionsDir, userId.toString());
        try {
            Files.createDirectories(base.resolve("db"));
            Files.createDirectories(base.resolve("files"));
        } catch (Exception ignored) {}
        TDLibSettings s = TDLibSettings.create(new APIToken(apiId, apiHash));
        s.setDatabaseDirectoryPath(base.resolve("db"));
        s.setDownloadedFilesDirectoryPath(base.resolve("files"));
        return s;
    }

    private void updateStatus(UUID userId, SessionStatus status, String error) {
        sessionRepo.findByOwner_Id(userId).ifPresent(s -> {
            s.setStatus(status);
            s.setErrorMessage(error);
            sessionRepo.save(s);
        });
    }

    // FIX 4: Faqat belgilangan holatlardan boshqa holatda bo'lsa yangilaydi
    private void updateStatusIfNot(UUID userId, SessionStatus newStatus,
                                   Set<SessionStatus> skipIfCurrent) {
        sessionRepo.findByOwner_Id(userId).ifPresent(s -> {
            if (!skipIfCurrent.contains(s.getStatus())) {
                s.setStatus(newStatus);
                s.setErrorMessage(null);
                sessionRepo.save(s);
            }
        });
    }

    // FIX 3: Client yopiladi lekin Closing eventi DB ni o'zgartirmasin deb
    // shuttingDown ni vaqtincha true qilmaymiz — buning o'rniga
    // Closing event handler da PENDING holat tekshiriladi
    private void closeClientSilent(UUID userId) {
        SimpleTelegramClient c = clients.remove(userId);
        if (c != null) { try { c.close(); } catch (Exception ignored) {} }
    }

    private void zipDirectory(Path source, Path target) throws IOException {
        try (ZipOutputStream zs = new ZipOutputStream(Files.newOutputStream(target))) {
            Files.walk(source)
                    .filter(path -> !Files.isDirectory(path))
                    .forEach(path -> {
                        ZipEntry zipEntry = new ZipEntry(source.relativize(path).toString());
                        try {
                            zs.putNextEntry(zipEntry);
                            Files.copy(path, zs);
                            zs.closeEntry();
                        } catch (IOException e) { log.error("Zip error: {}", e.getMessage()); }
                    });
        }
    }

    private void unzip(Path zipFile, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path newPath = targetDir.resolve(entry.getName());
                Files.createDirectories(newPath.getParent());
                Files.copy(zis, newPath);
                zis.closeEntry();
            }
        }
    }
}
