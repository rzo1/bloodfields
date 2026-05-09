package com.example.armyclash.ai;

import com.example.armyclash.engine.GameLoop;
import com.example.armyclash.engine.GameState;
import com.example.armyclash.engine.SpatialHashGrid;
import com.example.armyclash.engine.Structure;
import com.example.armyclash.engine.StructureType;
import com.example.armyclash.engine.World;
import com.example.armyclash.model.Army;
import com.example.armyclash.model.Faction;
import com.example.armyclash.model.Unit;
import com.example.armyclash.model.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoraleSystemTest {

    private static final double DT = 1.0 / 60.0;
    private static long nextStructureId = 1L;

    private static long nextId() {
        return nextStructureId++;
    }

    private GameState newState(World world) {
        SpatialHashGrid grid = new SpatialHashGrid(world.width, world.height, 32.0);
        Army red = new Army(Faction.RED, 100);
        Army blue = new Army(Faction.BLUE, 100);
        return new GameState(world, red, blue, grid);
    }

    private void rebuildGrid(GameState s) {
        s.grid.clear();
        for (Unit u : s.red.units()) {
            if (u.isAlive()) s.grid.insert(u);
        }
        for (Unit u : s.blue.units()) {
            if (u.isAlive()) s.grid.insert(u);
        }
    }

    @Test
    void unitAtLowHpBecomesRouted() {
        World world = World.grass(800.0, 800.0, 32.0);
        GameState s = newState(world);

        Unit r = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 400.0, 400.0);
        r.hp = r.type.maxHp() * 0.10;
        s.red.add(r);
        rebuildGrid(s);

        MoraleSystem morale = new MoraleSystem();
        morale.update(r, s, DT);

        assertTrue(morale.isRouted(r), "low-HP unit should rout");
        assertTrue(r.routed, "Unit.routed flag should be set");
    }

    @Test
    void unitOutnumberedAtMediumHpBecomesRouted() {
        World world = World.grass(800.0, 800.0, 32.0);
        GameState s = newState(world);

        Unit r = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 400.0, 400.0);
        r.hp = r.type.maxHp() * 0.25; // below OUTNUMBERED_RATIO (0.30) but above LOW_HP_RATIO (0.15)
        s.red.add(r);

        for (int i = 0; i < 3; i++) {
            Unit b = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.BLUE,
                    400.0 + 30.0 * Math.cos(i), 400.0 + 30.0 * Math.sin(i));
            s.blue.add(b);
        }
        rebuildGrid(s);

        MoraleSystem morale = new MoraleSystem();
        morale.update(r, s, DT);

        assertTrue(morale.isRouted(r), "outnumbered medium-HP unit should rout");
    }

    @Test
    void fullHpNotOutnumberedDoesNotRout() {
        World world = World.grass(800.0, 800.0, 32.0);
        GameState s = newState(world);

        Unit r = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 400.0, 400.0);
        s.red.add(r);

        Unit b = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.BLUE, 430.0, 400.0);
        s.blue.add(b);
        rebuildGrid(s);

        MoraleSystem morale = new MoraleSystem();
        morale.update(r, s, DT);

        assertFalse(morale.isRouted(r), "full-HP unit not outnumbered should NOT rout");
        assertFalse(r.routed);
    }

    @Test
    void routedUnitIsolatedRalliesAfterFiveSecondsWithHighHp() {
        World world = World.grass(1600.0, 1600.0, 32.0);
        GameState s = newState(world);

        Unit r = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 400.0, 400.0);
        r.hp = r.type.maxHp() * 0.10;
        s.red.add(r);
        rebuildGrid(s);

        MoraleSystem morale = new MoraleSystem();
        morale.update(r, s, DT);
        assertTrue(morale.isRouted(r));

        r.hp = r.type.maxHp() * 0.80;

        rebuildGrid(s);
        int steps = (int) (5.5 / DT);
        for (int i = 0; i < steps; i++) {
            morale.update(r, s, DT);
        }

        assertFalse(morale.isRouted(r), "routed unit isolated 5s+ with HP > 50% should rally");
    }

    @Test
    void dragonNeverRouts() {
        World world = World.grass(800.0, 800.0, 32.0);
        GameState s = newState(world);

        Unit dragon = new Unit(s.nextUnitId++, UnitType.DRAGON, Faction.RED, 400.0, 400.0);
        dragon.hp = 1.0;
        s.red.add(dragon);

        for (int i = 0; i < 5; i++) {
            double angle = i * (Math.PI * 2.0 / 5.0);
            Unit b = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.BLUE,
                    400.0 + 30.0 * Math.cos(angle), 400.0 + 30.0 * Math.sin(angle));
            s.blue.add(b);
        }
        rebuildGrid(s);

        MoraleSystem morale = new MoraleSystem();
        for (int i = 0; i < 60; i++) {
            morale.update(dragon, s, DT);
        }

        assertFalse(morale.isRouted(dragon), "DRAGON should never rout, even at 1 HP surrounded");
        assertFalse(dragon.routed, "DRAGON.routed flag must remain false");
    }

    @Test
    void golemNeverRouts() {
        assertFearlessType(UnitType.GOLEM);
    }

    @Test
    void catapultNeverRouts() {
        assertFearlessType(UnitType.CATAPULT);
    }

    @Test
    void healerNeverRouts() {
        assertFearlessType(UnitType.HEALER);
    }

    @Test
    void necromancerNeverRouts() {
        assertFearlessType(UnitType.NECROMANCER);
    }

    private void assertFearlessType(UnitType type) {
        World world = World.grass(800.0, 800.0, 32.0);
        GameState s = newState(world);

        Unit u = new Unit(s.nextUnitId++, type, Faction.RED, 400.0, 400.0);
        u.hp = 1.0;
        s.red.add(u);

        for (int i = 0; i < 5; i++) {
            double angle = i * (Math.PI * 2.0 / 5.0);
            Unit b = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.BLUE,
                    400.0 + 30.0 * Math.cos(angle), 400.0 + 30.0 * Math.sin(angle));
            s.blue.add(b);
        }
        rebuildGrid(s);

        MoraleSystem morale = new MoraleSystem();
        for (int i = 0; i < 60; i++) {
            morale.update(u, s, DT);
        }

        assertFalse(morale.isRouted(u), type + " must never rout");
        assertFalse(u.routed, type + ".routed flag must remain false");
    }

    @Test
    void nearbyFriendlyDragonPreventsRout() {
        World world = World.grass(800.0, 800.0, 32.0);
        GameState s = newState(world);

        Unit infantry = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 400.0, 400.0);
        infantry.hp = 5.0; // 10% of 50 maxHp
        s.red.add(infantry);

        Unit dragon = new Unit(s.nextUnitId++, UnitType.DRAGON, Faction.RED, 550.0, 400.0);
        s.red.add(dragon);

        for (int i = 0; i < 3; i++) {
            Unit b = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.BLUE,
                    400.0 + 25.0 * Math.cos(i), 400.0 + 25.0 * Math.sin(i));
            s.blue.add(b);
        }
        rebuildGrid(s);

        MoraleSystem morale = new MoraleSystem();
        morale.update(infantry, s, DT);

        assertFalse(morale.isRouted(infantry),
                "infantry near friendly DRAGON should not rout despite low HP + enemies");
        assertFalse(infantry.routed);
    }

    @Test
    void nearbyFriendlyDragonDeroutsAlly() {
        World world = World.grass(800.0, 800.0, 32.0);
        GameState s = newState(world);

        Unit infantry = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 400.0, 400.0);
        infantry.hp = infantry.type.maxHp() * 0.10;
        s.red.add(infantry);
        rebuildGrid(s);

        MoraleSystem morale = new MoraleSystem();
        morale.update(infantry, s, DT);
        assertTrue(morale.isRouted(infantry), "infantry should be routed initially");

        Unit dragon = new Unit(s.nextUnitId++, UnitType.DRAGON, Faction.RED, 550.0, 400.0);
        s.red.add(dragon);
        rebuildGrid(s);

        morale.update(infantry, s, DT);

        assertFalse(morale.isRouted(infantry),
                "friendly DRAGON within 250px should immediately derout the ally");
        assertFalse(infantry.routed);
    }

    @Test
    void enemyDragonDoesntPreventRout() {
        World world = World.grass(800.0, 800.0, 32.0);
        GameState s = newState(world);

        Unit infantry = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 400.0, 400.0);
        infantry.hp = infantry.type.maxHp() * 0.10;
        s.red.add(infantry);

        Unit enemyDragon = new Unit(s.nextUnitId++, UnitType.DRAGON, Faction.BLUE, 500.0, 400.0);
        s.blue.add(enemyDragon);
        rebuildGrid(s);

        MoraleSystem morale = new MoraleSystem();
        morale.update(infantry, s, DT);

        assertTrue(morale.isRouted(infantry),
                "enemy DRAGON does not rally; low-HP infantry should still rout");
    }

    @Test
    void farDragonDoesntPreventRout() {
        World world = World.grass(1600.0, 1600.0, 32.0);
        GameState s = newState(world);

        Unit infantry = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 400.0, 400.0);
        infantry.hp = infantry.type.maxHp() * 0.10;
        s.red.add(infantry);

        Unit dragon = new Unit(s.nextUnitId++, UnitType.DRAGON, Faction.RED, 800.0, 400.0);
        s.red.add(dragon);
        rebuildGrid(s);

        MoraleSystem morale = new MoraleSystem();
        morale.update(infantry, s, DT);

        assertTrue(morale.isRouted(infantry),
                "DRAGON 400px away (outside 250px aura) should not prevent rout");
    }

    @Test
    void nearbyFriendlyTowerWithGarrisonPreventsRout() {
        World world = World.grass(800.0, 800.0, 32.0);
        GameState s = newState(world);

        Structure tower = new Structure(nextId(), 488.0, 388.0, 64.0, 64.0,
                StructureType.TOWER, StructureType.TOWER.maxHp());
        s.structures.add(tower);
        Unit archer = new Unit(s.nextUnitId++, UnitType.ARCHER, Faction.RED, 0.0, 0.0);
        s.red.add(archer);
        s.structures.garrison(tower, archer);

        Unit infantry = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 450.0, 400.0);
        infantry.hp = infantry.type.maxHp() * 0.10;
        s.red.add(infantry);

        for (int i = 0; i < 3; i++) {
            Unit b = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.BLUE,
                    400.0 + 25.0 * Math.cos(i), 400.0 + 25.0 * Math.sin(i));
            s.blue.add(b);
        }
        rebuildGrid(s);

        MoraleSystem morale = new MoraleSystem();
        morale.update(infantry, s, DT);

        assertFalse(morale.isRouted(infantry),
                "infantry near friendly garrisoned TOWER should not rout");
        assertFalse(infantry.routed);
    }

    @Test
    void unmannedTowerDoesNotPreventRout() {
        World world = World.grass(800.0, 800.0, 32.0);
        GameState s = newState(world);

        Structure tower = new Structure(nextId(), 488.0, 388.0, 64.0, 64.0,
                StructureType.TOWER, StructureType.TOWER.maxHp());
        s.structures.add(tower);

        Unit infantry = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 450.0, 400.0);
        infantry.hp = infantry.type.maxHp() * 0.10;
        s.red.add(infantry);
        rebuildGrid(s);

        MoraleSystem morale = new MoraleSystem();
        morale.update(infantry, s, DT);

        assertTrue(morale.isRouted(infantry),
                "unmanned TOWER must NOT prevent rout — no defenders, no rallying point");
    }

    @Test
    void enemyGarrisonedTowerDoesNotHelp() {
        World world = World.grass(800.0, 800.0, 32.0);
        GameState s = newState(world);

        Structure tower = new Structure(nextId(), 488.0, 388.0, 64.0, 64.0,
                StructureType.TOWER, StructureType.TOWER.maxHp());
        s.structures.add(tower);
        Unit blueArcher = new Unit(s.nextUnitId++, UnitType.ARCHER, Faction.BLUE, 0.0, 0.0);
        s.blue.add(blueArcher);
        s.structures.garrison(tower, blueArcher);

        Unit infantry = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 450.0, 400.0);
        infantry.hp = infantry.type.maxHp() * 0.10;
        s.red.add(infantry);
        rebuildGrid(s);

        MoraleSystem morale = new MoraleSystem();
        morale.update(infantry, s, DT);

        assertTrue(morale.isRouted(infantry),
                "enemy-garrisoned TOWER does not rally; low-HP infantry still routs");
    }

    @Test
    void farTowerDoesntPreventRout() {
        World world = World.grass(1600.0, 1600.0, 32.0);
        GameState s = newState(world);

        Structure tower = new Structure(nextId(), 600.0, 400.0, 64.0, 64.0,
                StructureType.TOWER, StructureType.TOWER.maxHp());
        s.structures.add(tower);
        Unit archer = new Unit(s.nextUnitId++, UnitType.ARCHER, Faction.RED, 0.0, 0.0);
        s.red.add(archer);
        s.structures.garrison(tower, archer);

        Unit infantry = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 400.0, 400.0);
        infantry.hp = infantry.type.maxHp() * 0.10;
        s.red.add(infantry);
        rebuildGrid(s);

        MoraleSystem morale = new MoraleSystem();
        morale.update(infantry, s, DT);

        assertTrue(morale.isRouted(infantry),
                "TOWER >150px away does not provide aura; low-HP infantry routs");
    }

    @Test
    void routedUnitFlightDirectionPointsAwayFromEnemies() {
        World world = World.grass(800.0, 800.0, 32.0);
        GameState s = newState(world);

        Unit r = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 400.0, 400.0);
        r.hp = r.type.maxHp() * 0.10;
        s.red.add(r);

        Unit b1 = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.BLUE, 450.0, 400.0);
        Unit b2 = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.BLUE, 460.0, 410.0);
        s.blue.add(b1);
        s.blue.add(b2);
        rebuildGrid(s);

        MoraleSystem morale = new MoraleSystem();
        morale.update(r, s, DT);
        assertTrue(morale.isRouted(r));

        double[] dir = morale.flightDirection(r, s);
        assertTrue(dir[0] < 0.0,
                "flight direction X should be negative (away from enemies to the right); was " + dir[0]);
        double mag = Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1]);
        assertTrue(Math.abs(mag - 1.0) < 1.0e-6, "flight direction should be unit vector; mag=" + mag);
    }

    @Test
    void routedFlightDoesNotReachEdge() {
        double width = 1280.0;
        double height = 800.0;
        World world = World.grass(width, height, 32.0);
        SpatialHashGrid grid = new SpatialHashGrid(width, height, 32.0);
        Army red = new Army(Faction.RED, 100);
        Army blue = new Army(Faction.BLUE, 100);
        GameState state = new GameState(world, red, blue, grid);
        state.phase = GameState.Phase.BATTLE;

        UnitAI ai = new UnitAI();
        GameLoop gl = new GameLoop(state, ai);

        Unit r = new Unit(state.nextUnitId++, UnitType.INFANTRY, Faction.RED, 640.0, 400.0);
        r.hp = r.type.maxHp() * 0.10;
        state.red.add(r);

        for (int i = 0; i < 4; i++) {
            double angle = i * Math.PI / 2.0;
            Unit b = new Unit(state.nextUnitId++, UnitType.INFANTRY, Faction.BLUE,
                    640.0 + 30.0 * Math.cos(angle), 400.0 + 30.0 * Math.sin(angle));
            state.blue.add(b);
        }

        int steps = (int) (10.0 / DT);
        for (int i = 0; i < steps; i++) {
            gl.step(DT);
            if (!r.isAlive()) break;
        }

        if (r.isAlive()) {
            assertTrue(r.x > 50.0 && r.x < width - 50.0,
                    "routed unit should not pin to x edge; x=" + r.x);
            assertTrue(r.y > 50.0 && r.y < height - 50.0,
                    "routed unit should not pin to y edge; y=" + r.y);
        }
    }

    @Test
    void routedTooLongRalliesAutomatically() {
        World world = World.grass(1600.0, 1600.0, 32.0);
        GameState s = newState(world);

        Unit r = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 800.0, 800.0);
        r.hp = r.type.maxHp() * 0.10;
        s.red.add(r);
        rebuildGrid(s);

        MoraleSystem morale = new MoraleSystem();
        morale.update(r, s, DT);
        assertTrue(morale.isRouted(r), "should rout initially");

        int steps = (int) (13.0 / DT);
        for (int i = 0; i < steps; i++) {
            morale.update(r, s, DT);
        }

        assertFalse(morale.isRouted(r),
                "routed unit should auto-rally after MAX_FLEE_SECONDS (12s)");
        assertFalse(r.routed);
    }

    @Test
    void routedAtEdgeRalliesImmediately() {
        World world = World.grass(1280.0, 800.0, 32.0);
        GameState s = newState(world);

        Unit r = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 640.0, 400.0);
        r.hp = r.type.maxHp() * 0.10;
        s.red.add(r);
        rebuildGrid(s);

        MoraleSystem morale = new MoraleSystem();
        morale.update(r, s, DT);
        assertTrue(morale.isRouted(r));

        r.x = 20.0;

        morale.update(r, s, DT);

        assertFalse(morale.isRouted(r),
                "routed unit pinned within 30px of edge should rally immediately");
        assertFalse(r.routed);
    }
}
