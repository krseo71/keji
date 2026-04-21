# syntax=docker/dockerfile:1.6

# --- Stage 1: Build with Gradle ---
FROM gradle:8.8-jdk21 AS build
WORKDIR /src
COPY settings.gradle.kts build.gradle.kts ./
COPY src ./src
RUN gradle bootJar --no-daemon

# --- Stage 2: Runtime (JRE + Node + Claude Code CLI) ---
FROM eclipse-temurin:21-jre
ENV DEBIAN_FRONTEND=noninteractive \
    TZ=Asia/Seoul
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates gnupg tini \
    && curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && npm install -g @anthropic-ai/claude-code \
    && npm cache clean --force \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /src/build/libs/*.jar app.jar

RUN mkdir -p /data/files /data/chat /tmp/jobs \
    && (getent passwd 1000 && userdel -r -f "$(getent passwd 1000 | cut -d: -f1)" || true) \
    && (getent group 1000 && groupdel "$(getent group 1000 | cut -d: -f1)" || true) \
    && useradd -u 1000 -m -d /home/fileapp fileapp \
    && chown -R fileapp:fileapp /app /data/files /data/chat /tmp/jobs /home/fileapp
USER fileapp
ENV HOME=/home/fileapp

EXPOSE 8080
ENTRYPOINT ["/usr/bin/tini", "--", "java", "-jar", "/app/app.jar"]
