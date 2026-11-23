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

Gradle also configures the repository's other tools. If another tool still
targets the emulator, its metadata can block these commands. In that case,
pass `-DlightSdk.allowAltServerPackage=true` to allow that configuration; this
module still targets `com.lightos` until its own TOML is changed.

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

## SDK 0.2.10 text entry

The editor uses the external Light keyboard app when available and falls back
to the embedded keyboard otherwise. Settings passwords are masked while editing.
Automatic sentence capitalization is disabled for server URLs, passwords, and
recipient addresses; message editors use the SDK's automatic capitalization.
Settings editors use the SDK's per-composition editor key so reopening an editor
does not reuse a keyboard callback tied to an earlier text state.
