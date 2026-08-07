# PeerLink

| English | [中文](README-zh.md) |

A Minecraft multiplayer mod based on WebRTC.

This feature is forked from Minecraft snapshot version `26.2-snapshot7`, and it has been removed.

# Implement details

PeerLink use itself signaling server to communicate with other PeerLink clients.

Create a room to get a room ID, share it to your friends, they use room id to join your world.

## NAT Traversal

Direct P2P connectivity is used when NAT conditions permit, bypassing intermediate servers. For restricted networks,
TURN servers act as relays to guarantee successful connections.

## Signaling Servers

Available public signaling servers

| Address                           | Comment                           | Turn Server                                   | Provider                                      |
|-----------------------------------|-----------------------------------|-----------------------------------------------|-----------------------------------------------|
| wss://peer.7o.ink                 | Built-in default signaling server | Mainland China                                | [xiaoman1221](https://github.com/xiaoman1221) |
| wss://peerlink-signaling.rtast.cn | For global users                  | Cloudflare(For global, except Mainland China) | [RTAkland](https://github.com/RTAkland)       |

## TURN Servers

Available STUN/TURN servers

1. Cloudflare STUN/TURN
2. STUN/TURN servers in mainland China assigned by the signaling server

# Open source

Licensed under Apache-2.0

[LICENSE](LICENSE)
