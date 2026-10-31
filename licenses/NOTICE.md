# Third-party notices

## jump3r — Java port of LAME

GossipAudio vendors the `mp3` and `mpg` packages of
[jump3r](https://github.com/tsichevski/jump3r), a pure-Java port of the LAME MP3 encoder and the
MPGLIB decoder tables, so that MP3 export works on every Android device without native code.

* Paths: `app/src/main/java/ru/wowa/jump3r/mp3/**`, `app/src/main/java/ru/wowa/jump3r/mpg/**`
* Licence: **LGPL-2.1** — full text in [`LGPL-2.1-jump3r-LAME.txt`](LGPL-2.1-jump3r-LAME.txt)
* Modifications made for Android: removed `javax.sound.sampled` dependencies and Eclipse JDT
  annotations, deleted the file/stream helper classes that are not needed by the encoder, kept the
  encoder wiring identical to jump3r's own `LameEncoder`.
* LGPL compliance: the library is shipped as unmodified-source Java inside the APK, the sources are in
  this repository, and relinking is possible by replacing those two packages and rebuilding — see
  [`../docs/BUILD.md`](../docs/BUILD.md).
* Vendoring is reproducible via [`../tools/vendor_jump3r.py`](../tools/vendor_jump3r.py).

## LAME

The algorithms are derived from LAME (LGPL-2.1), © the LAME project authors.

## Fonts, icons, artwork

* Icons: hand-written vector paths in `ui/widgets/Icons.kt` (this project).
* Butterfly, vinyl, waveforms, sparklines, covers: drawn with `Canvas` in this project.
* Logo `GossipAudio.png`: supplied by Beyoneer XYZ.
* Fonts: system fonts only (`sans-serif`, `sans-serif-medium`, `sans-serif-black`, `sans-serif-condensed`).

## Everything else

© 2025 Beyoneer XYZ. All rights reserved for the original code and brand assets.
