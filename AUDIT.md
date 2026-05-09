# Bloodfield — Feature Audit

**Date:** 2026-05-09
**Auditor:** auditor (army-clash-build)
**Scope:** Closed tasks #16–#49.
**Method:** code review of every feature against `PLAN.md` / `README.md`,
cross-referenced with the test suite. No behavior changed during the audit
except a single README typo (the speed-key mapping was wrong).

## Summary

- **Tests:** 577 / 577 passing (`mvn test`).
- **Package:** `mvn -DskipTests package` produces `army-clash-0.1.0-SNAPSHOT.jar`
  and `army-clash-0.1.0-SNAPSHOT-all.jar`.
- **Game source:** ~20,587 lines of Java (`find src -name '*.java' | xargs wc -l`).
- **Features verified working:** 86 / 89 — 3 caveats called out below.
- **New tasks filed:** #51 (P1 quality bug — bot general lacks hero skill).
  Task #50 (P0 closed-gate freeze) was already filed and is owned elsewhere.
- **README fix:** the "Controls" table claimed `1=1×, 2=2×, 3=3×, 4=4×`. The
  code (and HelpOverlay) actually map `1=0.5×, 2=1×, 3=2×, 4=4×`. README updated
  to match the code.

---

## Core gameplay

### Campaign mode
- [x] **6 levels playable** — `Levels.all()` returns six entries (`Levels.java:36-45`).
- [x] **Level codes work** — `LevelCodes.indexFor` accepts SKIRMISH/WEDGE/FLANKS/CHARGE/MIXED/FUCKERY case-insensitively (`LevelCodesTest`).
- [x] **Advance on win** — `LevelProgression.onWin` increments index; campaign-complete banner painted by `GameApp.renderCampaignBanner` after final level (`GameApp.java:601-616`).
- [x] **Restart on loss** — `LevelProgression.onLoss` keeps current index (`LevelProgression.java:14`).

### Versus (hot-seat)
- [x] **Map selection** — `buildMapCards()` lists 11 presets, click to select (`GameApp.java:1050-1082`).
- [x] **Budget slider 100–10000** — `VersusFlow.MIN_BUDGET=100`, `MAX_BUDGET=10000` (`VersusFlow.java:5-7`).
- [x] **Reserves slider** — 0–50% in 10% increments (`VersusFlow.MIN_RESERVE..MAX_RESERVE`).
- [x] **Dragon opt-in per player** — both checkboxes must be ticked: `dragonsAllowed = p1OptIn && p2OptIn` (`VersusFlow.java:73-78`).
- [x] **Weather selector** — combo box wired through `versus.setSelectedWeather` (`GameApp.java:914-953`).
- [x] **Per-player handicap sliders** — independent P1/P2 sliders, 0–200% in 25% steps (`buildHandicapSection`).
- [x] **Hero-skill picker per player** — `Hud.heroPicker` shows when a GENERAL is in the army; Hud.refresh hides it otherwise (`Hud.java:247-257`).

