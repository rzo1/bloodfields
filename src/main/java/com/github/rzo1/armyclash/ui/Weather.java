package com.github.rzo1.armyclash.ui;

public enum Weather {
    CLEAR("Clear", "Cloudless skies; no effect on combat.",
            "#000000", 0.0, 1.0, 1.0, false),
    FOG("Fog", "Thick fog reduces ranged attack range by 30%.",
            "#a0a8a8", 0.25, 0.7, 1.0, false),
    RAIN("Rain", "Soaked ground slows units by 15%.",
            "#1a2030", 0.18, 1.0, 0.85, true),
    SNOW("Snow", "Falling snow; cosmetic only.",
            "#d8e0ec", 0.10, 1.0, 1.0, true),
    NIGHT("Night", "Darkness reduces ranged attack range by 20%.",
            "#0a1020", 0.45, 0.8, 1.0, false);

    private final String displayName;
    private final String description;
    private final String tintHex;
    private final double tintAlpha;
    private final double rangedRangeMult;
    private final double speedMult;
    private final boolean particles;

    Weather(String displayName, String description, String tintHex, double tintAlpha,
            double rangedRangeMult, double speedMult, boolean particles) {
        this.displayName = displayName;
        this.description = description;
        this.tintHex = tintHex;
        this.tintAlpha = tintAlpha;
        this.rangedRangeMult = rangedRangeMult;
        this.speedMult = speedMult;
        this.particles = particles;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public String tint() {
        return tintHex;
    }

    public double tintAlpha() {
        return tintAlpha;
    }

    public double rangedRangeMult() {
        return rangedRangeMult;
    }

    public double speedMult() {
        return speedMult;
    }

    public boolean particles() {
        return particles;
    }
}
