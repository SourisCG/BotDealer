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
| yt-dlp (bundled binary, Phase 4) | pinned per release | Unlicense (PyInstaller bundle: GPLv3+, see its THIRD_PARTY_LICENSES) |
| Deno (bundled binary, Phase 4) | pinned per release | MIT |
| Lombok | 1.18.48 | MIT |

The in-app About screen lists these attributions at runtime (Phase 1).
