package com.example.armyclash.ui;

import com.example.armyclash.ai.Pathfinding;
import com.example.armyclash.engine.GameState;
import com.example.armyclash.engine.World;
import com.example.armyclash.model.Army;
import com.example.armyclash.model.HeroSkill;
import com.example.armyclash.model.Unit;
import com.example.armyclash.model.UnitType;

import java.util.List;

public final class Levels {

    private Levels() {}

    static void place(Army enemy, GameState state, UnitType type, double x, double y) {
        double rx = x;
        double ry = y;
        World world = state == null ? null : state.world;
        if (world != null && !world.passableAt(x, y)) {
            int col = (int) (x / world.tileSize);
            int row = (int) (y / world.tileSize);
            if (col < 0) col = 0;
            if (row < 0) row = 0;
            if (col >= world.cols()) col = world.cols() - 1;
            if (row >= world.rows()) row = world.rows() - 1;
            int[] nearest = Pathfinding.findNearestPassable(world, col, row);
            if (nearest != null) {
                rx = (nearest[0] + 0.5) * world.tileSize;
                ry = (nearest[1] + 0.5) * world.tileSize;
            }
        }
        enemy.add(new Unit(state.nextUnitId++, type, enemy.faction(), rx, ry, enemy.hpMultiplier()));
    }

    public static List<Level> all() {
        return List.of(
                new Level(1, "Skirmish Line", 220, Levels::skirmishLine, Weather.CLEAR, "plains"),
                new Level(2, "Pike Wall", 280, Levels::pikeWall, Weather.CLEAR, "plains"),
                new Level(3, "Archers on the Flanks", 360, Levels::archersOnFlanks, Weather.CLEAR, "bridge"),
                new Level(4, "Cavalry Charge", 440, Levels::cavalryCharge, Weather.RAIN, "twin"),
                new Level(5, "Mage Cabal", 520, Levels::mageCabal, Weather.FOG, "crossroads"),
                new Level(6, "Stone Sentinels", 600, Levels::stoneSentinels, Weather.CLEAR, "woods"),
                new Level(7, "Healer's Stand", 700, Levels::healersStand, Weather.CLEAR, "mire"),
                new Level(8, "Shadow Cult", 800, Levels::shadowCult, Weather.NIGHT, "gauntlet"),
                new Level(9, "Siege Towers", 900, Levels::siegeTowers, Weather.CLEAR, "fortress_wall"),
                new Level(10, "Final Fuckery", 2200, Levels::finalFuckery, Weather.NIGHT, "fortress_wall")
        );
    }

    private static void skirmishLine(Army enemy, GameState state, int width, int height) {
        double cx = width / 2.0;
        int rows = 2;
        int perRow = 16;
        double rowSpacing = 50.0;
        double colSpacing = 70.0;
        double startY = 90.0;
        for (int r = 0; r < rows; r++) {
            double y = startY + r * rowSpacing;
            for (int i = 0; i < perRow; i++) {
                double x = cx + (i - (perRow - 1) / 2.0) * colSpacing;
                place(enemy, state, UnitType.INFANTRY, x, y);
            }
        }
    }

    private static void pikeWall(Army enemy, GameState state, int width, int height) {
        double cx = width / 2.0;
        double pikeSpacing = 60.0;
        double[] pikeYs = {180.0, 230.0};
        for (double y : pikeYs) {
            for (int i = 0; i < 12; i++) {
                double x = cx + (i - 5.5) * pikeSpacing;
                place(enemy, state, UnitType.PIKEMAN, x, y);
            }
        }
        double infantrySpacing = 90.0;
        for (int i = 0; i < 8; i++) {
            double x = cx + (i - 3.5) * infantrySpacing;
            place(enemy, state, UnitType.INFANTRY, x, 110.0);
        }
    }

