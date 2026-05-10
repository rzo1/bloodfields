package com.github.rzo1.bloodfields.engine;

public enum StructureType {
    WALL(200.0, true),
    TOWER(300.0, true),
    GATE(100.0, true);

    private final double maxHp;
    private final boolean blocksWhenAlive;

    StructureType(double maxHp, boolean blocksWhenAlive) {
        this.maxHp = maxHp;
        this.blocksWhenAlive = blocksWhenAlive;
    }

    public double maxHp() {
        return maxHp;
    }

    public boolean blocksWhenAlive() {
        return blocksWhenAlive;
    }
}
