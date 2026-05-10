package com.github.rzo1.armyclash.engine;

public record Structure(long id, double x, double y, double width, double height,
                        StructureType type, double hp) {

    public boolean contains(double px, double py) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }
}
