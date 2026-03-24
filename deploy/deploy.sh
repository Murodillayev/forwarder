#!/bin/bash
# deploy.sh — yangi versiyani serverga joylash
# Ishlatish: bash deploy.sh
# Loyiha root papkasida ishlatiladi

set -e

echo "=== Deploy boshlandi ==="

# 1. Build
echo "[1/3] Maven build..."
./mvnw clean package -DskipTests -q
echo "  Build muvaffaqiyatli"

# 2. JAR ni serverga nusxalash
echo "[2/3] JAR ko'chirilmoqda..."
cp target/*.jar /opt/tgforward/app.jar
chown tgforward:tgforward /opt/tgforward/app.jar

# 3. Servisni qayta ishga tushirish
echo "[3/3] Servis qayta ishga tushirilmoqda..."
systemctl restart tgforward
sleep 3
systemctl status tgforward --no-pager

echo ""
echo "=== Deploy tugadi! ==="
echo "Loglarni ko'rish: journalctl -fu tgforward"
