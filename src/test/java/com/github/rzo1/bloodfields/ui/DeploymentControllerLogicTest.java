package com.github.rzo1.bloodfields.ui;

import com.github.rzo1.bloodfields.engine.World;
import com.github.rzo1.bloodfields.model.Army;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.Terrain;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitType;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeploymentControllerLogicTest {

    private static final double TILE = 32.0;

    private DeploymentController make(Army army, DeploymentZone zone, AtomicLong ids) {
        return new DeploymentController(null, army, zone, TILE, ids::getAndIncrement);
    }

    private DeploymentController makeWithWorld(Army army, DeploymentZone zone, AtomicLong ids, World world) {
        return new DeploymentController(null, army, zone, TILE, ids::getAndIncrement, world);
    }

    private World worldWithRiverTile(int riverCol, int riverRow) {
        int cols = 10;
        int rows = 10;
        Terrain.TileType[][] tiles = new Terrain.TileType[cols][rows];
        for (int c = 0; c < cols; c++) {
            for (int r = 0; r < rows; r++) {
                tiles[c][r] = Terrain.TileType.GRASS;
            }
        }
        tiles[riverCol][riverRow] = Terrain.TileType.RIVER;
        return new World(cols * TILE, rows * TILE, TILE, tiles);
    }

    @Test
    void placeWithoutSelectedTypeFails() {
        Army army = new Army(Faction.BLUE, 100);
        DeploymentZone zone = new DeploymentZone(0, 0, 320, 320);
        DeploymentController c = make(army, zone, new AtomicLong(1));
        assertFalse(c.tryPlace(50, 50));
        assertEquals(0, army.units().size());
    }

    @Test
    void validPlacementSnapsAndAddsUnit() {
        Army army = new Army(Faction.BLUE, 100);
        DeploymentZone zone = new DeploymentZone(0, 0, 320, 320);
        AtomicLong ids = new AtomicLong(7);
        DeploymentController c = make(army, zone, ids);
        c.setSelectedType(UnitType.INFANTRY);
        assertTrue(c.tryPlace(50, 70));
        assertEquals(1, army.units().size());
        Unit u = army.units().get(0);
        assertEquals(48.0, u.x, 1e-9);
        assertEquals(80.0, u.y, 1e-9);
        assertEquals(7L, u.id);
        assertEquals(Faction.BLUE, u.faction);
        assertEquals(UnitType.INFANTRY, u.type);
        assertEquals(10, army.spentPoints());
    }

    @Test
    void outOfZonePlacementRejected() {
        Army army = new Army(Faction.BLUE, 100);
        DeploymentZone zone = new DeploymentZone(0, 0, 320, 320);
        DeploymentController c = make(army, zone, new AtomicLong(1));
        c.setSelectedType(UnitType.INFANTRY);
        assertFalse(c.tryPlace(500, 50));
        assertFalse(c.tryPlace(50, 500));
        assertEquals(0, army.units().size());
    }

    @Test
    void unaffordablePlacementRejected() {
        Army army = new Army(Faction.BLUE, 20);
        DeploymentZone zone = new DeploymentZone(0, 0, 320, 320);
        DeploymentController c = make(army, zone, new AtomicLong(1));
        c.setSelectedType(UnitType.CAVALRY);
        assertFalse(c.tryPlace(50, 50));
        assertEquals(0, army.units().size());
    }

    @Test
    void onArmyChangedFiresOnPlace() {
        Army army = new Army(Faction.BLUE, 100);
        DeploymentZone zone = new DeploymentZone(0, 0, 320, 320);
        DeploymentController c = make(army, zone, new AtomicLong(1));
        AtomicInteger calls = new AtomicInteger();
        c.setOnArmyChanged(calls::incrementAndGet);
        c.setSelectedType(UnitType.INFANTRY);
        c.tryPlace(50, 50);
        c.tryPlace(80, 80);
        assertEquals(2, calls.get());
    }

    @Test
    void tryRemoveRemovesNearestWithinRadius() {
        Army army = new Army(Faction.BLUE, 100);
        DeploymentZone zone = new DeploymentZone(0, 0, 320, 320);
        DeploymentController c = make(army, zone, new AtomicLong(1));
        c.setSelectedType(UnitType.INFANTRY);
        c.tryPlace(48, 48);
        c.tryPlace(112, 112);
        assertEquals(2, army.units().size());

        AtomicInteger calls = new AtomicInteger();
        c.setOnArmyChanged(calls::incrementAndGet);
        assertTrue(c.tryRemove(50, 50));
        assertEquals(1, army.units().size());
        assertEquals(112.0, army.units().get(0).x, 1e-9);
        assertEquals(1, calls.get());
    }

    @Test
    void tryRemoveOutsideRadiusFails() {
        Army army = new Army(Faction.BLUE, 100);
        DeploymentZone zone = new DeploymentZone(0, 0, 320, 320);
        DeploymentController c = make(army, zone, new AtomicLong(1));
        c.setSelectedType(UnitType.INFANTRY);
        c.tryPlace(48, 48);
        assertFalse(c.tryRemove(300, 300));
        assertEquals(1, army.units().size());
    }

    @Test
    void canStartReflectsUnitCount() {
        Army army = new Army(Faction.BLUE, 100);
        DeploymentZone zone = new DeploymentZone(0, 0, 320, 320);
        DeploymentController c = make(army, zone, new AtomicLong(1));
        assertFalse(c.canStart());
        c.setSelectedType(UnitType.INFANTRY);
        c.tryPlace(50, 50);
        assertTrue(c.canStart());
    }

    @Test
    void budgetTrackingAcrossPlacesAndRemoves() {
        Army army = new Army(Faction.BLUE, 50);
        DeploymentZone zone = new DeploymentZone(0, 0, 320, 320);
        DeploymentController c = make(army, zone, new AtomicLong(1));
        c.setSelectedType(UnitType.INFANTRY);
        assertTrue(c.tryPlace(48, 48));
        assertTrue(c.tryPlace(112, 48));
        assertTrue(c.tryPlace(176, 48));
        assertTrue(c.tryPlace(240, 48));
        assertTrue(c.tryPlace(48, 112));
        assertEquals(0, army.remainingBudget());
        assertFalse(c.tryPlace(112, 112));

        Unit first = army.units().get(0);
        assertTrue(c.tryRemove(first.x, first.y));
        assertEquals(10, army.remainingBudget());
        assertTrue(c.tryPlace(176, 112));
        assertEquals(0, army.remainingBudget());
    }

    @Test
    void snapToGridCenter() {
        Army army = new Army(Faction.BLUE, 100);
        DeploymentZone zone = new DeploymentZone(0, 0, 320, 320);
        DeploymentController c = make(army, zone, new AtomicLong(1));
        double[] s = c.snap(0, 0);
        assertEquals(16.0, s[0], 1e-9);
        assertEquals(16.0, s[1], 1e-9);
        s = c.snap(31.9, 31.9);
        assertEquals(16.0, s[0], 1e-9);
        assertEquals(16.0, s[1], 1e-9);
        s = c.snap(32.0, 32.0);
        assertEquals(48.0, s[0], 1e-9);
        assertEquals(48.0, s[1], 1e-9);
    }

    @Test
    void placementOnRiverTileRejected() {
        Army army = new Army(Faction.RED, 100);
        DeploymentZone zone = new DeploymentZone(0, 0, 320, 320);
        World world = worldWithRiverTile(2, 2);
        DeploymentController c = makeWithWorld(army, zone, new AtomicLong(1), world);
        c.setSelectedType(UnitType.INFANTRY);
        double riverCenterX = 2 * TILE + TILE / 2.0;
        double riverCenterY = 2 * TILE + TILE / 2.0;
        assertFalse(c.tryPlace(riverCenterX, riverCenterY));
        assertEquals(0, army.units().size());
    }

    @Test
    void placementOnGrassWithWorldStillSucceeds() {
        Army army = new Army(Faction.RED, 100);
        DeploymentZone zone = new DeploymentZone(0, 0, 320, 320);
        World world = worldWithRiverTile(2, 2);
        DeploymentController c = makeWithWorld(army, zone, new AtomicLong(1), world);
        c.setSelectedType(UnitType.INFANTRY);
        double grassX = 5 * TILE + TILE / 2.0;
        double grassY = 5 * TILE + TILE / 2.0;
        assertTrue(c.tryPlace(grassX, grassY));
        assertEquals(1, army.units().size());
    }

    @Test
    void ghostOnPassableTerrainReportsCorrectly() {
        Army army = new Army(Faction.RED, 100);
        DeploymentZone zone = new DeploymentZone(0, 0, 320, 320);
        World world = worldWithRiverTile(2, 2);
        DeploymentController c = makeWithWorld(army, zone, new AtomicLong(1), world);
        c.setSelectedType(UnitType.INFANTRY);
        assertFalse(c.isGhostOnPassableTerrain(), "no ghost yet");
        c.setGhostForTest(5 * TILE + 16, 5 * TILE + 16);
        assertTrue(c.isGhostOnPassableTerrain());
        c.setGhostForTest(2 * TILE + 16, 2 * TILE + 16);
        assertFalse(c.isGhostOnPassableTerrain());
    }

    @Test
    void ghostPassableWhenWorldIsNull() {
        Army army = new Army(Faction.RED, 100);
        DeploymentZone zone = new DeploymentZone(0, 0, 320, 320);
        DeploymentController c = make(army, zone, new AtomicLong(1));
        c.setSelectedType(UnitType.INFANTRY);
        c.setGhostForTest(50, 50);
        assertTrue(c.isGhostOnPassableTerrain());
    }

    @Test
    void canonicalBattlefieldRejectsRiverPlacement() {
        World world = World.battlefield(1280, 800, TILE);
        int riverCol = -1;
        int riverRow = -1;
        outer:
        for (int c = 0; c < world.cols(); c++) {
            for (int r = 0; r < world.rows(); r++) {
                if (world.terrain[c][r] == Terrain.TileType.RIVER) {
                    riverCol = c;
                    riverRow = r;
                    break outer;
                }
            }
        }
        assertTrue(riverCol >= 0 && riverRow >= 0, "expected battlefield to contain at least one RIVER tile");

        Army army = new Army(Faction.RED, 100);
        DeploymentZone zone = new DeploymentZone(0, 0, 1280, 800);
        DeploymentController c = makeWithWorld(army, zone, new AtomicLong(1), world);
        c.setSelectedType(UnitType.INFANTRY);
        double riverCenterX = riverCol * TILE + TILE / 2.0;
        double riverCenterY = riverRow * TILE + TILE / 2.0;
        assertFalse(c.tryPlace(riverCenterX, riverCenterY),
                "placement on RIVER tile (" + riverCol + "," + riverRow + ") should be rejected");
        assertEquals(0, army.units().size());
    }

    @Test
    void canonicalBattlefieldAllowsGrassPlacement() {
        World world = World.battlefield(1280, 800, TILE);
        int grassCol = -1;
        int grassRow = -1;
        outer:
        for (int c = 0; c < world.cols(); c++) {
            for (int r = 0; r < world.rows(); r++) {
                if (world.terrain[c][r] == Terrain.TileType.GRASS) {
                    grassCol = c;
                    grassRow = r;
                    break outer;
                }
            }
        }
        assertTrue(grassCol >= 0 && grassRow >= 0);

        Army army = new Army(Faction.RED, 100);
        DeploymentZone zone = new DeploymentZone(0, 0, 1280, 800);
        DeploymentController c = makeWithWorld(army, zone, new AtomicLong(1), world);
        c.setSelectedType(UnitType.INFANTRY);
        double gx = grassCol * TILE + TILE / 2.0;
        double gy = grassRow * TILE + TILE / 2.0;
        assertTrue(c.tryPlace(gx, gy));
        assertEquals(1, army.units().size());
    }

    @Test
    void autoFillSkipsRiverTiles() {
        World world = World.battlefield(1280, 800, TILE);
        Army army = new Army(Faction.RED, 220);
        DeploymentZone zone = new DeploymentZone(0, 400, 1280, 800);
        DeploymentController c = makeWithWorld(army, zone, new AtomicLong(1), world);
        c.setSelectedType(UnitType.INFANTRY);
        double tile = TILE;
        for (double y = 400 + tile / 2.0; y < 800; y += tile) {
            for (double x = tile / 2.0; x < 1280; x += tile) {
                c.tryPlace(x, y);
            }
        }
        for (Unit u : army.units()) {
            assertTrue(world.passableAt(u.x, u.y),
                    "unit at (" + u.x + "," + u.y + ") landed on impassable tile");
        }
    }

    @Test
    void uniqueUnitCannotBePlacedTwice() {
        Army army = new Army(Faction.RED, 1000);
        DeploymentZone zone = new DeploymentZone(0, 0, 1280, 1280);
        DeploymentController c = make(army, zone, new AtomicLong(1));
        c.setSelectedType(UnitType.GENERAL);
        assertTrue(UnitType.GENERAL.unique(), "test depends on GENERAL.unique()");
        assertFalse(c.hasUnit(UnitType.GENERAL));
        assertTrue(c.tryPlace(160, 160), "first GENERAL placement should succeed");
        assertEquals(1, army.units().size());
        assertTrue(c.hasUnit(UnitType.GENERAL));
        assertFalse(c.tryPlace(320, 320), "second GENERAL placement must be rejected");
        assertEquals(1, army.units().size());
    }

    @Test
    void uniqueRulesDoNotAffectNonUniqueTypes() {
        Army army = new Army(Faction.RED, 1000);
        DeploymentZone zone = new DeploymentZone(0, 0, 1280, 1280);
        DeploymentController c = make(army, zone, new AtomicLong(1));
        c.setSelectedType(UnitType.INFANTRY);
        assertTrue(c.tryPlace(160, 160));
        assertTrue(c.tryPlace(224, 160));
        assertTrue(c.tryPlace(288, 160));
        assertEquals(3, army.units().size());
    }

    @Test
    void unaffordableAfterSelectionStillRejects() {
        Army army = new Army(Faction.BLUE, 25);
        DeploymentZone zone = new DeploymentZone(0, 0, 320, 320);
        DeploymentController c = make(army, zone, new AtomicLong(1));
        c.setSelectedType(UnitType.INFANTRY);
        c.tryPlace(48, 48);
        c.tryPlace(112, 48);
        assertEquals(5, army.remainingBudget());
        c.setSelectedType(UnitType.ARCHER);
        assertFalse(c.isGhostAffordable());
        assertFalse(c.tryPlace(176, 48));
    }
}
