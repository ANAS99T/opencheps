# jniLibs

CI replaces this directory with the official **Stockfish 18** binaries:

| ABI | Source file (from `stockfish-android-armv8.tar` / `…-armv7-neon.tar`) | Installed as |
|---|---|---|
| `arm64-v8a` | `stockfish-android-armv8` | `lib/arm64-v8a/libstockfish.so` |
| `armeabi-v7a` | `stockfish-android-armv7-neon` | `lib/armeabi-v7a/libstockfish.so` |

Android installs files under `lib/<abi>/lib*.so` into `nativeLibraryDir` with executable permissions and on a partition that allows execve, so we can `Runtime.exec()` it directly with no manual unpack.

Stockfish 18 binaries are statically-linked, NDK r27c, NNUE-embedded (~110 MB).