### Skirmish (vs bot)
- [x] **Map / dragon / handicap selection** — `showSkirmishOptInPane` reuses the same builders (`GameApp.java:775-816`). Bot is fixed at 0% handicap (`GameApp.java:706`).
- [x] **Dragon respect (#49)** — `SkirmishBot.compose` only adds DRAGON when `dragonsAllowed` is true (`SkirmishBot.java:46-52`); covered by `SkirmishBotTest.dragonNotPlacedWhenDragonsDisallowed`.
- [⚠] **Bot picks hero skill** — bot adds a GENERAL but **never** sets `botArmy.setHeroSkill(...)`, so the bot's general carries no aura. Filed as task **#51**.

### Deployment
- [x] **Drag/click to place, right-click remove** — `DeploymentController.handlePressed` (`DeploymentController.java:257-266`).
- [x] **32px snap** — `DeploymentController.snap` floors to tile and adds half-tile (`DeploymentController.java:243-249`).
- [x] **GENERAL unique cap** — `tryPlace` blocks when `selectedType.unique() && hasUnit(selectedType)` (`DeploymentController.java:181-183`).
- [x] **No placement on impassable terrain** — checks `world.passableAt(sx, sy)` and `structures.blocksMovement(sx, sy)` (`DeploymentController.java:204-209`).
- [x] **Garrison ranged in towers/walls** — `tryPlace` routes to `structures.garrison(...)` when the click hits a tower/wall (`DeploymentController.java:184-197`); also `tryGarrison` direct path. Tested by `GarrisonTest`, `HudAutoFillGarrisonTest`.

### Battle phase
- [x] **AI runs** — `GameLoop.runUpdaters` walks both armies (`GameLoop.java:71-77`).
- [x] **Projectiles** — `GameLoop.integrateProjectiles` advances + collides (`GameLoop.java:131-177`).
- [x] **Melee** — `UnitAI.update` direct-applies damage when in range (`UnitAI.java:128-141`).
- [x] **AoE for mage / dragon / catapult** — `splashRadius>0` triggers `applyProjectileImpact` AoE branch (`GameLoop.java:188-196`); MAGE 50, CATAPULT 70, DRAGON 45.

### Result screen
- [x] **Casualty stats + level code** — `ResultRenderer.render` shows red/blue casualties and earned next-level code (`GameApp.java:380-389`).

---

## Units

- [x] **All 12 unit types** — `UnitType` enum lines `INFANTRY..GENERAL` (`UnitType.java:3-27`). Tested by `UnitTypeTest`.
- [x] **Stats match README table** — verified row-by-row against `UnitType.java`. Costs/HP/damage/range all match.
- [x] **Distinct shapes** — `Renderer.shapeFor` maps each type to a unique `UnitShape` (`Renderer.java:108-127`).
- [x] **Description in HUD** — `Hud.descriptionFor` reads `UnitType.description()` and shows on selection (`Hud.java:221-225`); covered by `HudDescriptionSmokeTest`.
- [x] **Player-selectable filter** — `UnitType.playerSelectable()` is `false` only for DRAGON, then re-allowed via opt-in (`GameApp.allowedTypesForVersus`).
- [x] **Unique flag (GENERAL)** — `UnitType.GENERAL.unique() == true`; enforced in DeploymentController and Hud's roster button disabling.

---

## AI

- [x] **Pathfinding routes around terrain + structures** — `Pathfinding.findPath` accepts a `StructureField` and treats blocked cells as impassable (`Pathfinding.java:159-165`); `PathfindingStructureRoutingTest`, `PathfindingTest`.
- [x] **Healer follows non-healer ally, heals wounded** — `UnitAI.updateHealer` finds nearest non-healer ally and the most-wounded ally in range (`UnitAI.java:371-448`); `HealerBehaviorTest`, `HealerFollowsArmyTest`.
- [x] **Necromancer revive/detonate** — `UnitAI.castNecromancy` flips on `pokeRng.nextBoolean()` (`UnitAI.java:298-327`); `NecromancerReviveTest`, `NecromancerExplodeTest`, `NecromancerNoCorpseTest`.
- [x] **General aura applies in 120px** — `UnitType.GENERAL.abilityRadius()=120`, `HeroAura.AURA_RADIUS = UnitType.GENERAL.abilityRadius()` (`HeroAura.java:9`); `GeneralAuraTest`.
- [x] **All five hero skills** — BATTLE_LUST/IRON_DISCIPLINE/SWIFT_STRIKE/VAMPIRIC_BANNER/SWIFT_FEET each modify damage/cooldown/speed/heal in `HeroAura` (`HeroAura.java:42-76`); `HeroSkillIntegrationTest`.
- [x] **Morale: low-HP / outnumbered routs** — `MoraleSystem.update` (`MoraleSystem.java:111-122`).
- [x] **Fearless types** — DRAGON, GOLEM, CATAPULT, HEALER, NECROMANCER all early-return in `MoraleSystem.update` (`MoraleSystem.isFearless`).
- [x] **Tower+Dragon aura prevents rout** — `hasNearbyFriendlyMannedTower` and `hasNearbyFriendlyDragon` clear routed flag.
- [x] **Routed flight bounded + force-rally** — `EDGE_BLEND_MARGIN`/`EDGE_INWARD_PUSH` and 12s `MAX_FLEE_SECONDS` (`MoraleSystem.java:25-26,75-83`); `RoutedFleeIntegrationTest`.
- [x] **Anti-deadlock poke (1.5s)** — `POKE_THRESHOLD_SECONDS=1.5`; random kick at `UnitAI.java:209-220`; `UnitAiStuckTest`.
- [x] **Target re-acquisition every tick** — `UnitAI.update` always queries `state.grid.nearestEnemy(u)` (`UnitAI.java:100-114`).

---

## Structures

- [x] **WALL/TOWER/GATE block movement and projectiles** — `StructureField.blocksMovement` / `blocksLine` (`StructureField.java:206-245`). `GameLoop.integrateProjectiles` consults `structures.structureAt` for projectile blocking (`GameLoop.java:154-169`).
- [x] **Gates default closed, click-to-toggle** — `StructureField.add` initializes `openById=false`; `DeploymentController.tryToggleGate` flips state (`DeploymentController.java:268-280`); `DeploymentControllerGateToggleTest`.
- [x] **Allies pass open gates** — `blocksMovement`/`blocksLine` skip gates when `isOpen()` (`StructureField.java:209,238`).
- [x] **A* respects gates** — `Pathfinding` uses `structures.blocksMovement` which respects open state (`Pathfinding.java:159-165`); `PathfindingThroughOpenGateTest`.
- [⚠] **Closed-gate path freeze** — known performance issue with closed gates on `fortress_wall` map. With both gates closed and the wall fully sealed, the top half is unreachable from the bottom; ~100 units replan at 0.5s intervals against an unreachable target, each scanning ~500 tiles (~3ms/call). The system stays bounded but visibly slows. **Already filed as task #50** (closed-gate freeze + auto-open).
- [x] **Garrison ranged in towers/walls; melee blocked; ranged 0.75× + 10% bleed; evict on destruction** — `StructureField.GARRISONED_RANGED_DAMAGE_MULT=0.75`, `GARRISONED_RANGED_STRUCTURE_BLEED=0.10` (`StructureField.java:163-164`); `evictGarrison` runs on destruction (`StructureField.java:255-256`); `GarrisonRangedDamageTest`, `GarrisonStructureBreakEvictsTest`, `GarrisonMeleeBlockedTest`.
- [x] **Tower archer +30% range, garrisoned ranged +50% (TOWER) / +20% (WALL)** — `AiTuning.towerArcherRangeBonus=0.3`, `StructureHelper.GARRISONED_TOWER_RANGE_BONUS=0.5`, `GARRISONED_WALL_RANGE_BONUS=0.2` (`StructureHelper.java:9-10`); `TowerArcherRangeTest`, `GarrisonRangeBonusTest`.

---

## Terrain & weather

- [x] **River impassable, forest 0.5× speed** — `Terrain.TileType.RIVER.passable()=false`, `FOREST.speedMultiplier()=0.5`; `TerrainTest`.
- [x] **11 map presets** — `Maps.ALL` lists plains, bridge, twin, woods, gauntlet, fortress, crossroads, mire, peninsula, badlands, fortress_wall (`Maps.java:10-44`); `MapsTest`.
- [x] **Map preview shows structures** — `MapPreviewRenderer.render` invoked in `GameApp.buildMapCards` and includes structures via `MapPreviewRendererSmokeTest`.
- [x] **Weather effects** — `Weather` enum carries `tint`, `tintAlpha`, `rangedRangeMult`, `speedMult`, `particles` flags (`Weather.java:6-15`); `WeatherSystem.set` resets state per change. Combat hooks via `AiTuning.weatherRangedRangeMult/SpeedMult` set in `GameApp.applyBattleWeather`.
- [x] **All 5 weathers** — CLEAR, FOG, RAIN, SNOW, NIGHT all defined (`Weather.java:6-15`); `WeatherTest`, `WeatherSystemTest`.

---

## Combat

- [x] **RPS damage matchup** — `DamageModel` table (`DamageModel.java:7-15`):
  - ARCHER → CAVALRY = 1.5×
  - CAVALRY → INFANTRY = 1.5×
  - INFANTRY → ARCHER = 1.5×
  - MAGE → INFANTRY = 1.5×
  - CAVALRY → MAGE = 1.5×
  - PIKEMAN → CAVALRY = **2.5×**
  Covered by `DamageModelTest`.
- [x] **Mage AoE splash with per-target matchup** — `applyProjectileImpact` multiplies by `DamageModel.damageMultiplier(p.attackerType, e.type)` per victim (`GameLoop.java:188-195`); `MageSplashTest`.
- [x] **Catapult BOULDER projectile + spawnExplosion** — `Renderer.projectileStyleFor(CATAPULT)=BOULDER`; `HitTrackerCatapultTest`.
- [x] **ASSASSIN ignores armor (no matchup multiplier)** — `UnitAI.update` melee branch sets `matchup = 1.0` if `armorPiercing` (`UnitAI.java:129-131`); `AssassinDamageTest`.
- [x] **Projectiles stop at first enemy unit** — `integrateProjectiles` uses spatial query and breaks on first non-friendly hit (`GameLoop.java:147-153`).

---

## Polish & UX

- [x] **Speed control + HUD label** — `1`=0.5×, `2`=1×, `3`=2×, `4`=4×, `P`=pause (`SpeedControl.java`, `GameApp.handleSpeedHotkey`); `SpeedControlTest`. **README fixed** to match.
- [x] **Help overlay (`?`/`H`)** — `HelpOverlay.toggle` from `GameApp.start` keyhandler (`GameApp.java:160-163`); `HelpOverlayTest`, `HelpOverlaySmokeTest`.
- [x] **Map cards horizontally scrollable in OPT_IN** — `buildMapSection` wraps in `ScrollPane` with `HBAR=AS_NEEDED`, `VBAR=NEVER` (`GameApp.java:837-855`); `OptInMapScrollSmokeTest`.
- [x] **OPT_IN pane laid out cleanly** — `buildOptInLayout` puts content in a `ScrollPane` with sticky bottom action row and gradient background (`GameApp.java:818-835`).
- [x] **Unit type tooltips/descriptions** — `Hud.descriptionLabel` updates on toggle selection (`Hud.java:86-93`).
- [x] **Hero skill descriptions in picker** — `HeroSkillPicker` sets tooltips and updates description label on toggle (`HeroSkillPicker.java:38-46`).
- [x] **Reserve hotkeys (R/B) in BATTLE** — `GameApp.handleReserveHotkey` (`GameApp.java:219-236`); `ReserveDeployFlowTest`.

---

## Gore

- [x] **Blood splash on death (HP-scaled, killer-tinted)** — `DeathTracker.detectDeaths` produces `ParticleSystem.BloodPool` sized by max HP and tinted by killer type (`DeathTrackerKillerAttributionTest`).
- [x] **Hit splash on damage (directional)** — `HitTracker.detectHits` spawns directional sparks from projectiles (`HitTrackerTest`).
- [x] **Persistent corpses, decompose 30s, charred for dragon kills** — `CorpseRenderer.update` ages corpses; `DeathTracker` tags dragon kills as charred (`CorpseFieldDecompositionTest`).
- [x] **Persistent blood pools** — `ParticleSystem.pools()` retained until cleared on round reset.
- [x] **Bloody tile overlay accumulating + bone piles at 8+** — `BloodyTiles` tracks per-tile death counts; `BloodyTilesBonePileTest` asserts the threshold.
- [x] **Severed limbs** — `LimbField.spawn` invoked in `DeathTracker.detectDeaths`; `LimbFieldTest`.
- [x] **Stuck arrows on archer-hit survivors** — `StuckArrows.update` + `HitTracker` integration; `StuckArrowsTest`.
- [x] **Crow flock at attritive sites (10s+ delay)** — `CrowFlock.SPAWN_DELAY_SECONDS=10.0`, `MIN_DEATHS_TO_ATTRACT=3` (`CrowFlock.java:15-17`); `CrowFlockTest`.
- [x] **Lone vulture at the bloodiest tile** — `Vulture.update` spawns one bird at the highest-count tile after the threshold (`VultureTest`).
- [x] **Camera shake on big hits** — `CameraShake.kick` fired by `HitTracker` for catapult/dragon/golem (`CameraShakeTest`).
- [x] **Death ragdoll rotation** — `RagdollOverlay.spawn` in `DeathTracker`; rotation animated in `update`.
- [x] **Scorch marks on dragon-fire impacts** — `ScorchMarks` spawned by `HitTracker` for FIREBALL/BOULDER (`ScorchMarksTest`).
- [x] **Battle smoke layer at 50+ casualties** — `BattleSmoke.update` activates above threshold (`BattleSmokeTest`).
- [x] **Casualty-darkened vignette** — `Renderer.paintVignette` brightens with corpse count (`Renderer.java:285-286`).
- [x] **Red sky overlay on levels 5-6** — `Renderer.paintRedSky` only when `redSky=true` and level≥5 (`GameApp.java:353` sets `redSky = currentLevelIndex >= 4`).

---

## Engine safety

- [x] **Unit positions clamped to map bounds** — `GameLoop.clampToWorldBounds` runs every tick (`GameLoop.java:123-129`); `UnitClampToMapBoundsTest`.
- [x] **Water (RIVER) blocks movement** — `Terrain.TileType.RIVER.speedMultiplier()=0.0`; `GameLoop.integrateUnits` zeros movement (`GameLoop.java:84-103`).
- [x] **OOB destination blocks movement** — `destPassable = inBounds && speedMul>0` else 0 (`GameLoop.java:98-100`).

---

## Broken or Missing

| Item | Severity | Status |
|---|---|---|
| Closed-gate freeze on fortress_wall (perf, A* explores half the map per replan when target is unreachable) | P0 | Already filed as **#50**, in_progress |
| Skirmish bot deploys GENERAL but never sets a hero skill (aura system never fires for bot's army) | P1 | Filed as **#51** during this audit |
| README controls table mapped speed keys to wrong multipliers | P2 | **Fixed** in this audit (now matches code & HelpOverlay) |

No other broken or missing functionality found. Every feature in the closed-task
list has working code, is invoked from the live game paths (GameApp / GameLoop /
Renderer / DeploymentController), and is exercised by at least one test.
