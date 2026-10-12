# Flashback Redroided

Flashback on Android. Runtime patches so [Flashback](https://modrinth.com/mod/flashback) replays, ImGui UI, file dialogs, exports and FFmpeg recording work on Android Minecraft launchers.

Flashback Redroided is a small addon that patches the parts of Flashback that don't work properly on Android Minecraft launchers. Instead of modifying and redistributing the original Flashback jar, it uses Fabric mixins and extra compatibility code to patch those parts at runtime. It is an unofficial project and is not affiliated with Flashback or Moulberry.

> Need help? Join the Discord: https://discord.gg/dKgFxHKc7T

## Which jar

| Branch | Minecraft | Jar |
| --- | --- | --- |
| `1.21` | 1.21 – 1.21.11 | `fba-1.21-1.21.11-<version>.jar` |
| `26.x` | 26.1 – 26.2 | `fba-26.1-26.2-<version>.jar` |
| `main` | 26.3 | `fba-26.3-<version>.jar` |

Pick the jar that matches your Minecraft version.

## Downloads

- Stable releases: [GitHub Releases](https://github.com/whaltermc/Flashback-Redroided/releases) (published from `v*` tags).
- Dev builds: rolling prereleases `dev-1.21`, `dev-26.x` and `dev-main`, updated on every push.

Both are direct `.jar` downloads — no need to unzip anything.

## Installation

Requires: Fabric Loader, Fabric API, and the original [Flashback](https://modrinth.com/project/4das1Fjq) mod.

1. Install Fabric for Minecraft.
2. Install Fabric API.
3. Install Flashback.
4. Download Flashback Redroided (see Downloads above).
5. Put both `.jar` files in your `mods` folder.
6. Start Minecraft using your Android launcher.

Made for Android launchers such as Mojo/MJLauncher, Amethyst Launcher and ZalithLauncher (1 and 2). Compatibility varies with launcher, LWJGL build and renderer.

## File dialogs

Android has no desktop file-dialog support, so exports and imports are handled in layers:

1. The platform (SDL) file dialog, like on desktop.
2. A tinyfd dialog as backup.
3. A hardcoded path under `flashback/exports/` in the game directory, so exports never fail silently.

Cancelled dialogs stay cancelled — only a failed dialog falls through to the next layer.

## Issues

If something doesn't work, open an issue and include:

- Minecraft version
- Flashback version
- Flashback Redroided version
- Fabric Loader version
- Android device
- Launcher
- Renderer
- Crash log / `latest.log`

## Building

Requires JDK 25 or newer (Fabric Loom 1.18 needs it to run Gradle):

```
./gradlew build
```

The finished jar is in `build/libs/`.

## Links

- Source and issues: https://github.com/Redroided-Team/Flashback-Redroided
- Flashback: https://modrinth.com/mod/flashback
- Website: https://whaltermc.vercel.app
- Discord: https://discord.gg/dKgFxHKc7T

## License

Flashback Redroided's own code is **All Rights Reserved** — see [LICENSE](LICENSE).

Bundled third-party components keep their own licenses (see [NOTICE](NOTICE)):

- [FFmpeg](https://ffmpeg.org) — GPL-3.0-or-later
- [JavaCpp](https://github.com/bytedeco/javacpp) — Apache-2.0
- [ImGui-Java (Redroided-Team fork)](https://github.com/Redroided-Team/imgui-java) — MIT
- [LWJGL tinyfd](https://www.lwjgl.org) — bundled classes only, no natives

Flashback by [Moulberry](https://modrinth.com/mod/flashback) — download it separately and follow its license.
