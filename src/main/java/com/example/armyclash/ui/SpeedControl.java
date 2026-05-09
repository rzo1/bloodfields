package com.example.armyclash.ui;

public final class SpeedControl {

    public enum Speed {
        PAUSED(0.0, "PAUSED"),
        HALF(0.5, "0.5x"),
        NORMAL(1.0, "1x"),
        DOUBLE(2.0, "2x"),
        QUAD(4.0, "4x");

        private final double multiplier;
        private final String label;

        Speed(double multiplier, String label) {
            this.multiplier = multiplier;
            this.label = label;
        }

        public double multiplier() {
            return multiplier;
        }

        public String label() {
            return label;
        }
    }

    private Speed current = Speed.NORMAL;

    public Speed get() {
        return current;
    }

    public void set(Speed s) {
        if (s != null) {
            current = s;
        }
    }

    public void cycle() {
        Speed[] order = Speed.values();
        current = order[(current.ordinal() + 1) % order.length];
    }

    public void togglePause() {
        if (current == Speed.PAUSED) {
            current = Speed.NORMAL;
        } else {
            current = Speed.PAUSED;
        }
    }

    public double multiplier() {
        return current.multiplier();
    }

    public boolean isPaused() {
        return current == Speed.PAUSED;
    }

    public String hudText() {
        switch (current) {
            case PAUSED:
                return "[||] PAUSED";
            case HALF:
                return "> 0.5x";
            case NORMAL:
                return "> 1x";
            case DOUBLE:
                return ">> 2x";
            case QUAD:
                return ">>> 4x";
            default:
                return current.label();
        }
    }
}
