package com.github.rzo1.bloodfields.ai;

import com.github.rzo1.bloodfields.engine.GameLoop;
import com.github.rzo1.bloodfields.engine.GameState;
import com.github.rzo1.bloodfields.engine.SpatialHashGrid;
import com.github.rzo1.bloodfields.engine.Structure;
import com.github.rzo1.bloodfields.engine.StructureType;
import com.github.rzo1.bloodfields.engine.World;
import com.github.rzo1.bloodfields.model.Army;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GarrisonMeleeBlockedTest {

    private static final double DT = 1.0 / 60.0;

    @Test
    void meleeAttackerCannotDamageGarrisonedUnit() {
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
        Unit infantry = new Unit(2L, UnitType.INFANTRY, Faction.BLUE,
                towerCenterX + 5.0, towerCenterY + 5.0);
        blue.add(infantry);

        double startArcherHp = archer.hp;
        double startTowerHp = state.structures.hpOf(tower);

        GameLoop loop = new GameLoop(state, new UnitAI());
        for (int i = 0; i < 120; i++) {
            loop.step(DT);
        }

        assertEquals(startArcherHp, archer.hp, 1e-9,
                "garrisoned archer should be untouched by melee infantry");
        assertEquals(startTowerHp, state.structures.hpOf(tower), 1e-9,
                "melee should not bleed into the structure");
    }
}