    private static void archersOnFlanks(Army enemy, GameState state, int width, int height) {
        double cx = width / 2.0;
        double infantrySpacing = 60.0;
        double[] infantryYs = {120.0, 170.0, 220.0};
        for (double y : infantryYs) {
            for (int i = 0; i < 8; i++) {
                double x = cx + (i - 3.5) * infantrySpacing;
                place(enemy, state, UnitType.INFANTRY, x, y);
            }
        }
        double leftX1 = width * 0.05;
        double leftX2 = width * 0.10;
        double rightX1 = width * 0.95;
        double rightX2 = width * 0.90;
        double[] archerYs = {110.0, 170.0, 230.0};
        for (double y : archerYs) {
            place(enemy, state, UnitType.ARCHER, leftX1, y);
            place(enemy, state, UnitType.ARCHER, leftX2, y);
            place(enemy, state, UnitType.ARCHER, rightX1, y);
            place(enemy, state, UnitType.ARCHER, rightX2, y);
        }
    }

    private static void cavalryCharge(Army enemy, GameState state, int width, int height) {
        double cx = width / 2.0;
        double cavalrySpacing = 70.0;
        double[] cavalryYs = {220.0, 270.0};
        for (double y : cavalryYs) {
            for (int i = 0; i < 14; i++) {
                double x = cx + (i - 6.5) * cavalrySpacing;
                place(enemy, state, UnitType.CAVALRY, x, y);
            }
        }
        double infantrySpacing = 60.0;
        double[] infantryYs = {80.0, 120.0, 160.0};
        for (int row = 0; row < 3; row++) {
            int count = 10;
            double y = infantryYs[row];
            for (int i = 0; i < count; i++) {
                double x = cx + (i - (count - 1) / 2.0) * infantrySpacing;
                place(enemy, state, UnitType.INFANTRY, x, y);
            }
        }
    }

    private static void mageCabal(Army enemy, GameState state, int width, int height) {
        double cx = width / 2.0;
        double infantrySpacing = 55.0;
        for (int i = 0; i < 16; i++) {
            double x = cx + (i - 7.5) * infantrySpacing;
            place(enemy, state, UnitType.INFANTRY, x, 230.0);
        }
        double archerSpacing = 70.0;
        for (int i = 0; i < 16; i++) {
            double x = cx + (i - 7.5) * archerSpacing;
            place(enemy, state, UnitType.ARCHER, x, 150.0);
        }
        double mageSpacing = 95.0;
        for (int i = 0; i < 12; i++) {
            double x = cx + (i - 5.5) * mageSpacing;
            place(enemy, state, UnitType.MAGE, x, 70.0);
        }
    }

    private static void stoneSentinels(Army enemy, GameState state, int width, int height) {
        double cx = width / 2.0;
        double golemSpacing = 180.0;
        for (int i = 0; i < 6; i++) {
            double x = cx + (i - 2.5) * golemSpacing;
            place(enemy, state, UnitType.GOLEM, x, 240.0);
        }
        double infantrySpacing = 60.0;
        for (int i = 0; i < 16; i++) {
            double x = cx + (i - 7.5) * infantrySpacing;
            place(enemy, state, UnitType.INFANTRY, x, 170.0);
        }
        double archerSpacing = 70.0;
        for (int i = 0; i < 16; i++) {
            double x = cx + (i - 7.5) * archerSpacing;
            place(enemy, state, UnitType.ARCHER, x, 90.0);
        }
    }

    private static void healersStand(Army enemy, GameState state, int width, int height) {
        double cx = width / 2.0;
        double infantrySpacing = 50.0;
        double[] infantryYs = {200.0, 245.0};
        for (double y : infantryYs) {
            for (int i = 0; i < 12; i++) {
                double x = cx + (i - 5.5) * infantrySpacing;
                place(enemy, state, UnitType.INFANTRY, x, y);
            }
        }
        double pikeSpacing = 90.0;
        for (int i = 0; i < 12; i++) {
            double x = cx + (i - 5.5) * pikeSpacing;
            place(enemy, state, UnitType.PIKEMAN, x, 295.0);
        }
        double archerSpacing = 130.0;
        for (int i = 0; i < 8; i++) {
            double x = cx + (i - 3.5) * archerSpacing;
            place(enemy, state, UnitType.ARCHER, x, 100.0);
        }
        double healerSpacing = 130.0;
        for (int i = 0; i < 8; i++) {
            double x = cx + (i - 3.5) * healerSpacing;
            place(enemy, state, UnitType.HEALER, x, 150.0);
        }
    }

