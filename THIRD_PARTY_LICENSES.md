# Third-party licenses

BotDealer itself is **GPL-3.0** (see `LICENSE`). The table below lists the major
bundled dependencies and their licenses. The full inventory (including transitive
dependencies) is regenerated before every release from the Maven dependency tree.

| Component | Version | License |
|---|---|---|
| Spring Boot / Spring Framework | 4.0.6 | Apache-2.0 |
| Hibernate ORM | via Spring Boot BOM | LGPL-2.1 |
| H2 Database | via Spring Boot BOM | MPL-2.0 / EPL-1.0 |
| JDA (Java Discord API) | 6.7.0 | Apache-2.0 |
| JDAVE (Discord Audio & Video Encryption) + natives | 0.1.8 | Apache-2.0 (+ libdave, see below) |
| libdave (Discord, via JDAVE natives) | bundled in JDAVE | see JDAVE repo |
| udpqueue natives (MinnDevelopment) | 0.2.12 | Apache-2.0 |
| opus-java (MinnDevelopment) | 1.1.1 | BSD-3-Clause |
| LavaPlayer (lavalink-devs fork) | 2.2.7 | Apache-2.0 |
| youtube-source (lavalink-devs, `v2` module) | 1.18.2 | Apache-2.0 |
| piper-jni (jvoice-project, includes Piper + espeak-ng + ONNX Runtime) | 1.4.1 | **GPL-3.0** (combined work; this is why BotDealer is GPL-3.0) |
| Piper neural voices (rhasspy/piper-voices, e.g. es_ES-sharvard, en_US-lessac, en_US-libritts_r) | various | MIT/CC-BY (per voice, see voice `.onnx.json` + HuggingFace pages) |
| java-keyring | 1.0.4 | Apache-2.0 (check artifact POM at release time) |
| JavaFX (OpenJFX) | 25.0.4 | GPL-2.0 with Classpath Exception |
| yt-dlp (bundled binary) | 2026.08.19 at the time of writing | Unlicense (the PyInstaller bundle is GPLv3+, see its THIRD_PARTY_LICENSES) |
| Deno (bundled binary) | 2.9.7 at the time of writing | MIT |
| Lombok | 1.18.48 | MIT |

The in-app About screen lists these attributions at runtime.

## What actually ships in the installers

| | |
|---|---|
| Java runtime | built by jpackage/jlink from the JDK used to package |
| Application + dependencies | the jars listed above |
| `voices/` | three Piper voices from `rhasspy/piper-voices` (see each voice's `.onnx.json` for its own license) |
| `engines/` | `yt-dlp` and `deno` binaries, downloaded and checksum-verified at build time |
| At runtime only | yt-dlp fetches its JavaScript challenge solver from its own release page when the reinforced engine needs it; it runs inside Deno, never in the JVM (see `docs/SECURITY.md`) |

Because Piper and its espeak-ng phonemizer are GPL-3.0, the distributed application as a
whole is GPL-3.0.
