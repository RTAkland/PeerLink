# 信令服务器搭建

> 这篇文章是关于如何搭建私有信令服务器, 通常情况下使用公共信令服务器即可

# 快速开始

> 你可以使用以下两种方式来部署: `Docker`, `二进制可执行文件`

## 示例配置文件

```json
{
  // 如果使用cloudflare的turn服务则设置为true
  "useCloudflareTurn": true,
  // 仅在useCloudflareTurn为true时需要配置
  // Cloudflare turn service token id
  "cloudflareTurnTokenId": "...",
  // 仅在useCloudflareTurn为true时需要配置
  // Cloudflare turn service token key
  "cloudflareTurnTokenKey": "...",
  // Cloudflare账户ID, 必须配置
  "cloudflareAccountId": "...",
  // 创建一个Cloudflare 账户 Token 必须包含KV的读写权限, 必须配置
  "cloudflareAccountToken": "...",
  // KV的NamespaceID, 必须配置
  "cloudflareKvId": "...",
  // 仅在useCloudflareTurn为false时需要配置
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

> 需要将配置文件挂载到镜像中

## Binary

```shell
./gradlew :pl-signaling:linkReleaseExecutableLinuxX64  # Linux64 平台
./gradlew :pl-signaling:linkReleaseExecutableMingwX64  # Windows64 平台
```

> 可执行文件生成在 `./pl-signaling/build/bin/{平台名称}/releaseExecutable/`