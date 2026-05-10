package com.github.rzo1.bloodfields.engine;

import com.github.rzo1.bloodfields.model.Army;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.Projectile;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GarrisonRangedDamageTest {

    private static final double DT = 1.0 / 60.0;

    @Test
    void projectileAgainstGarrisonedArcherDealsReducedDamageAndBleedsToTower() {
        World world = World.grass(800.0, 600.0, 32.0);
        SpatialHashGrid grid = new SpatialHashGrid(800.0, 600.0, 64.0);
        Army red = new Army(Faction.RED, 100);
        Army blue = new Army(Faction.BLUE, 100);
        GameState state = new GameState(world, red, blue, grid);
        state.phase = GameState.Phase.BATTLE;

        Structure tower = new Structure(-1L, 200.0, 200.0, 64.0, 64.0,
                StructureType.TOWER, StructureType.TOWER.maxHp());
        state.structures.add(tower);

        Unit archer = new Unit(1L, UnitType.ARCHER, Faction.RED, 0.0, 0.0);
        red.add(archer);
        state.structures.garrison(tower, archer);

        double towerCenterX = tower.x() + tower.width() / 2.0;
        double towerCenterY = tower.y() + tower.height() / 2.0;

        double damage = 20.0;
        Projectile p = new Projectile(towerCenterX - 8.0, towerCenterY,
                400.0, 0.0,
                Faction.BLUE, damage, archer, 0.0, UnitType.ARCHER);
        state.projectiles.add(p);

        double startArcherHp = archer.hp;
        double startTowerHp = state.structures.hpOf(tower);

        GameLoop loop = new GameLoop(state, (u, s, dt) -> {});
        for (int i = 0; i < 30 && p.alive; i++) {
            loop.step(DT);
        }

        double archerLost = startArcherHp - archer.hp;
        double towerLost = startTowerHp - state.structures.hpOf(tower);
        // For UnitType.ARCHER vs UnitType.ARCHER, damage matchup is 1.0 by DamageModel.
        assertEquals(damage * StructureField.GARRISONED_RANGED_DAMAGE_MULT, archerLost, 0.5,
                "garrisoned archer should lose 0.75x of base damage");
        assertEquals(damage * StructureField.GARRISONED_RANGED_STRUCTURE_BLEED, towerLost, 0.5,
                "structure should bleed 0.10x of base damage");
        assertTrue(archer.isAlive(), "archer should survive the single hit");
    }
}
