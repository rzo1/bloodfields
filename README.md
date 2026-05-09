```
   ▓▓▒▒░  ▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄  ░▒▒▓▓
  ▓▓▒░    ____  _                 _  __ _      _     _   ░▒▓▓
 ▓▒░▒    | __ )| | ___   ___   __| |/ _(_) ___| | __| |   ▒░▓
 ▒░     |  _ \| |/ _ \ / _ \ / _` | |_| |/ _ \ |/ _` |     ░▒
 ░      | |_) | | (_) | (_) | (_| |  _| |  __/ | (_| |      ░
        |____/|_|\___/ \___/ \__,_|_| |_|\___|_|\__,_|
   ░▒▓                                                      ▓▒░
   ▓▒░     armies clash.   bones break.   crows feast.     ░▒▓
   ▒░▓                                                      ▓░▒
   ░▓▒  †   †   †   ▓▒░  ▒▓░  ░▓▒  ░▒▓   †   †   †      ▒▓░
       ╱│╲ ╱│╲ ╱│╲                              ╱│╲ ╱│╲ ╱│╲
        │   │   │     ▓▒░ blood-soil ░▒▓        │   │   │
       ╱ ╲ ╱ ╲ ╱ ╲                              ╱ ╲ ╱ ╲ ╱ ╲
   ▓▓▒▒░░  ▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀  ░░▒▒▓▓
```

## About

Bloodfield is a top-down auto-battler. You place an army during a deployment phase,
then watch the carnage unfold in real time — heroes rallying, archers volleying,
cavalry crashing into infantry, dragons charring whole columns. Three modes:
**Campaign** (six escalating levels with codes), **Versus** (2-player hot-seat with
hidden deployment), **Skirmish** (you vs. an AI bot). Heroes carry skills that
buff nearby allies. Terrain matters. Weather matters. Structures hold lines.
Corpses don't disappear — they pile up, blacken, get pecked at by crows, and rot.

## Screenshots

![Battlefield](docs/screenshots/battle.png)

*The aftermath of a clash — corpses, blood pools, crows circling.*

![Deployment](docs/screenshots/deployment.png)

*The deployment phase: pick units from the roster, place them in your zone.*

![Fortress wall](docs/screenshots/fortress.png)

*Fortress maps with garrisoned towers and gates that open for approaching units.*

> Drop your own captures into `docs/screenshots/` to populate this section.

## Quickstart

```bash
mvn javafx:run                               # run from source
# or build the fat jar:
mvn -DskipTests package
java -jar target/army-clash-0.1.0-SNAPSHOT-all.jar
```

Java 21 required. The shade plugin produces `army-clash-0.1.0-SNAPSHOT-all.jar`
in `target/` — the artifact ID stays as-is to keep build paths stable.

## Modes

- **Campaign** — Six levels of escalating difficulty. Win a level to receive a
  code that lets you jump back in later without replaying earlier stages.
- **Versus** — Two-player hot-seat. Each player deploys behind a privacy
  blackout, picks a hero skill, and optionally toggles dragon opt-in. Budget is
  configurable from 100 to 10,000 points. Reserves and per-player handicap (HP
  multiplier) round out the matchup.
- **Skirmish** — Single player vs. the bot. Same deployment rules as versus.

## Units

