# MVPN APK

Small Android 12+ VLESS VPN client with Material You UI.

## Features

- Import and store `vless://` profiles.
- Connect/disconnect through Android `VpnService`.
- Xray-core via `AndroidLibXrayLite` (`app/libs/libv2ray.aar`).
- TUN inbound; no separate `tun2socks` process is required because the bundled Xray library accepts the Android TUN file descriptor.
- Test-key signed debug and release APKs.
- GitHub Actions APK build.

## Build

```bash
gradle assembleRelease
```

The signed APK is created in `app/build/outputs/apk/release/`.
