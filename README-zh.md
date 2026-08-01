# PeerLink

PeerLink是一个基于WebRTC创建的联机模组, 复刻自`26.2-snapshot7`快照版本的一项功能, 目前此功能已被移除.

想要使用PeerLink需要在Actions中找到对应平台的预构建jar.

# 实现细节

信令服务器不再依赖于官方信令服务器, PeerLink重新实现了信令服务器. 目前可以创建房间获得房间ID, 通过此ID可以在另一个客户端加入游戏.

## NAT穿透

在NAT类型优良的情况下双方可以使用P2P功能直连, 无需中间服务器转发流量.

网络受限的情况下引入了TURN服务器来转发流量以实现`必定能成功联机`

## 信令服务器

目前可用的信令服务器地址如下

| 地址                              | 备注               | 来源                                          |
|-----------------------------------|--------------------|-----------------------------------------------|
| wss://peer.7o.ink                 | 内置默认信令服务器 | [xiaoman1221](https://github.com/xiaoman1221) |
| wss://peerlink-signaling.rtast.cn | 不稳定             | [RTAkland](https://github.com/RTAkland)       |

# 开源信息

项目基于Apache-2.0协议开源

你可以在[LICENSE](LICENSE)查看协议明细