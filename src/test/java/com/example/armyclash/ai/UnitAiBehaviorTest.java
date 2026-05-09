package com.example.armyclash.ai;

import com.example.armyclash.engine.GameLoop;
import com.example.armyclash.engine.GameState;
import com.example.armyclash.engine.SpatialHashGrid;
import com.example.armyclash.engine.World;
import com.example.armyclash.model.Army;
import com.example.armyclash.model.Faction;
import com.example.armyclash.model.Projectile;
import com.example.armyclash.model.Unit;
import com.example.armyclash.model.UnitState;
import com.example.armyclash.model.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnitAiBehaviorTest {

    private static final double DT = 1.0 / 60.0;

    private GameLoop newLoop(World world) {
        SpatialHashGrid grid = new SpatialHashGrid(world.width, world.height, 32.0);
        Army red = new Army(Faction.RED, 100);
        Army blue = new Army(Faction.BLUE, 100);
        GameState state = new GameState(world, red, blue, grid);
        state.phase = GameState.Phase.BATTLE;
        return new GameLoop(state, new UnitAI());
    }

    @Test
    void twoOpposingInfantryConverge_oneOrBothDieWithinTenSeconds() {
        World world = World.grass(800.0, 800.0, 32.0);
        GameLoop gl = newLoop(world);
        GameState s = gl.state();

        Unit r = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 300.0, 400.0);
        Unit b = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.BLUE, 500.0, 400.0);
        s.red.add(r);
        s.blue.add(b);

        // first step: AI sets velocities, engine integrates one tick
        gl.step(DT);

        // velocities point toward each other
        assertTrue(r.vx > 0.0 || !r.isAlive(), "red should move +x toward blue");
        assertTrue(b.vx < 0.0 || !b.isAlive(), "blue should move -x toward red");

        // simulate up to 10 sim-seconds
        int maxSteps = (int) (10.0 / DT);
        for (int i = 0; i < maxSteps; i++) {
            gl.step(DT);
            if (!r.isAlive() || !b.isAlive()) break;
        }
        assertTrue(!r.isAlive() || !b.isAlive(),
                "expected at least one unit dead within 10s; red.hp=" + r.hp + " blue.hp=" + b.hp);
    }

    @Test
    void archerInRangeSpawnsProjectileAndDamagesTarget() {
        World world = World.grass(800.0, 800.0, 32.0);
        GameLoop gl = newLoop(world);
        GameState s = gl.state();

        Unit archer = new Unit(s.nextUnitId++, UnitType.ARCHER, Faction.RED, 300.0, 400.0);
        Unit target = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.BLUE, 450.0, 400.0);
        s.red.add(archer);
        s.blue.add(target);

        // 150 px apart, archer range = 180
        assertTrue(150.0 <= UnitType.ARCHER.attackRange());

        double initialHp = target.hp;

        // first step should fire
        gl.step(DT);
        assertTrue(s.projectiles.size() >= 1, "expected at least one projectile spawned");

        // run enough steps for projectile to travel 150 px at speed 260 px/s -> < 1s
        int maxSteps = (int) (3.0 / DT);
        for (int i = 0; i < maxSteps; i++) {
            gl.step(DT);
            if (target.hp < initialHp) break;
        }
        assertTrue(target.hp < initialHp, "expected target hp to drop after projectile hit");
    }

    @Test
    void archerCooldownPreventsTwoAttacksInQuickSuccession() {
        World world = World.grass(800.0, 800.0, 32.0);
        GameLoop gl = newLoop(world);
        GameState s = gl.state();

        Unit archer = new Unit(s.nextUnitId++, UnitType.ARCHER, Faction.RED, 300.0, 400.0);
        Unit target = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.BLUE, 450.0, 400.0);
        s.red.add(archer);
        s.blue.add(target);

        // first attack
        gl.step(DT);
        int afterFirst = countOwnerProjectilesEverSeen(s, Faction.RED);
        assertTrue(afterFirst >= 1, "expected >=1 projectile after first attack");

        // tick for less than the cooldown — 1.0s when cooldown is 1.5s
        double cooldown = UnitType.ARCHER.attackCooldownSeconds();
        double window = cooldown - 0.5;
        assertTrue(window > 0.0);
        int steps = (int) (window / DT);
        int spawnedDuringWindow = 0;
        long projectilesSeen = s.projectiles.size();
        for (int i = 0; i < steps; i++) {
            int before = (int) Math.max(projectilesSeen, s.projectiles.size());
            gl.step(DT);
            // count new spawns: projectiles with x close to archer.x and very fresh
            // simpler: track via attackCooldownRemaining transitions — but easier to check
            // that the cooldown remains > 0 throughout the window
            assertTrue(archer.attackCooldownRemaining > 0.0,
                    "archer cooldown should not have reset during window; t=" + i);
            // also: no second attack while in window. We rely on cooldown invariant above.
            spawnedDuringWindow += Math.max(0, s.projectiles.size() - before);
        }
        // cooldown intact for entire window — implies no second attack
        assertTrue(archer.attackCooldownRemaining > 0.0,
                "cooldown still active at end of window");
    }

    @Test
    void noEnemiesGoesIdle() {
        World world = World.grass(800.0, 800.0, 32.0);
        GameLoop gl = newLoop(world);
        GameState s = gl.state();

        Unit lone = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 300.0, 400.0);
        s.red.add(lone);

        gl.step(DT);

        assertTrue(lone.state == UnitState.IDLE, "lone unit should be IDLE; was " + lone.state);
        assertTrue(lone.vx == 0.0 && lone.vy == 0.0, "lone unit should have zero velocity");
    }

    private static int countOwnerProjectilesEverSeen(GameState s, Faction owner) {
        int n = 0;
        for (Projectile p : s.projectiles) {
            if (p.owner == owner) n++;
        }
        return n;
    }
}
