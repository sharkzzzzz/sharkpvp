# Shark PVP — Fabric 1.21.11

Client-side PvP mechanics library for Minecraft 1.21.11.

## API-backed improvements in 0.6.0

- Fabric key mapping API for the configurable Right Shift shortcut.
- Fabric Mod Menu API for the optional configuration entry.
- Fabric resource reload API for runtime mechanic content indexing and `/reload`-friendly content refresh.
- Fabric HUD Element Registry for an optional, zero-network content warning overlay.
- Official Mojang mappings for Minecraft 1.21.11.
- Data-driven JSON remains the source of mechanic content.

The content loader is intentionally lightweight: it indexes local JSON during resource reload and swaps an immutable snapshot, so the gameplay/render path does not repeatedly scan resources.

## Build

Requires JDK 21 and network access for Gradle/Loom dependency resolution.

```text
./gradlew build
```

Output: `build/libs/shark-pvp-0.6.0.jar`


## Resource Packs

Shark PVP 0.6.0 includes an in-client PacksMC catalog browser. It uses the official PacksMC API for search and metadata and keeps pack browsing inside the Minecraft UI. PacksMC's API intentionally does not expose raw file URLs; downloads must flow through the creator-attributed PacksMC download page, so Shark PVP does not bypass that protection or re-host pack files.

Set `packsMcApiKey` in the Shark PVP client config after creating an API key with PacksMC.
