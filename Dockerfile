# 1-bosqich: Build
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests

# 2-bosqich: Run
FROM eclipse-temurin:21-jre
WORKDIR /app

# --- MUHIM O'ZGARIŞ 1: Native kutubxonalarni o'rnatish ---
# TDLight (TDLib) ishlashi uchun bular shart!
RUN apt-get update && apt-get install -y \
    libssl-dev \
    zlib1g-dev \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/target/*.jar app.jar

# --- MUHIM O'ZGARIŞ 2: Papka yaratish ---
RUN mkdir -p /app/sessions && chmod 777 /app/sessions

# --- MUHIM O'ZGARIŞ 3: Muhit o'zgaruvchisi ---
# Dastur sessions-dir ni qayerdan olishni bilishi uchun:
ENV TD_SESSIONS_DIR=/app/sessions

# Xotirani 350MB bilan cheklash juda to'g'ri qaror
ENTRYPOINT ["java", "-Xmx350m", "-jar", "app.jar"]