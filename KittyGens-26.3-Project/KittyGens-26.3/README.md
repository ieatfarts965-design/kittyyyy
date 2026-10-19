# KittyGens — Minecraft Java / Paper 26.3

A Paper plugin project targeting the **26.3 API** and **Java 25**.

## Features implemented in this project
- `/spawn` and first-join teleport after an admin sets spawn.
- `/island` creates/teleports to a personal island and opens a menu.
- Island co-op invites: `/island invite <player>`, `/island accept`, `/island leave`.
- Island expansion with `/island expand`; island size is stored per island.
- 28 generator tiers, starting with 3 free Tier 1 generators.
- Tier 1 costs $100; generator purchase costs scale by tier.
- 25 starting generator slots; slot upgrades add one slot.
- Fur items carry persistent data, can be sold with `/sellfur` or the sell menu.
- Every 5 minutes, active generators meow and receive a 5-minute x2 production boost.
- Island/generator/player data saved in `plugins/KittyGens/data.yml`.
- A resource-pack starter folder with 28 generated pixel-art fur textures and model files.

## Build
Requires **JDK 25** and an internet connection to resolve the Paper API.

```bash
gradle build
```

The plugin jar will be in `build/libs/KittyGens-1.0.0.jar`.

A GitHub Actions workflow is included. Push the project to a GitHub repository, then open **Actions → Build KittyGens 26.3** to build it and download the JAR artifact.

## Install
1. Run a Paper 26.3 server with Java 25.
2. Put `KittyGens-1.0.0.jar` into `plugins/`.
3. Restart the server.
4. As an operator, stand where players should arrive and run `/kittygens setspawn`.
5. Players can use `/island`, `/spawn`, `/generators`, and `/sellfur`.

## Commands
- `/island` — create/visit your island.
- `/island invite <player>` — invite a player to your island.
- `/island accept` — accept the most recent invitation.
- `/island leave` — leave a co-op island.
- `/island expand` — expand your island (cost scales with size).
- `/spawn` — teleport to configured spawn.
- `/generators` — open the 28-tier generator shop.
- `/sellfur` — sell all KittyGens fur in your inventory.
- `/kittygens setspawn` — set spawn (admin).
- `/kittygens reload` — reload saved data (admin).

## Resource pack
The `resource-pack/` folder contains individual PNG fur textures and item model JSON files. This is a resource-pack starter; it does not automatically install itself on players. Zip the *contents* of `resource-pack/` (so `pack.mcmeta` is at the root) and host it using your server's resource-pack setting. The generated texture set uses the vanilla `white_dye` model override system with custom model data; if you use a later resource-pack format, adjust `pack_format` and model syntax to that format.

## Important testing note
This project is configured for Paper 26.3, but it has **not been run against a live 26.3 server in this environment**. Use the GitHub Actions build to verify compilation, then test gameplay on a disposable server before using it on a public server. Back up worlds before testing island creation or expansion.
