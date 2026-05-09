package com.example.armyclash.ui;

import com.example.armyclash.model.UnitType;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RendererShapesTest {

    @Test
    void shapeForReturnsNonNullForAllTypes() {
        for (UnitType t : UnitType.values()) {
            assertNotNull(Renderer.shapeFor(t), "shape for " + t);
        }
    }

    @Test
    void shapeForReturnsDistinctShapesPerType() {
        Set<UnitShape> seen = EnumSet.noneOf(UnitShape.class);
        for (UnitType t : UnitType.values()) {
            UnitShape s = Renderer.shapeFor(t);
            assertNotNull(s);
            seen.add(s);
        }
        assertEquals(UnitType.values().length, seen.size(),
                "each unit type should map to a distinct shape");
    }

    @Test
    void shapeForSpecificMappings() {
        assertEquals(UnitShape.SQUARE, Renderer.shapeFor(UnitType.INFANTRY));
        assertEquals(UnitShape.TRIANGLE, Renderer.shapeFor(UnitType.ARCHER));
        assertEquals(UnitShape.HORIZONTAL_RECT, Renderer.shapeFor(UnitType.CAVALRY));
        assertEquals(UnitShape.CIRCLE_WITH_HAT, Renderer.shapeFor(UnitType.MAGE));
    }

    @Test
    void shapeForNullDefaultsToSquare() {
        assertEquals(UnitShape.SQUARE, Renderer.shapeFor(null));
    }

    @Test
    void projectileStyleArcherVsMageDistinct() {
        ProjectileStyle archer = Renderer.projectileStyleFor(UnitType.ARCHER);
        ProjectileStyle mage = Renderer.projectileStyleFor(UnitType.MAGE);
        assertNotNull(archer);
        assertNotNull(mage);
        assertNotEquals(archer, mage);
    }

    @Test
    void projectileStyleSpecificMappings() {
        assertEquals(ProjectileStyle.ARROW, Renderer.projectileStyleFor(UnitType.ARCHER));
        assertEquals(ProjectileStyle.FIREBALL, Renderer.projectileStyleFor(UnitType.MAGE));
        assertEquals(ProjectileStyle.BOULDER, Renderer.projectileStyleFor(UnitType.CATAPULT));
        assertEquals(ProjectileStyle.DEFAULT, Renderer.projectileStyleFor(UnitType.INFANTRY));
        assertEquals(ProjectileStyle.DEFAULT, Renderer.projectileStyleFor(null));
    }

    @Test
    void projectileStyleNonNullForAllTypes() {
        Set<ProjectileStyle> styles = new HashSet<>();
        for (UnitType t : UnitType.values()) {
            ProjectileStyle s = Renderer.projectileStyleFor(t);
            assertNotNull(s);
            styles.add(s);
        }
        assertNotEquals(0, styles.size());
    }
}
