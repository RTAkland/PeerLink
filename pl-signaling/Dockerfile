FROM eclipse-temurin:25-jdk-alpine AS builder

WORKDIR /app

COPY . .

RUN chmod +x ./gradlew && ./gradlew :pl-signaling:build -x test --no-daemon

RUN find pl-signaling/build/libs/ -name "*-all*.jar" -exec cp {} /app/app.jar \;

FROM eclipse-temurin:25-jre-alpine AS runner

WORKDIR /app

COPY --from=builder /app/app.jar ./app.jar

EXPOSE 7879

ENTRYPOINT ["sh", "-c", "java -jar app.jar"]