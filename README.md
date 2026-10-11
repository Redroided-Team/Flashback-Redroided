# Flashback Redroided

Android compatibility patches for [Flashback](https://modrinth.com/mod/flashback).

Flashback Redroided is a small addon that patches parts of Flashback that don't work properly on Android Minecraft launchers.

### Installation

1. Install Fabric for Minecraft.
2. Install Fabric API.
3. Install the original [Flashback](https://modrinth.com/project/4das1Fjq) mod.
4. Download Flashback Redroided.
5. Put both ".jar" files in your "mods" folder.
6. Start Minecraft using your Android launcher.

### Android Launchers

It is mainly made for Android Minecraft launchers such as:

- Mojo/MJLauncher
- Amethyst Launcher
- ZalithLauncher 2
- Zalithlauncher

Compatibility can vary depending on the launcher, LWJGL build and renderer.

### Exports

Android doesn't have the same desktop file-dialog support used by Flashback.

Flashback Redroided changes the export handling so files can be saved directly to the Minecraft game directory.

Exports are normally stored in:

flashback/exports/

### Why?

Some parts of Flashback rely on desktop libraries or functionality that isn't available on Android.

Instead of modifying and redistributing the original Flashback jar, this project uses Fabric mixins and additional compatibility code to patch those parts at runtime.

### Credits

[Flashback](https://modrinth.com/mod/flashback)
Created by "Moulberry".

Flashback Redroided is an unofficial project and is not affiliated with Flashback or Moulberry unless explicitly stated.

Please download Flashback separately and follow its license.

### Links

- Website: https://whaltermc.vercel.app
- Source: https://github.com/Redroided-Team/Flashback-Redroided
- Flashback: https://modrinth.com/mod/flashback

### Issues

If something doesn't work, open an issue and include:

- Minecraft version
- Flashback version
- Flashback Redroided version
- Fabric Loader version
- Android device
- Launcher
- Renderer
- Crash log / "latest.log"

## License

Flashback Redroided's own code is **All Rights Reserved** — see [LICENSE](https://github.com/Redroided-Team/Flashback-Redroided/blob/main/LICENSE).

Bundled third-party components keep their own licenses (see `NOTICE`):

- [FFmpeg](https://ffmpeg.org) — GPL-3.0-or-later
- [JavaCpp](https://github.com/bytedeco/javacpp) — Apache-2.0
- [ImGui-Java (Redroided-Team fork)](https://github.com/Redroided-Team/imgui-java) — MIT

## Credits & Dependencies
[Flashback](https://modrinth.com/mod/flashback), see [LICENSE](https://github.com/Moulberry/Flashback/blob/master/LICENSE.md)

[ImGui-Java (Redroided-Team fork)](https://github.com/Redroided-Team/imgui-java), see [LICENSE](https://github.com/Redroided-Team/imgui-java/blob/main/LICENSE)

[JavaCpp](https://github.com/bytedeco/javacpp), see [LICENSE](https://github.com/bytedeco/javacpp/blob/master/LICENSE.txt)

[FFMPEG](https://github.com/ffmpeg/ffmpeg), see [LICENSE](https://www.ffmpeg.org/legal.html)
