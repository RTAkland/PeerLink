# Signaling server

```shell
# windows64 executable binary  
./gradlew :pl-signalinng:linkReleaseExecutableMingwX64

# linux64 executable binary
./gradlew :pl-signalinng:linkReleaseExecutableLinuxX64
```

## Docker Image for Signaling server

`docker pull ghcr.io/rtakland/peerlink:latest`

> Only Linux64 platform is supported

# Fabric Mod

```shell
./gradlew :pl-minecraft:build
```