# TG Forward Bot — Spring Boot SaaS

Telegram kanal postlarini avtomatik ravishda narxga markup qo'shib boshqa kanalga yuboruvchi SaaS bot.

---

## Loyiha tuzilmasi

```
src/main/java/uz/tgforward/
├── TgForwardBotApplication.java        ← Kirish nuqtasi
│
├── config/
│   ├── AppConfig.java                  ← TelegramBotConfig, JwtService, SecurityConfig
│   └── TelegramBotHandler.java         ← Bot buyruqlarini qabul qiladi (/start, /list, ...)
│
├── controller/
│   └── Controllers.java                ← AuthController, ForwardConfigController (REST API)
│
├── domain/
│   ├── AppUser.java                    ← SaaS foydalanuvchisi
│   ├── ForwardConfig.java              ← Asosiy config modeli
│   └── ProcessedPost.java              ← Duplicate oldini olish
│
├── dto/
│   └── Dtos.java                       ← ForwardConfigDto, ParsedProduct, ApiResponse
│
├── exception/
│   └── Exceptions.java                 ← BusinessException, GlobalExceptionHandler
│
├── repository/
│   └── Repositories.java               ← AppUserRepo, ForwardConfigRepo, ProcessedPostRepo
│
├── scheduler/
│   ├── ChannelWatcherScheduler.java    ← Har 30 soniyada kanallarni tekshiradi
│   └── TelegramChannelReader.java      ← Kanaldan yangi postlarni o'qiydi
│
└── service/
    ├── ForwardConfigService.java       ← Config CRUD business logic
    ├── PostParserService.java          ← Regex parsing engine (FORMAT_A, FORMAT_B, CUSTOM)
    ├── PostTextBuilder.java            ← Yangi post matni quradi (markup + header/footer)
    └── TelegramPublisherService.java   ← Kanalga xabar yuboradi (matn / rasm / album)
```

---

## Qo'llab-quvvatlanadigan post formatlari

### FORMAT_A
```
H119 1*4 26        →  MODEL  KAROBKA*DONA  NARX
BXT-M1 1*2 31
KIDILO S10 2*6 45
```

### FORMAT_B
```
Kidilo D9 - 1*1*61     →  MODEL - KAROBKA*DONA*NARX
Kidilo C820 - 1*1*76
```

### CUSTOM
Regex pattern yozasiz. Named group'lar majburiy:
- `(?<model>...)` — mahsulot nomi
- `(?<price>...)` — narx

---

## Ishga tushirish (Linux Server)

### 1. Bot yaratish
[BotFather](https://t.me/BotFather) dan yangi bot oling.

### 2. Botni kanallarga admin qiling
- Har bir **manba kanal** ga botni admin qiling (postlarni o'qishi uchun)
- Har bir **maqsad kanal** ga botni admin qiling (post yozish uchun)

### 3. Server setup (birinchi marta)
```bash
sudo bash deploy/setup.sh
```
Bu script quyidagilarni o'rnatadi: Java 21, PostgreSQL, Redis, systemd service.

### 4. Bot tokenini to'ldirish
```bash
sudo nano /etc/tgforward/tgforward.env
# TELEGRAM_BOT_TOKEN va TELEGRAM_BOT_USERNAME ni yozing
```

### 5. Build va deploy
```bash
./mvnw clean package -DskipTests
sudo bash deploy/deploy.sh
```

### Foydali buyruqlar
```bash
systemctl status tgforward           # holat
systemctl restart tgforward          # qayta ishga tushirish
journalctl -fu tgforward             # real-time loglar
tail -f /var/log/tgforward/app.log   # log fayl
```

### Local development uchun (PostgreSQL va Redis allaqachon ishlayapti)
```bash
export DB_USERNAME=tgforward
export DB_PASSWORD=PAROL
export TELEGRAM_BOT_TOKEN=TOKEN
export TELEGRAM_BOT_USERNAME=USERNAME
export JWT_SECRET=$(openssl rand -hex 32)
./mvnw spring-boot:run
```

---

## REST API

### Autentifikatsiya
```
POST /api/auth/telegram
Body: { "id": 123456789, "first_name": "Ali", "username": "ali" }
Response: { "token": "eyJ...", "planType": "FREE" }
```

Keyingi so'rovlarda:
```
Authorization: Bearer eyJ...
```

### Config boshqarish
```
GET    /api/configs              ← barcha configlar
POST   /api/configs              ← yangi config yaratish
PUT    /api/configs/{id}         ← configni yangilash
PATCH  /api/configs/{id}/activate    ← yoqish
PATCH  /api/configs/{id}/deactivate  ← o'chirish (saqlanadi)
DELETE /api/configs/{id}         ← o'chirish
```

### Config yaratish misoli
```json
POST /api/configs
{
  "sourceChannel": "@diller_telefon",
  "targetChannel": "@mening_do_kon",
  "markupPercent": 20.0,
  "patternType": "FORMAT_A",
  "headerText": "🔥 Yangi keldi!",
  "footerText": "📦 Buyurtma: @admin"
}
```

---

## Telegram Bot buyruqlari

```
/start          — botni ishga tushirish
/list           — barcha configlarni ko'rish
/activate <id>  — configni yoqish (ID ning birinchi 8 belgisi)
/deactivate <id>— configni to'xtatish
/delete <id>    — configni o'chirish
/help           — yordam
```

---

## Planing

| Plan | Config soni | Narx |
|------|-------------|------|
| FREE | 2 ta        | Bepul |
| PRO  | Cheksiz     | Obuna |

---

## Kengaytirish imkoniyatlari

- [ ] Telegram Login Widget (frontend)
- [ ] Webhook mode (polling o'rniga)
- [ ] MediaGroup (album) postlarni birlashtirish
- [ ] Telegram hash verification (`/api/auth/telegram` da)
- [ ] Statistika: nechta post yuborildi, daromad
- [ ] Scheduled report: kunlik/haftalik xabar
- [ ] Rate limiting (per user)
- [ ] Obuna to'lovi (Telegram Stars yoki to'lov tizimi)
