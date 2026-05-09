# Game Implementation Plan — "Bloodfield"

A top-down auto-battler in the style of the reference screenshot: two armies (red vs blue) face each other on a green battlefield bordered by rivers/forests. The player places their army during a deployment phase, then watches the battle play out.

## 1. Tech Stack

- **Java**: 21 LTS (records, pattern matching, sealed types help with model code)
- **UI**: JavaFX 21 — `Canvas` for the battlefield, scene graph for menus/HUD
- **Build**: Maven with `org.openjfx:javafx-maven-plugin` (handles `--module-path`; run via `mvn javafx:run`)
- **Tests**: JUnit 5 (`junit-jupiter`)
- **Packaging later**: `jpackage` for a native installer (optional, M9+)

### `pom.xml` skeleton

```xml
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.example</groupId>
  <artifactId>army-clash</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <packaging>jar</packaging>

  <properties>
    <maven.compiler.release>21</maven.compiler.release>
    <javafx.version>21.0.4</javafx.version>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>

  <dependencies>
    <dependency>
      <groupId>org.openjfx</groupId>
      <artifactId>javafx-controls</artifactId>
      <version>${javafx.version}</version>
    </dependency>
    <dependency>
      <groupId>org.openjfx</groupId>
      <artifactId>javafx-graphics</artifactId>
      <version>${javafx.version}</version>
    </dependency>
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <version>5.11.0</version>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.openjfx</groupId>
        <artifactId>javafx-maven-plugin</artifactId>
        <version>0.0.8</version>
        <configuration>
          <mainClass>com.example.armyclash.ui.GameApp</mainClass>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

## 2. Project Layout (Maven standard)

```
army-clash/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/example/armyclash/
    │   │   ├── engine/      (GameLoop, GameState, World, SpatialHashGrid)
    │   │   ├── model/       (Unit, UnitType, Faction, Army, Terrain, Projectile)
    │   │   ├── ai/          (UnitAI, Steering, Pathfinding)
    │   │   └── ui/          (GameApp, BattleView, DeploymentController, Renderer, HUD, AssetLoader)
    │   └── resources/
    │       ├── sprites/
    │       └── terrain/
    └── test/
        └── java/com/example/armyclash/
```

## 3. Game Loop & Phases

State machine with four phases:

```
MAIN_MENU → DEPLOYMENT → BATTLE → RESULT → (back to MENU)
```

- **DEPLOYMENT**: time stops. Player drags units from a roster onto the lower half of the field within a "deployment zone". Enemy is pre-placed (later: AI-generated formations).
- **BATTLE**: fixed-timestep simulation tick (60 Hz logic, render at display rate via `AnimationTimer`). Units act autonomously per their AI.
- **RESULT**: freeze, show casualty counts and win/lose.

Single `AnimationTimer` driving:

```
accumulator += dt;
while (accumulator >= TICK) { update(TICK); accumulator -= TICK; }
render(alpha);  // alpha for interpolation
```

## 4. Core Engine Architecture

Lightweight component-style — not full ECS, just enough separation.

```
engine/
  GameLoop          // AnimationTimer, fixed timestep
  GameState         // current phase, both armies, world
  World             // bounds, terrain grid, spatial index
  SpatialHashGrid   // O(1) nearest-enemy queries

model/
  Unit              // pos, vel, hp, faction, stats, target, state (IDLE/MOVING/ATTACKING/DEAD)
  UnitType          // enum/data class: Infantry, Archer, Cavalry... (stats: hp, dmg, range, speed, attackCooldown)
  Faction           // RED, BLUE
  Army              // List<Unit>, deploymentBudget
  Terrain           // tile grid: GRASS, RIVER (impassable), FOREST (slow)
  Projectile        // for ranged units

ai/
  UnitAI            // per-tick: find target → move/attack
  Steering          // seek + simple separation so units don't stack
  Pathfinding       // A* on terrain grid (only if rivers/forests block paths)

ui/
  GameApp           // JavaFX Application entry point
  BattleView        // Canvas + HUD overlay
  DeploymentController  // mouse drag-and-drop placement
  Renderer          // draws terrain, units, projectiles, health bars
  HUD               // unit roster, budget, start button, casualty counter
  AssetLoader       // Image cache (sprites, terrain tile)
```

## 5. Combat Model

Each tick, per unit:

1. If dead → skip.
2. If no target or target dead → query `SpatialHashGrid` for nearest enemy.
3. If target in range → face + attack (respect `attackCooldown`).
4. Else → steer toward target (seek + separation against allies within radius).
5. Apply damage on attack-frame; ranged units spawn `Projectile` entities.

Damage: deterministic for now (`target.hp -= attacker.dmg`). Later: RNG / armor / type counters (e.g. spear > cavalry > archer > infantry > spear).

## 6. Deployment Phase UX

- Player has a points budget (e.g. 100). Each unit type costs N points.
- Drag from a side panel into the player half of the field.
- Snap to a coarse grid (32 px) so formations look tidy.
- Right-click to remove. "Auto-fill" button as a convenience.
- "Start Battle" disabled until ≥1 unit placed.
- Polish: ghost preview while dragging, red tint if outside zone.

## 7. Rendering

- One `Canvas` for the world; redraw fully each frame (fine up to ~2000 sprites).
- Layer order: terrain → unit shadows → units → projectiles → health bars → selection overlay.
- Camera transform via `GraphicsContext.translate/scale` (zoom + pan optional).
- Top-down sprites; for MVP, colored rectangles or circles with a faction-tinted border match the screenshot's "blocky soldier" look.

## 8. Build Order (Milestones)

| #   | Milestone                  | Goal                                                          |
| --- | -------------------------- | ------------------------------------------------------------- |
| M1  | Skeleton                   | Maven + JavaFX runs, empty green canvas, FPS counter          |
| M2  | Units render               | Static red and blue squares on screen                         |
| M3  | Game loop                  | Fixed-timestep update; one unit walks toward another          |
| M4  | Combat                     | Units find nearest enemy, melee, die                          |
| M5  | Spatial grid + separation  | Hundreds of units run smoothly without stacking               |
| M6  | Deployment phase           | Drag-and-drop, budget, start button                           |
| M7  | Terrain                    | River/forest tiles, A* pathfinding around them                |
| M8  | Unit types & ranged        | Archer + Projectile, attack cooldowns, type counters          |
| M9  | UI polish                  | Menu, result screen, casualty counter, sound                  |
| M10 | Levels / AI enemy          | Pre-set enemy formations per level, win progression           |

Ship M1–M5 first — that's already a playable battle.

## 9. Key Design Decisions

- **Tick rate**: 60 Hz logic, decoupled render.
- **Determinism**: not required (no multiplayer planned), so floating-point positions are fine.
- **Units count target**: ~500 per side. Drives the spatial-hash requirement (`O(n²)` target search won't hold up).
- **Coordinate system**: world units = pixels at 1× zoom.
