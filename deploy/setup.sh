#!/bin/bash
# setup.sh — Linux serverda birinchi marta ishga tushirish uchun
# Ubuntu 22.04 / Debian 12 uchun mo'ljallangan
# Ishlatish: sudo bash setup.sh

set -e  # xato bo'lsa to'xta

echo "=== TG Forward Bot — Server Setup ==="

# ──────────────────────────────────────────
# 1. Java 21 o'rnatish
# ──────────────────────────────────────────
echo "[1/6] Java 21 o'rnatilmoqda..."
apt-get update -q
apt-get install -y wget apt-transport-https

# Eclipse Temurin (OpenJDK) reposi
wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | \
  gpg --dearmor -o /etc/apt/trusted.gpg.d/adoptium.gpg

echo "deb https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" \
  > /etc/apt/sources.list.d/adoptium.list

apt-get update -q
apt-get install -y temurin-21-jdk
java -version

# ──────────────────────────────────────────
# 2. PostgreSQL o'rnatish
# ──────────────────────────────────────────
echo "[2/6] PostgreSQL o'rnatilmoqda..."
apt-get install -y postgresql postgresql-contrib

systemctl enable postgresql
systemctl start postgresql

# Database va user yaratish
DB_PASS=$(openssl rand -hex 16)
sudo -u postgres psql <<SQL
CREATE USER tgforward WITH PASSWORD '$DB_PASS';
CREATE DATABASE tgforward OWNER tgforward;
GRANT ALL PRIVILEGES ON DATABASE tgforward TO tgforward;
SQL

echo ""
echo "  PostgreSQL user: tgforward"
echo "  PostgreSQL pass: $DB_PASS   ← bu parolni eslab qoling!"
echo ""

# ──────────────────────────────────────────
# 3. Redis o'rnatish
# ──────────────────────────────────────────
echo "[3/6] Redis o'rnatilmoqda..."
apt-get install -y redis-server

# Faqat localhostdan ruxsat
sed -i 's/^bind .*/bind 127.0.0.1/' /etc/redis/redis.conf

systemctl enable redis-server
systemctl start redis-server

echo "  Redis ishga tushdi (localhost:6379)"

# ──────────────────────────────────────────
# 4. Tizim foydalanuvchisi va papkalar
# ──────────────────────────────────────────
echo "[4/6] tgforward user va papkalar yaratilmoqda..."

# Alohida system user (login qila olmaydi)
useradd --system --no-create-home --shell /usr/sbin/nologin tgforward || true

mkdir -p /opt/tgforward
mkdir -p /etc/tgforward
mkdir -p /var/log/tgforward
mkdir -p /var/lib/tgforward/sessions   # TDLight session fayllari

chown tgforward:tgforward /opt/tgforward
chown tgforward:tgforward /var/log/tgforward
chown tgforward:tgforward /var/lib/tgforward/sessions
chmod 700 /var/lib/tgforward/sessions  # Faqat tgforward user o'qiy oladi
chown root:tgforward /etc/tgforward
chmod 750 /etc/tgforward

# ──────────────────────────────────────────
# 5. Environment fayli
# ──────────────────────────────────────────
echo "[5/6] Environment fayli yaratilmoqda..."

JWT_SECRET=$(openssl rand -hex 32)

cat > /etc/tgforward/tgforward.env <<ENV
# TG Forward Bot — Environment variables
# chmod 600 /etc/tgforward/tgforward.env

DB_USERNAME=tgforward
DB_PASSWORD=$DB_PASS
REDIS_PASSWORD=
TELEGRAM_BOT_TOKEN=BU_YERGA_BOT_TOKENINI_YOZING
TELEGRAM_BOT_USERNAME=BU_YERGA_BOT_USERNAME_YOZING
JWT_SECRET=$JWT_SECRET
ENV

chmod 600 /etc/tgforward/tgforward.env
chown root:tgforward /etc/tgforward/tgforward.env

echo ""
echo "  MUHIM: /etc/tgforward/tgforward.env faylini oching"
echo "  TELEGRAM_BOT_TOKEN va TELEGRAM_BOT_USERNAME ni to'ldiring!"
echo ""

# ──────────────────────────────────────────
# 6. systemd service
# ──────────────────────────────────────────
echo "[6/6] systemd service o'rnatilmoqda..."

cp deploy/tgforward.service /etc/systemd/system/tgforward.service
systemctl daemon-reload
systemctl enable tgforward

echo ""
echo "=== Setup tugadi! ==="
echo ""
echo "Keyingi qadamlar:"
echo "  1. nano /etc/tgforward/tgforward.env   → bot token ni yozing"
echo "  2. mvn clean package -DskipTests       → JAR yaratish"
echo "  3. cp target/*.jar /opt/tgforward/app.jar"
echo "  4. systemctl start tgforward"
echo "  5. systemctl status tgforward"
echo "  6. journalctl -fu tgforward            → loglarni ko'rish"
