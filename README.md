# PeerLink

A Minecraft multiplayer mod based on WebRTC.

This feature is forked from Minecraft snapshot version `26.2-snapshot7`, and it has been removed.

Supported Minecraft version `26.2`

Pre-built Jars in Actions workflow

PeerLink does not have pre-built JARs for `windows-arm64` and `linux-arm32` platforms. If you wish to use PeerLink on
these platforms, please build it from source.

Note: Building is required for the specific platform.

# Implement details

PeerLink use itself signaling server to communicate with other PeerLink clients.

Create a room to get a room ID, share it to your friends, they use room id to join your world.

## NAT Traversal

Direct P2P connectivity is used when NAT conditions permit, bypassing intermediate servers. For restricted networks,
TURN servers act as relays to guarantee successful connections.

## Signaling Servers

Available public signaling servers

| Address                           | Comment                           | Provider                                      |
|-----------------------------------|-----------------------------------|-----------------------------------------------|
| wss://peer.7o.ink                 | Built-in default signaling server | [xiaoman1221](https://github.com/xiaoman1221) |
| wss://peerlink-signaling.rtast.cn | Service server is unstable        | [RTAkland](https://github.com/RTAkland)       |

# Open source

Licensed under Apache-2.0

[LICENSE](LICENSE)