    private static void shadowCult(Army enemy, GameState state, int width, int height) {
        double cx = width / 2.0;
        double infantrySpacing = 50.0;
        double[] infantryYs = {200.0, 250.0};
        for (double y : infantryYs) {
            for (int i = 0; i < 12; i++) {
                double x = cx + (i - 5.5) * infantrySpacing;
                place(enemy, state, UnitType.INFANTRY, x, y);
            }
        }
        double assassinSpacing = 80.0;
        for (int i = 0; i < 12; i++) {
            double x = cx + (i - 5.5) * assassinSpacing;
            place(enemy, state, UnitType.ASSASSIN, x, 130.0);
        }
        double necroSpacing = 240.0;
        for (int i = 0; i < 4; i++) {
            double x = cx + (i - 1.5) * necroSpacing;
            place(enemy, state, UnitType.NECROMANCER, x, 65.0);
        }
    }

    private static void siegeTowers(Army enemy, GameState state, int width, int height) {
        double cx = width / 2.0;
        double infantrySpacing = 50.0;
        for (int i = 0; i < 24; i++) {
            double x = cx + (i - 11.5) * infantrySpacing;
            place(enemy, state, UnitType.INFANTRY, x, 340.0);
        }
        double archerSpacing = 70.0;
        for (int i = 0; i < 16; i++) {
            double x = cx + (i - 7.5) * archerSpacing;
            place(enemy, state, UnitType.ARCHER, x, 280.0);
        }
        double catapultSpacing = 140.0;
        for (int i = 0; i < 8; i++) {
            double x = cx + (i - 3.5) * catapultSpacing;
            place(enemy, state, UnitType.CATAPULT, x, 150.0);
        }
        place(enemy, state, UnitType.GENERAL, cx, 80.0);
        enemy.setHeroSkill(HeroSkill.IRON_DISCIPLINE);
    }

    private static void finalFuckery(Army enemy, GameState state, int width, int height) {
        double cx = width / 2.0;

        double infantrySpacing = 50.0;
        for (int i = 0; i < 24; i++) {
            double x = cx + (i - 11.5) * infantrySpacing;
            place(enemy, state, UnitType.INFANTRY, x, 350.0);
        }
        double pikeSpacing = 50.0;
        for (int i = 0; i < 24; i++) {
            double x = cx + (i - 11.5) * pikeSpacing;
            place(enemy, state, UnitType.PIKEMAN, x, 305.0);
        }
        double golemSpacing = 140.0;
        for (int i = 0; i < 8; i++) {
            double x = cx + (i - 3.5) * golemSpacing;
            place(enemy, state, UnitType.GOLEM, x, 260.0);
        }
        double archerSpacing = 70.0;
        for (int i = 0; i < 16; i++) {
            double x = cx + (i - 7.5) * archerSpacing;
            place(enemy, state, UnitType.ARCHER, x, 220.0);
        }
        double mageSpacing = 95.0;
        for (int i = 0; i < 12; i++) {
            double x = cx + (i - 5.5) * mageSpacing;
            place(enemy, state, UnitType.MAGE, x, 165.0);
        }
        double catapultSpacing = 140.0;
        for (int i = 0; i < 8; i++) {
            double x = cx + (i - 3.5) * catapultSpacing;
            place(enemy, state, UnitType.CATAPULT, x, 115.0);
        }
        double necroSpacing = 180.0;
        for (int i = 0; i < 6; i++) {
            double x = cx + (i - 2.5) * necroSpacing;
            place(enemy, state, UnitType.NECROMANCER, x, 65.0);
        }
        place(enemy, state, UnitType.GENERAL, cx - 80.0, 30.0);
        place(enemy, state, UnitType.DRAGON, cx + 80.0, 30.0);
        enemy.setHeroSkill(HeroSkill.BATTLE_LUST);
    }
}
