# Signaling server wiki

> This page is about how to set up a private signaling server,
> normally, a public signaling server is sufficient.

# Get started

> There's two ways to deploy server: `Docker` `Binary executable file`

## Example config

```json
{
  // set true if use cloudflare stun and turn server.
  "useCloudflareTurn": true,
  // required only useCloudflareTurn is true
  // Cloudflare turn service token id
  "cloudflareTurnTokenId": "...",
  // required only useCloudflareTurn is true
  // Cloudflare turn service token key
  "cloudflareTurnTokenKey": "...",
  // required
  "cloudflareAccountId": "...",
  // required, create a token with Workers KV read & write permission
  "cloudflareAccountToken": "...",
  // required, Workers KV Namespace
  "cloudflareKvId": "...",
  // optional, if useCloudflareTurn is false, configure your own stun/turn server below
  "customStunConfig": {
    "stunServers": [
      "stun:stun.l.google.com:19302",
      "stun:stun.chat.bilibili.com:3478",
      "stun:stun.syncthing.net:3478"
    ],
    "turnServers": [
      {
        "urls": [
          "turn:turn.example.com?transport=tcp"
        ],
        "username": "username",
        "password": "password"
      }
    ]
  }
}
```

## Docker

```shell
docker run -d \
  --name peerlink-signaling-server \
  -p 7879:7879 \
  -v /path/to/your/local/config.json:/app/config.json \
  ghcr.io/rtakland/peerlink:latest
```

## Binary

```shell
./gradlew :pl-signaling:linkReleaseExecutableLinuxX64  # For linux64
./gradlew :pl-signaling:linkReleaseExecutableMingwX64  # For windows64
```

> Output files at `./pl-signaling/build/bin/{platform}/releaseExecutable/`