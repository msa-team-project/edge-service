# 빌드 스테이지: Gradle + JDK 21 (Alpine)
FROM gradle:8.7.0-jdk21-alpine AS builder

WORKDIR /workspace
COPY gradle.* .
COPY build.gradle .
COPY settings.gradle .
COPY src ./src

# Gradle 캐시 최적화
RUN --mount=type=cache,target=/home/gradle/.gradle/caches gradle build -x test --no-daemon

# 런타임 스테이지: glibc 기반 이미지
FROM eclipse-temurin:21-jre-jammy

RUN apt-get update && \
    apt-get install -y --no-install-recommends libstdc++6 && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=builder /workspace/build/libs/*.jar ./app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
