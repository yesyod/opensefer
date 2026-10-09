# App icon

`source.png` is the artwork: an open book with a gold bookmark cord on a rounded navy board.
Everything the apps ship is generated from it — run, from the repository root:

```sh
python3 design/app-icon/make_icons.py design/app-icon/source.png .
```

(Pillow and numpy only.) The stores want a plain square — iOS and Android apply their own rounded
mask — so the script rebuilds the navy board as a smooth full‑bleed field (from the artwork's own
background, extended past its rounded corners and highlight rim) and lifts the book, cord and soft
shadow off it. It prints how closely the two layers reproduce the artwork and checks that the book
stays inside Android's safe zone.

| Output | Where | What |
|---|---|---|
| iOS | `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png` | 1024 × 1024, opaque (the App Store refuses transparency); Xcode derives every other size |
| Android 8+ | `composeApp/src/androidMain/res/mipmap-*/ic_launcher_{background,foreground}.png` + `mipmap-anydpi-v26/ic_launcher*.xml` | adaptive icon, 108 dp layers; the book within the 66 dp safe zone |
| Android 13+ | `mipmap-*/ic_launcher_monochrome.png` | one‑colour layer for themed icons |
| Android 7 | `mipmap-*/ic_launcher.png`, `ic_launcher_round.png` | 48 dp square and round icons |
| Google Play | `design/app-icon/play-store-512.png` | 512 × 512, 32‑bit — upload it in Play Console (Play rounds it) |

Don't edit the generated PNGs by hand: change the artwork (or the script) and regenerate.
