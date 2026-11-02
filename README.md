## Factions Plugin with Web Stats, Hex Color Support, and Multilingual Interface
A customizable factions plugin featuring a modern web interface for viewing player statistics, full hex color support, and multi-language capabilities. Designed with future-proof configuration flexibility in mind.

![preview](https://i.imgur.com/czjGRT9.jpeg)

> Note: This plugin is currently in development. If you have ideas, suggestions, or feedback, feel free to contact me via Telegram or Discord!

Telegram: @MRXlllll

Discord: degree7

## Features

- Faction management: create, invite, accept, leave, transfer leadership, remove members, dissolve, rename, recolor.
- Hex (`#RRGGBB`) faction colors.
- Faction chat (`/fchat`).
- Statistics per faction and player: blocks placed/broken, kills, online time and online samples.
- Stats export to an external backend ("ingest") that powers the web interface; optional Discord role sync flag per faction.
- PlaceholderAPI placeholders (optional).
- Languages: Russian (`ru`) and English (`en`); messages are editable in `plugins/Faction/lang/`.
- Paper 26.3 and Folia support. Data is stored locally in SQLite.

## Requirements

- Java 25 and Paper 26.3
- PlaceholderAPI (optional)

## Build

```bash
./gradlew shadowJar
```

The jar is produced in `build/libs/`.

## Commands

| Command | Description | Permission |
|---|---|---|
| `/faction create <name> <#hex>` | Create a faction | `faction.create` |
| `/faction invite <player>` | Invite a player | `faction.invite` |
| `/faction accept` | Accept an invite | `faction.accept` |
| `/faction leave` | Leave your faction | `faction.leave` |
| `/faction transfer <player>` | Transfer leadership | `faction.transfer` |
| `/faction remove <player>` | Remove a member | `faction.remove` |
| `/faction dissolve` | Dissolve your faction | `faction.dissolve` |
| `/faction stats` | Open faction stats | `faction.stats` |
| `/faction rename <old> <new>` | Rename a faction (admin) | `faction.rename` |
| `/faction color <name> <#hex>` | Change faction color (admin) | `faction.color` |
| `/faction delete <name>` | Delete a faction (admin) | `faction.delete` |
| `/faction update <name>` | Push faction stats to the web (admin) | `faction.update` |
| `/fchat <message>` | Faction chat | – |
| `/factionrolesync <enable\|disable\|status> <player>` | Discord role sync flag | `faction.rolesync` |

Every `/faction` subcommand also requires `faction.usage`. All permissions default to `op`; grant the ones
you want regular players to have (for example `faction.usage`, `faction.create`, `faction.invite`,
`faction.accept`, `faction.leave`, `faction.stats`) with your permissions plugin.

## Configuration

See `src/main/resources/config.yml`. The `ingest` section controls sending snapshots to the backend
(`base-url`, `api-key`, intervals); it is disabled by default.
