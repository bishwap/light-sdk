# OpenBubbles

A native Light Phone III client for an external BlueBubbles server. Configure
the server's HTTPS URL and password in Settings. Attachments are displayed as
placeholders; live refresh uses push notifications and manual refresh rather
than a WebSocket connection.

## Build for LightOS

The checked-in `lighttool.toml` targets `com.lightos`:

```sh
./gradlew :examples:openbubbles:assembleDebug
./gradlew check
```

The debug APK is `build/outputs/apk/debug/openbubbles-debug.apk` within this
module. The application ID remains `com.thelightphone.openbubbles`.

## Build for the emulator

Follow the [system-app emulator setup](../../docs/system_app/README.md).
Temporarily change this module's `lighttool.toml` to
`serverPackage = "com.thelightphone.sdk.emulator"`, then opt in to the alternate
server package:

```sh
./gradlew :examples:openbubbles:assembleDebug -DlightSdk.allowAltServerPackage=true
./gradlew check -DlightSdk.allowAltServerPackage=true
```

Restore `serverPackage = "com.lightos"` before committing or building for a phone.
The flag permits an alternate server package; it does not change the package
embedded in the APK on its own.
