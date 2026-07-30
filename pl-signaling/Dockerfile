FROM eclipse-temurin:25-jdk-alpine AS builder

WORKDIR /app

COPY . .

RUN chmod +x ./gradlew && ./gradlew :pl-signaling:linkReleaseExecutableLinuxX64 -x test --no-daemon

FROM alpine:latest AS runner

WORKDIR /app

COPY --from=builder /app/pl-signaling/build/bin/linuxX64/releaseExecutable/pl-signaling.kexe ./pl-signaling.kexe

RUN chmod +x ./pl-signaling.kexe

EXPOSE 7879

ENTRYPOINT ["./pl-signaling.kexe"]