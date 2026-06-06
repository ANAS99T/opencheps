# OpenCheps

Android background chess assistant. Watches your screen via **MediaProjection**, locates the chess board, asks **Stockfish** (UCI, NNUE) for the best continuations, and draws **multi-PV arrows** over whatever app is in front.

![CI](https://github.com/Sethdsai/opencheps/actions/workflows/android.yml/badge.svg)

## Features

- 🔐 Asks for overlay + screen-capture permissions
- 🎥 Foreground service of type `mediaProjection`, survives in background
- 🧠 Bundled Stockfish, NNUE on, 2 threads / 32 MB hash (strong + light)
- 🎯 Multi-PV: top 3 candidate moves drawn in green / amber / blue
- 🔁 Auto-detects board orientation from piece distribution (TFLite classifier)
- ♟️ Manual White/Black radio as fallback
- ⚡ Downscales captures to 720 px longest edge, samples ~1×/sec

## Build

1. Install Android Studio Koala (or newer) with SDK 34
2. Open this folder, let Gradle sync
3. Drop a **Stockfish arm64-v8a PIE UCI binary** at `app/src/main/assets/stockfish`
4. *(optional)* Drop a 32x32 RGB TFLite piece classifier at `app/src/main/assets/piece_classifier.tflite` (13 classes: `. P N B R Q K p n b r q k`)
5. Run on a real device, Android 8.0+

## CI

Every push to `main` triggers GitHub Actions which builds a debug APK and uploads it as a workflow artifact. The Stockfish asset is replaced with a placeholder for CI builds.

## Architecture

```
MainActivity ──▶ MediaProjection consent
       │
       ▼
ScreenCaptureService (FG, mediaProjection type)
   │   ├── VirtualDisplay + ImageReader   frames
   │   ├── BoardDetector + PieceClassifier (TFLite)   FEN + bbox
   │   └── StockfishEngine (UCI subprocess)   top-3 PV
                                                       │
                                                       ▼
                                            OverlayService → ArrowOverlayView
```

## License

MIT
