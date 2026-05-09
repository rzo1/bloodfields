package com.example.armyclash.ai;

import com.example.armyclash.engine.Structure;
import com.example.armyclash.engine.StructureField;
import com.example.armyclash.engine.StructureType;
import com.example.armyclash.engine.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LineCrossesStructureTest {

    @Test
    void lineThroughWallIsBlocked() {
        World w = World.grass(640.0, 480.0, 32.0);
        StructureField structures = new StructureField();
        Structure wall = new Structure(-1L, 200.0, 100.0, 64.0, 32.0,
                StructureType.WALL, StructureType.WALL.maxHp());
        structures.add(wall);

        assertTrue(UnitAI.lineCrossesImpassable(w, structures,
                100.0, 116.0, 350.0, 116.0));
    }

    @Test
    void lineWithoutStructuresIsNotBlocked() {
        World w = World.grass(640.0, 480.0, 32.0);
        StructureField structures = new StructureField();
        assertFalse(UnitAI.lineCrossesImpassable(w, structures,
                100.0, 100.0, 350.0, 100.0));
    }

    @Test
    void lineGoingAroundWallIsNotBlocked() {
        World w = World.grass(640.0, 480.0, 32.0);
        StructureField structures = new StructureField();
        Structure wall = new Structure(-1L, 200.0, 100.0, 64.0, 32.0,
                StructureType.WALL, StructureType.WALL.maxHp());
        structures.add(wall);

        // Line above the wall (y=50), shouldn't intersect the y=100..132 strip
        assertFalse(UnitAI.lineCrossesImpassable(w, structures,
                100.0, 50.0, 350.0, 50.0));
    }

    @Test
    void backwardCompatNoStructuresArgument() {
        World w = World.grass(640.0, 480.0, 32.0);
        // No structures → only terrain checked
        assertFalse(UnitAI.lineCrossesImpassable(w, 100.0, 100.0, 350.0, 100.0));
    }
}
