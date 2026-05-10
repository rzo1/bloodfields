package com.github.rzo1.bloodfields.ui;

import com.github.rzo1.bloodfields.engine.Structure;
import com.github.rzo1.bloodfields.engine.StructureType;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class WallSplatterTest {

    private static Structure wall(long id) {
        return new Structure(id, 100.0, 100.0, 64.0, 16.0, StructureType.WALL,
                StructureType.WALL.maxHp());
    }

    @Test
    void recordHitAppendsSplat() {
        WallSplatter splatter = new WallSplatter(new Random(0));
        Structure s = wall(1L);
        splatter.recordHit(s, 110.0, 105.0);
        assertEquals(1, splatter.size());
        assertEquals(1, splatter.countOn(s));
    }

    @Test
    void multipleHitsStack() {
        WallSplatter splatter = new WallSplatter(new Random(0));
        Structure s = wall(1L);
        splatter.recordHit(s, 110.0, 105.0);
        splatter.recordHit(s, 130.0, 110.0);
        splatter.recordHit(s, 150.0, 108.0);
        assertEquals(3, splatter.size());
        assertEquals(3, splatter.countOn(s));
    }

    @Test
    void splatPositionLocalizedWithinStructure() {
        WallSplatter splatter = new WallSplatter(new Random(0));
        Structure s = wall(1L);
        splatter.recordHit(s, 130.0, 110.0);
        WallSplatter.Splat sp = splatter.splats().get(0);
        assertTrue(sp.offsetX >= 0.0 && sp.offsetX <= s.width(),
                "offsetX should be within structure footprint, got " + sp.offsetX);
        assertTrue(sp.offsetY >= 0.0 && sp.offsetY <= s.height(),
                "offsetY should be within structure footprint, got " + sp.offsetY);
    }

    @Test
    void clearEmpties() {
        WallSplatter splatter = new WallSplatter(new Random(0));
        Structure s = wall(1L);
        splatter.recordHit(s, 110.0, 105.0);
        splatter.recordHit(s, 130.0, 110.0);
        splatter.clear();
        assertEquals(0, splatter.size());
        assertEquals(0, splatter.countOn(s));
    }

    @Test
    void recordHitOnNullStructureIsNoOp() {
        WallSplatter splatter = new WallSplatter(new Random(0));
        splatter.recordHit(null, 110.0, 105.0);
        assertEquals(0, splatter.size());
    }

    @Test
    void capRespected() {
        WallSplatter splatter = new WallSplatter(new Random(0));
        Structure s = wall(1L);
        for (int i = 0; i < WallSplatter.MAX_SPLATS + 50; i++) {
            splatter.recordHit(s, 110.0, 105.0);
        }
        assertEquals(WallSplatter.MAX_SPLATS, splatter.size());
    }
}
