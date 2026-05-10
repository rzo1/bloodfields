# Bloodfield CLI

A headless, JavaFX-free entry point so external programs (LLMs, scripts, tournaments) can drive battles. Lives in `com.github.rzo1.armyclash.cli`.

## Build and run

```bash
mvn -DskipTests package
java -cp target/bloodfield-0.1.0-SNAPSHOT-all.jar com.github.rzo1.armyclash.cli.CliMain help
```

A wrapper is provided at `bin/bloodfield-cli`:

```bash
bin/bloodfield-cli help
```

## Subcommands

### `levels`

Print all campaign levels as a JSON array. Each entry has `number`, `name`, `code`, `mapId`, `weather`, `playerBudget`, `playerCaps`.

```bash
bin/bloodfield-cli levels
```

### `level <n|code>`

Print a single level's full details: terrain ASCII, enemy roster, structures, deployment zone, caps. The argument is `1..N` or a level code (e.g. `SKIRMISH`, `FUCKERY`).

```bash
bin/bloodfield-cli level 5
bin/bloodfield-cli level CABAL
```

### `maps`

Print all map presets as a JSON array (id, name, description).

### `map <id>`

Show ASCII terrain for a single map.

ASCII glyphs:

| Glyph | Meaning |
|-------|---------|
| `.`   | grass |
| `~`   | river |
| `*`   | forest |
| `#`   | wall |
| `T`   | tower |
| `=`   | gate (closed) |
| `_`   | gate (open) |

Units (lowercase = RED, uppercase = BLUE):

| Type | Glyph |
|------|-------|
| INFANTRY | `i` / `I` |
| ARCHER | `a` / `A` |
| CAVALRY | `c` / `C` |
| MAGE | `m` / `M` |
| DRAGON | `d` / `D` |
| NECROMANCER | `n` / `N` |
| HEALER | `h` / `H` |
| CATAPULT | `k` / `K` |
| ASSASSIN | `x` / `X` |
| GOLEM | `o` / `O` |
| PIKEMAN | `p` / `P` |
| GENERAL | `g` / `G` |

### `play <spec>`

Interactive REPL. `<spec>` is `<n>`, a level code, or `map:<mapId>:<budget>` (skirmish-style: empty enemy army).

Reads one JSON command per line from stdin and emits one JSON response per line on stdout.

Commands:

```json
{"op":"place","type":"INFANTRY","x":640,"y":700}
{"op":"remove","x":640,"y":700}
{"op":"set_hero_skill","skill":"BATTLE_LUST"}
{"op":"start"}
{"op":"step","ticks":60}
{"op":"state"}
{"op":"quit"}
```

Example (campaign level 1):

```bash
printf '%s\n' \
  '{"op":"place","type":"INFANTRY","x":640,"y":700}' \
  '{"op":"start"}' \
  '{"op":"step","ticks":3600}' \
  '{"op":"state"}' \
  '{"op":"quit"}' \
  | bin/bloodfield-cli play 1
```

### `sim <spec> --red=<file> [--blue=<file>] [--max-ticks=N]`

Fully scripted run. Loads RED (player) and optionally BLUE (enemy) armies from JSON, then runs to victory or `--max-ticks` (default 18000 = 5 in-game minutes at 60 Hz).

Army file format:

```json
{
  "heroSkill": "BATTLE_LUST",
  "units": [
    {"type": "INFANTRY", "x": 640, "y": 700},
    {"type": "ARCHER",   "x": 600, "y": 750}
  ]
}
```

For campaign specs the enemy is auto-spawned from the level's spawner; pass `--blue=` only when running against a custom roster on `map:` specs.

```bash
bin/bloodfield-cli sim 1 --red=examples/red.json
```

## Notes

- The CLI is JavaFX-free and lives in `com.github.rzo1.armyclash.cli`. It reuses `model/`, `engine/`, and `ai/`.
- Deployment uses the same gating as the JavaFX `DeploymentController`: zone check, terrain passability, budget, unique cap, per-type cap.
- World is fixed at 1280x800 with 32-pixel tiles.
