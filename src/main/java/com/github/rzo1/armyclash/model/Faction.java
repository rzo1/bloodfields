package com.github.rzo1.armyclash.model;

public enum Faction {
    RED,
    BLUE;

    public Faction opponent() {
        return this == RED ? BLUE : RED;
    }
}