| Unit         | Role            | HP  | Damage | Range | Cost | Notes                                      |
|--------------|-----------------|-----|--------|-------|------|--------------------------------------------|
| INFANTRY     | Melee line      | 50  | 10     | 18    | 10   | Counters archers.                          |
| ARCHER       | Ranged          | 30  | 8      | 180   | 15   | +50% vs cavalry.                           |
| CAVALRY      | Charger         | 80  | 15     | 20    | 25   | +50% vs infantry. Dies to archers.         |
| MAGE         | Ranged AoE      | 35  | 18     | 140   | 30   | Splash 50. Slow, fragile.                  |
| DRAGON       | Boss            | 600 | 40     | 110   | 999  | Fire breath. Opt-in only. Never routs.     |
| NECROMANCER  | Corpse-bender   | 60  | 8      | 130   | 50   | Revives or detonates corpses.              |
| HEALER       | Support         | 40  | 0      | 80    | 35   | Heals nearby allies. No damage.            |
| CATAPULT     | Siege           | 120 | 35     | 240   | 60   | Splash 70. Boulder projectile.             |
| ASSASSIN     | Skirmisher      | 25  | 22     | 18    | 30   | Fast. Armor-piercing.                      |
| GOLEM        | Tank            | 300 | 20     | 22    | 80   | Slow walking wall. Devastating in melee.   |
| PIKEMAN      | Anti-cavalry    | 55  | 11     | 25    | 15   | Spear line.                                |
| GENERAL      | Hero            | 180 | 18     | 22    | 100  | Unique. +25% damage aura. Carries skill.   |

## Hero Skills

The General can be equipped with one of five auras. Buff applies to allies in range.

- **Battle Lust** — Allies nearby deal +25% damage.
- **Iron Discipline** — Allies nearby take 25% less damage.
- **Swift Strike** — Allies nearby attack 30% faster.
- **Vampiric Banner** — Allies nearby heal 20% of damage dealt.
- **Swift Feet** — Allies nearby move 30% faster.

## Maps

- **Open Plains** — Pure grass. Nowhere to hide.
- **Crossing** — A wide river splits the field; one narrow bridge in the middle.
- **Twin Rivers** — Two rivers, bridges on opposite flanks.
- **Deep Woods** — Dense forest patches break sightlines and slow movement.
- **The Gauntlet** — A diagonal river funnels armies through forest chokepoints.
- **Fortress** — Forest ring walls around the player's deployment zone.
- **Crossroads** — Two perpendicular rivers form a cross with central bridges.
- **The Mire** — Forests everywhere. Slow grinding battle.
- **Peninsula** — Three sides of water funnel armies into a tight battlefield.
- **Badlands** — Scattered forest mounds and a serpentine river.
- **Fortress Wall** — Horizontal wall with twin towers and a central gate.

## Weather

- **Clear** — Cloudless skies. No combat effect.
- **Fog** — Thick fog reduces ranged attack range by 30%.
- **Rain** — Soaked ground slows units by 15%.
- **Snow** — Cosmetic. Falls quietly on the dead.
- **Night** — Darkness reduces ranged attack range by 20%.

## Controls

| Key       | Action                                  |
|-----------|-----------------------------------------|
| `1`       | 0.5× speed                              |
| `2`       | 1× speed (normal)                       |
| `3`       | 2× speed                                |
| `4`       | 4× speed                                |
| `P`       | Pause / unpause                         |
| `R` / `B` | Bring in reserves                       |
| `?` / `H` | Toggle controls & help overlay          |
| `ESC`     | Back to main menu                       |
| `ENTER`   | Start campaign / confirm                |
| `SPACE`   | Start campaign (from result screen)     |

## Gore features

- Blood splashes on every kill, scaled to the size of the unit that died.
- Corpses persist on the field — they don't despawn. Necromancers can revive
  them or blow them up (friendly fire included).
- Bloody tile overlay accumulates where the slaughter is heaviest.
- Decomposing remains darken over ~30 seconds before settling into bone piles.
- Severed limbs and stuck arrows litter the battlefield.
- Charred corpses where dragon fire passed through.
- Scorch marks from catapult splash.
- Crows circle, then descend on the casualty fields. Vultures pick at bones.
- Screen shake on heavy impacts. Catapult boulders, dragon strafes, golem
  slams.
- Casualty-darkened vignette — the more bodies, the darker the screen edges.
- Red sky on later campaign levels. The sun gives up.

```
░▒▓ † † † † † † † † † † † † † † † † † † † † † † † † † † † † † † † † † † † † † † ▓▒░
```
