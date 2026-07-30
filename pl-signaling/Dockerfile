FROM alpine:latest AS runner

RUN apk add --no-cache libstdc++ libgcc

WORKDIR /app

COPY pl-signaling.kexe ./pl-signaling.kexe

RUN chmod +x ./pl-signaling.kexe

EXPOSE 7879

ENTRYPOINT ["./pl-signaling.kexe"]