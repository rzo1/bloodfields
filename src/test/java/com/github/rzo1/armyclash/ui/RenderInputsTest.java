package com.github.rzo1.armyclash.ui;

import com.github.rzo1.armyclash.model.Faction;
import com.github.rzo1.armyclash.model.Projectile;
import com.github.rzo1.armyclash.model.Terrain.TileType;
import com.github.rzo1.armyclash.model.Unit;
import com.github.rzo1.armyclash.model.UnitType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderInputsTest {

    @Test
    void buildsValidRenderInputs() {
        int cols = 40;
        int rows = 25;
        TileType[][] grid = new TileType[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int mod = (r * 7 + c * 3) % 9;
                if (mod == 0) {
                    grid[r][c] = TileType.RIVER;
                } else if (mod == 1) {
                    grid[r][c] = TileType.FOREST;
                } else {
                    grid[r][c] = TileType.GRASS;
                }
            }
        }

        List<Unit> units = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Faction f = (i % 2 == 0) ? Faction.RED : Faction.BLUE;
            UnitType t = UnitType.values()[i % UnitType.values().length];
            Unit u = new Unit(i, t, f, 100.0 + i * 30.0, 200.0 + (i % 3) * 40.0);
            if (i == 3) {
                u.takeDamage(t.maxHp() * 0.4);
            }
            units.add(u);
        }

        List<Projectile> projectiles = new ArrayList<>();
        projectiles.add(new Projectile(50, 50, 1, 0, Faction.RED, 5, units.get(1), 0.0, UnitType.ARCHER));
        projectiles.add(new Projectile(80, 60, -1, 1, Faction.BLUE, 5, units.get(0), 0.0, UnitType.ARCHER));
        Projectile dead = new Projectile(0, 0, 0, 0, Faction.RED, 5, null, 0.0, UnitType.ARCHER);
        dead.alive = false;
        projectiles.add(dead);

        Camera cam = new Camera(10.0, 20.0, 1.25);

        assertEquals(rows, grid.length);
        assertEquals(cols, grid[0].length);
        assertEquals(10, units.size());
        assertEquals(3, projectiles.size());
        assertNotNull(AssetLoader.get().factionFill(Faction.RED));
        assertNotNull(AssetLoader.get().factionStroke(Faction.BLUE));
        assertTrue(cam.zoom > 0.0);
        assertTrue(units.get(3).hp < units.get(3).type.maxHp());
    }

    @Test
    void assetLoaderMissingImageReturnsNullAndCaches() {
        AssetLoader a = AssetLoader.get();
        assertNotNull(a);
        var first = a.image("sprites/__definitely_missing__.png");
        var second = a.image("sprites/__definitely_missing__.png");
        assertEquals(first, second);
    }
}
