package com.github.rzo1.bloodfields.model;

public final class Terrain {
    private Terrain() {}

    public enum TileType {
        GRASS(true, 1.0),
        RIVER(false, 0.0),
        FOREST(true, 0.5);

        private final boolean passable;
        private final double speedMultiplier;

        TileType(boolean passable, double speedMultiplier) {
            this.passable = passable;
            this.speedMultiplier = speedMultiplier;
        }

        public boolean passable() { return passable; }
        public double speedMultiplier() { return speedMultiplier; }
    }
}
