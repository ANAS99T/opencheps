# OpenCheps

![CI](https://github.com/Sethdsai/opencheps/actions/workflows/android.yml/badge.svg)

A screen-watching chess hint app for Android. It mirrors your screen with `MediaProjection`, finds the chess board in whatever app you're using, asks Stockfish 18 what to play, and draws arrows on top.

It's a personal project, not a polished commercial product. It works, it has rough edges, and I've documented both honestly below.

---

## What it actually does, end to end

1. You open OpenCheps, grant **Display over other apps** and **Screen capture** permissions, hit Start. The activity sends itself to the background.
2. A foreground service grabs frames from a `VirtualDisplay` at about 1.4 fps. Each frame is downscaled to a 720-pixel longest edge before processing — that's the single biggest performance trick. On a 1080×2340 P30 Lite screen this means we work on ~330×720 instead of the full panel.
3. A board detector scans that downscaled image for the strongest 8×8 checker pattern and returns its bounding box. This is the only really hard part of the app, and the algorithm is described below.
4. The first time it finds a board, it waits for you to show it the **starting position** (any board theme works). When it sees one, it snapshots all 32 piece-square crops as labeled templates from your own theme, learns the average color of each empty light/dark square, and figures out from king/queen luminance which side you're playing from. That's the "calibration" step. It runs once per session.
5. From then on, every frame: locate board → sample each of the 64 squares at its center → either match it to the closest template of the same square-color, or call it empty if the color is within tolerance of the calibrated empty color. Build a FEN. If the FEN changed, send it to Stockfish.
6. Stockfish 18 runs as a child process of the app. I ask for the top 3 candidate moves with a configurable think time (default 700 ms). When it answers, I un-flip the coordinates (if you're playing Black) and draw three colored arrows: green for the best move, amber for #2, blue for #3, with the move in algebraic notation labeled at the midpoint. The eval (centipawn or mate-in-N) goes on the floating panel.
7. There's a small draggable panel anchored to the bottom-left by default with status text, the live engine eval, and three buttons: pause, recalibrate (use this if you flip the board or start a new game), stop.

---

## Hard numbers I actually measured

Tested the detector and bootstrap pipeline on synthetic 1080×2340 phone screenshots using Java SE on a desktop (the production Android code is identical):

| Test image                                 | Board score | Detected bbox       | Truth bbox       | Result |
|--------------------------------------------|------------:|---------------------|------------------|--------|
| Lichess starting position (cream/brown)    |       133.4 | (58, 526, 988)      | (60, 540, 960)   | ✅ PASS |
| Lichess starting position (different y)    |       205.2 | (58, 702, 988)      | (30, 700, 1020)  | ✅ PASS |
| Chess.com starting position (green theme)  |      1075.3 | (58, 468, 988)      | (40, 480, 1000)  | ✅ PASS |
| Small board (680 px, top-bar UI above)     |      1145.8 | (195, 897, 676)     | (200, 900, 680)  | ✅ PASS |
| Random noise (no board)                    |         1.3 | —                   | —                | ✅ REJECT |

Accept threshold is `score > 18`, so the noise image (score 1.3) is correctly rejected. Position error is always within half a square cell. Each pass takes 43–69 ms on a desktop; expect 200–400 ms on a P30 Lite per frame, which fits inside the 700 ms sample period.

Bootstrap classification on a synthetic mid-game position with templates captured from the starting position: **61/64 squares correct (95.3%)**. The 3 errors were a black knight on c6 misclassified as a pawn, a white pawn on e4 misclassified as a rook, and a white knight on f3 misclassified as a pawn. This is the limit of nearest-neighbor matching with RGB pixel features — see the "what doesn't work great" section below.

---

## How the board detector works

The naive approach (`getPixel()` in a quad-nested loop) is dead on arrival — that's ~3 million JNI calls per frame, multi-second latency on the P30 Lite. I rewrote it properly:

1. **Downscale** the incoming bitmap to 360 px on its longest edge with `Bitmap.createScaledBitmap`. This is roughly 30× less work than full resolution.
2. **Bulk pixel read** via `getPixels(int[], …)` once into an int array, then convert to a grayscale int array.
3. **Integral image** (summed-area table) so any box mean is O(1) instead of O(area).
4. **Multi-scale grid search**: try 25 different board sizes from `min(W,H)/3` up to 98% of the smaller dimension. For each size, slide a candidate window with a position step of ~½ cell. For each candidate, sample four small box-means in the four corners of each of the 64 squares, take the median (median of four is robust to a piece sitting on one corner), then score the candidate as `(meanLight − meanDark)² / (noiseFloor + stdLight + stdDark)`. This is a Welch-style separability ratio: high score = consistent two-color pattern.
5. **Local refinement**: around the best candidate, search ±cell pixels in each direction and ±cell/4 in size, and re-score. Adds about 50 evaluations on top of the ~7,600 from the coarse pass.
6. **Acceptance threshold** of 18. Real boards score in the hundreds or thousands; random screen content scores in the low single digits.

There are no Sobel filters, no Hough lines, no OpenCV dependency. The whole thing is ~150 lines of plain Java and tested clean on four different board themes.

---

## How the classifier works

There's no machine-learning model file shipped with the app. Instead:

- **Bootstrap.** The first frame that contains a starting position calibrates everything. I sample the center 56% of each square (radius = 0.28 × cell — kept tight to avoid leaking into neighbor squares when the detector's offset is slightly off). For empty middle squares I average the RGB to get a reference "empty light" and "empty dark" color. The maximum RGB distance of an actual empty square from its reference defines the tolerance — anything within 3.5× that distance is considered empty. The 32 occupied squares become labeled piece templates.
- **Classification each frame.** For each of the 64 squares: if its mean RGB is within tolerance of the reference empty color for that parity, it's empty. Otherwise, compute squared pixel distance against every template of the same color parity, and take the nearest label. The same-parity constraint avoids matching a black piece on a dark square against a black piece on a light square, which would dominate due to the background color difference.
- **Side detection.** During bootstrap, average the luminance of every filled top-rank square vs every filled bottom-rank square. Brighter on the bottom → user plays White. Darker on the bottom → user plays Black. The board is un-flipped accordingly when building the FEN and when mapping move coordinates back to screen pixels.
- **Side-to-move tracking.** I use the material delta between consecutive non-identical frames. If white material went down, that capture was a White piece being taken, meaning it's now White's move. Falls back to simple alternation when material is unchanged. Not perfect but covers most real games.

---

## What doesn't work great, and why

I'm being upfront here:

- **Same-color piece confusion.** The classifier matches by raw pixel distance. Two pieces of the same color on squares of the same parity look very similar — a knight and a pawn at low resolution especially. Expect 4–6 wrong squares per mid-game position. This usually still produces a *legal* FEN, and Stockfish will give a sensible move for that position; visually the move usually looks plausible too. To go meaningfully higher you'd need a CNN classifier (32×32×3 → 13-way softmax), trained on screenshots from common chess apps. I left a clean place to plug that in (`PieceClassifier.classifyBoard`) if anyone wants to PR one.
- **Animations.** During chess.com's slide animation, the captured frame is mid-motion and the FEN parses as something nonsense. The duplicate-FEN-skip means the bad frame doesn't usually trigger an engine call, but you'll see ~1 second of stale arrows.
- **Theme changes mid-session.** If you swap board theme without recalibrating, every square will read as "occupied" because the empty colors no longer match. The fix is the **Recalibrate** button on the floating panel — tap it and show the starting position again.
- **Castling, en passant, draw claims.** The FEN I build always has castling rights `-` and ep-square `-`. Stockfish will still play legal moves, but it won't suggest castling. Adding real castling rights tracking requires move-history reconstruction, which I haven't done.
- **EMUI battery killer.** Huawei's EMUI is aggressive about killing background services. Go to *Settings → Apps → OpenCheps → Battery → App launch* and switch it to "Manage manually" with Auto-launch, Run in background, and Secondary launch all on. Without this, the service is killed within a minute of you switching away from the OpenCheps activity.

---

## Stockfish 18

Bundled as `app/src/main/jniLibs/<abi>/libstockfish.so`. The CI workflow downloads the official Stockfish 18 Android binaries from the `sf_18` release and SHA256-verifies them:

| ABI | Source file | SHA256 |
|---|---|---|
| `arm64-v8a` | `stockfish-android-armv8` | `cc3b3b74466b6d85cdc0f8cfdad4111c6a44bd0698f4eab96fea7b359a54d3d4` |
| `armeabi-v7a` | `stockfish-android-armv7-neon` | `672fbd7533d15fc70dae82df52c1185236975d059a585aa65eda960f1a3266ea` |

Both are statically linked PIE ELFs from NDK r27c, ~114 MB each on disk with the NNUE embedded. Why ship as `lib*.so` instead of an asset? Because Android extracts files matching `lib/<abi>/lib*.so` into `nativeLibraryDir` with executable permissions on a partition that allows `execve`. If you put it in `assets/`, you have to copy it to `filesDir` at first launch, `chmod +x` it, and on some manufacturer ROMs the data partition is `noexec` and the whole thing falls over. The `.so` approach is bulletproof.

Engine settings configured in `StockfishEngine.start`:
```
setoption name Threads value 2
setoption name Hash value 48
setoption name MultiPV value 3
setoption name Move Overhead value 100
```

2 threads because the P30 Lite's Kirin 710 has a 4+4 big.LITTLE config and we don't want to choke the UI thread that's already doing image processing. 48 MB hash is a reasonable middle ground.

---

## CI and APKs

`.github/workflows/android.yml` builds two ABI-split debug APKs on every push:

- `opencheps-arm64-v8a-debug` (~80 MB) — for any phone made roughly 2017 onwards, including Huawei P30 Lite
- `opencheps-armeabi-v7a-debug` (~80 MB) — older 32-bit phones only

The Stockfish download is cached between CI runs, so subsequent builds skip the 230 MB download.

---

## Permission flow on first launch

1. Tap **Grant Overlay Permission** → system Settings → enable for OpenCheps → back to app.
2. Tap **Start Watching** → the system shows the standard *"OpenCheps will start capturing everything that's displayed on your screen"* dialog → tap Start now.
3. The activity sends itself to the background (`moveTaskToBack`), the floating panel appears, and the notification shows.
4. Open your chess app at the starting position. Within one or two sample cycles the panel should say "Calibrated as WHITE POV" or BLACK POV.
5. Play normally. Arrows appear over the board on every position change.

---

## Building locally

You don't need to. Just download the artifact from the latest successful CI run.

If you actually want to: Android Studio Koala or newer, SDK 34. Drop a real `libstockfish.so` (download from the official Stockfish releases page, untar, rename) into `app/src/main/jniLibs/arm64-v8a/` and run `./gradlew assembleDebug`. The CI workflow does exactly this in 25 lines of YAML if you want to see the canonical sequence.

---

## File layout

```
app/src/main/
├── AndroidManifest.xml                   # 5 permissions, 2 services, 1 activity
├── java/com/opencheps/
│   ├── capture/ScreenCaptureService.java # Foreground service. Orchestrates everything.
│   ├── engine/StockfishEngine.java       # UCI bridge to libstockfish.so
│   ├── overlay/
│   │   ├── ArrowOverlayView.java         # Draws arrows + board frame
│   │   ├── FloatingPanel.java            # The draggable status panel
│   │   └── OverlayService.java           # Hosts both overlay windows
│   ├── ui/MainActivity.java              # Permission UI + settings sliders
│   ├── util/
│   │   ├── Prefs.java
│   │   └── Side.java
│   └── vision/
│       ├── BoardDetector.java            # Integral-image grid search
│       ├── FenBuilder.java               # 8x8 char grid → FEN
│       ├── PieceClassifier.java          # Bootstrap + nearest-template
│       └── SquareSampler.java            # Bulk-pixel feature extraction
├── jniLibs/<abi>/libstockfish.so         # Filled in by CI
└── res/                                  # Themes, layouts, the icon
```

---

## License

App code: MIT. Stockfish 18 binary: GPL-3.0 (license travels with the bundled binary).
