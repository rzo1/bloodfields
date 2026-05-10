package com.github.rzo1.bloodfields.ui;

import com.github.rzo1.bloodfields.engine.GameState;
import com.github.rzo1.bloodfields.engine.SpatialHashGrid;
import com.github.rzo1.bloodfields.engine.World;
import com.github.rzo1.bloodfields.model.Army;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelsTest {

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 800;
    private static final double TILE = 32.0;

    @Test
    void hasElevenLevelsWithMatchingNumbers() {
        List<Level> all = Levels.all();
        assertEquals(11, all.size());
        for (int i = 0; i < all.size(); i++) {
            Level lvl = all.get(i);
            assertEquals(i + 1, lvl.number(), "level number must equal index+1");
            assertNotNull(lvl.name());
            assertNotNull(lvl.spawner());
            assertNotNull(lvl.weather());
            assertNotNull(lvl.mapId());
            assertTrue(lvl.playerBudget() > 0);
        }
    }

    @Test
    void levelIdsAreDistinct() {
        List<Level> all = Levels.all();
        java.util.Set<String> names = new java.util.HashSet<>();
        java.util.Set<Integer> numbers = new java.util.HashSet<>();
        for (Level lvl : all) {
            assertTrue(names.add(lvl.name()), "duplicate name: " + lvl.name());
            assertTrue(numbers.add(lvl.number()), "duplicate number: " + lvl.number());
        }
    }

    @Test
    void budgetsAreEscalating() {
        List<Level> all = Levels.all();
        for (int i = 1; i < all.size(); i++) {
            assertTrue(all.get(i).playerBudget() >= all.get(i - 1).playerBudget(),
                    "level " + (i + 1) + " budget should not decrease");
        }
        assertTrue(all.get(all.size() - 1).playerBudget() > all.get(0).playerBudget(),
                "final budget should exceed first level budget");
    }

    @Test
    void everySpawnerProducesUnitsInUpperHalf() {
        for (Level lvl : Levels.all()) {
            GameState state = freshState(lvl);
            Army enemy = state.blue;
            lvl.spawner().spawn(enemy, state, WIDTH, HEIGHT);
            assertFalse(enemy.units().isEmpty(),
                    "spawner for " + lvl.name() + " produced no units");
            for (Unit u : enemy.units()) {
                assertTrue(u.y < HEIGHT / 2.0,
                        "unit in " + lvl.name() + " placed below midline at y=" + u.y);
                assertTrue(u.x >= 0 && u.x <= WIDTH,
                        "unit in " + lvl.name() + " out of bounds at x=" + u.x);
                assertEquals(Faction.BLUE, u.faction);
            }
        }
    }

    @Test
    void spawnersAreDeterministic() {
        for (Level lvl : Levels.all()) {
            GameState s1 = freshState(lvl);
            GameState s2 = freshState(lvl);
            lvl.spawner().spawn(s1.blue, s1, WIDTH, HEIGHT);
            lvl.spawner().spawn(s2.blue, s2, WIDTH, HEIGHT);

            List<Unit> a = new ArrayList<>(s1.blue.units());
            List<Unit> b = new ArrayList<>(s2.blue.units());
            assertEquals(a.size(), b.size(),
                    "spawner " + lvl.name() + " produced different unit counts");
            for (int i = 0; i < a.size(); i++) {
                assertEquals(a.get(i).type, b.get(i).type,
                        "type mismatch at index " + i + " for " + lvl.name());
                assertEquals(a.get(i).x, b.get(i).x, 1e-9,
                        "x mismatch at index " + i + " for " + lvl.name());
                assertEquals(a.get(i).y, b.get(i).y, 1e-9,
                        "y mismatch at index " + i + " for " + lvl.name());
            }
        }
    }

    @Test
    void levelsHaveSubstantialUnitCounts() {
        for (Level lvl : Levels.all()) {
            GameState state = freshState(lvl);
            lvl.spawner().spawn(state.blue, state, WIDTH, HEIGHT);
            assertTrue(state.blue.units().size() >= 12,
                    lvl.name() + " should field at least 12 units; got " + state.blue.units().size());
        }
    }

    @Test
    void eachLevelIntroducesItsSignatureUnitType() {
        Map<Integer, UnitType> sig = new java.util.HashMap<>();
        sig.put(1, UnitType.INFANTRY);
        sig.put(2, UnitType.PIKEMAN);
        sig.put(3, UnitType.ARCHER);
        sig.put(4, UnitType.CAVALRY);
        sig.put(5, UnitType.MAGE);
        sig.put(6, UnitType.GOLEM);
        sig.put(7, UnitType.HEALER);
        sig.put(8, UnitType.NECROMANCER);
        sig.put(9, UnitType.CATAPULT);
        sig.put(10, UnitType.DRAGON);
        sig.put(11, UnitType.DRAGON);
        for (Level lvl : Levels.all()) {
            UnitType expected = sig.get(lvl.number());
            assertNotNull(expected, "no signature mapping for level " + lvl.number());
            GameState state = freshState(lvl);
            lvl.spawner().spawn(state.blue, state, WIDTH, HEIGHT);
            boolean found = false;
            for (Unit u : state.blue.units()) {
                if (u.type == expected) { found = true; break; }
            }
            assertTrue(found, "level " + lvl.number() + " (" + lvl.name() + ") missing required type "
                    + expected);
        }
    }

    @Test
    void shadowCultIncludesAssassins() {
        Level lvl = Levels.all().get(7);
        GameState state = freshState(lvl);
        lvl.spawner().spawn(state.blue, state, WIDTH, HEIGHT);
        boolean assassin = false;
        boolean necro = false;
        for (Unit u : state.blue.units()) {
            if (u.type == UnitType.ASSASSIN) assassin = true;
            if (u.type == UnitType.NECROMANCER) necro = true;
        }
        assertTrue(assassin, "Shadow Cult should include ASSASSIN units");
        assertTrue(necro, "Shadow Cult should include NECROMANCER units");
    }

    @Test
    void siegeTowersIncludesGeneralAndSetsHeroSkill() {
        Level lvl = Levels.all().get(8);
        GameState state = freshState(lvl);
        lvl.spawner().spawn(state.blue, state, WIDTH, HEIGHT);
        boolean hasGeneral = false;
        for (Unit u : state.blue.units()) {
            if (u.type == UnitType.GENERAL) { hasGeneral = true; break; }
        }
        assertTrue(hasGeneral, "Siege Towers should include a GENERAL");
        assertNotNull(state.blue.heroSkill(), "Siege Towers should set an enemy hero skill");
    }

    @Test
    void finalFuckeryIncludesDragonAndGeneralAndHeroSkill() {
        Level lvl = Levels.all().get(9);
        GameState state = freshState(lvl);
        lvl.spawner().spawn(state.blue, state, WIDTH, HEIGHT);
        boolean dragon = false;
        boolean general = false;
        for (Unit u : state.blue.units()) {
            if (u.type == UnitType.DRAGON) dragon = true;
            if (u.type == UnitType.GENERAL) general = true;
        }
        assertTrue(dragon, "Final Fuckery must include the DRAGON");
        assertTrue(general, "Final Fuckery must include a GENERAL");
        assertNotNull(state.blue.heroSkill(), "Final Fuckery should set an enemy hero skill");
    }

    @Test
    void weathersMatchSpec() {
        List<Level> all = Levels.all();
        assertEquals(Weather.CLEAR, all.get(0).weather());
        assertEquals(Weather.CLEAR, all.get(1).weather());
        assertEquals(Weather.CLEAR, all.get(2).weather());
        assertEquals(Weather.RAIN, all.get(3).weather());
        assertEquals(Weather.FOG, all.get(4).weather());
        assertEquals(Weather.CLEAR, all.get(5).weather());
        assertEquals(Weather.CLEAR, all.get(6).weather());
        assertEquals(Weather.NIGHT, all.get(7).weather());
        assertEquals(Weather.CLEAR, all.get(8).weather());
        assertEquals(Weather.NIGHT, all.get(9).weather());
        assertEquals(Weather.NIGHT, all.get(10).weather());
    }

    @Test
    void mapIdsAreKnownPresets() {
        for (Level lvl : Levels.all()) {
            assertNotNull(Maps.byId(lvl.mapId()),
                    "level " + lvl.name() + " references unknown map id " + lvl.mapId());
        }
    }

    @Test
    void level1SkirmishLineHasInfantryCap() {
        Level lvl = Levels.all().get(0);
        Map<UnitType, Integer> caps = lvl.playerCaps();
        assertEquals(Integer.valueOf(30), caps.get(UnitType.INFANTRY),
                "Level 1 should cap INFANTRY at 30 to push variety");
    }

    @Test
    void level9SiegeTowersHasCatapultCap() {
        Level lvl = Levels.all().get(8);
        Map<UnitType, Integer> caps = lvl.playerCaps();
        assertNotNull(caps.get(UnitType.CATAPULT),
                "Level 9 should cap CATAPULT");
        assertTrue(caps.get(UnitType.CATAPULT) <= 6,
                "Level 9 catapult cap should keep siege scarce");
        assertEquals(Integer.valueOf(1), caps.get(UnitType.GENERAL),
                "Level 9 should allow at most one GENERAL");
    }

    @Test
    void mostCampaignLevelsDeclareAtLeastOneCap() {
        for (Level lvl : Levels.all()) {
            if ("Simon's Nightmare".equals(lvl.name())) continue;
            assertFalse(lvl.playerCaps().isEmpty(),
                    "level " + lvl.name() + " has no caps; spam is unrestricted");
        }
    }

    @Test
    void level11SimonsNightmareIncludesMultipleDragons() {
        Level lvl = Levels.all().get(10);
        assertEquals("Simon's Nightmare", lvl.name());
        GameState state = freshState(lvl);
        lvl.spawner().spawn(state.blue, state, WIDTH, HEIGHT);
        int dragons = 0;
        for (Unit u : state.blue.units()) {
            if (u.type == UnitType.DRAGON) dragons++;
        }
        assertEquals(4, dragons,
                "Simon's Nightmare must field exactly four DRAGONs; got " + dragons);
    }

    @Test
    void level11HasNoPlayerCaps() {
        Level lvl = Levels.all().get(10);
        assertEquals("Simon's Nightmare", lvl.name());
        assertTrue(lvl.playerCaps().isEmpty(),
                "Simon's Nightmare must declare no player caps");
    }

    @Test
    void capsAreUnmodifiable() {
        Level lvl = Levels.all().get(0);
        Map<UnitType, Integer> caps = lvl.playerCaps();
        try {
            caps.put(UnitType.MAGE, 99);
            org.junit.jupiter.api.Assertions.fail(
                    "playerCaps() must be unmodifiable to prevent mutation across runs");
        } catch (UnsupportedOperationException expected) {
            // good
        }
    }

    @Test
    void level10FinalFuckeryCapsBigThreats() {
        Level lvl = Levels.all().get(9);
        Map<UnitType, Integer> caps = lvl.playerCaps();
        assertNotNull(caps.get(UnitType.CATAPULT));
        assertNotNull(caps.get(UnitType.MAGE));
        assertNotNull(caps.get(UnitType.NECROMANCER));
        assertEquals(Integer.valueOf(1), caps.get(UnitType.GENERAL));
    }

    private static GameState freshState(Level lvl) {
        World world;
        MapPreset preset = lvl == null ? null : Maps.byId(lvl.mapId());
        if (preset == null) {
            world = World.battlefield(WIDTH, HEIGHT, TILE);
        } else {
            int cols = (int) Math.ceil(WIDTH / TILE);
            int rows = (int) Math.ceil(HEIGHT / TILE);
            world = new World(WIDTH, HEIGHT, TILE,
                    preset.generator().generate(cols, rows));
        }
        SpatialHashGrid grid = new SpatialHashGrid(WIDTH, HEIGHT, 64.0);
        Army red = new Army(Faction.RED, 100);
        Army blue = new Army(Faction.BLUE, 5000);
        return new GameState(world, red, blue, grid);
    }
}
