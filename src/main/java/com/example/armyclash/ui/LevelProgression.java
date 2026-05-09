package com.example.armyclash.ui;

public final class LevelProgression {

    private LevelProgression() {}

    public static int onWin(int currentIndex, int total) {
        if (total <= 0) return 0;
        int next = currentIndex + 1;
        int last = total - 1;
        return Math.min(next, last);
    }

    public static int onLoss(int currentIndex) {
        return currentIndex;
    }

    public static boolean isFinal(int currentIndex, int total) {
        return total > 0 && currentIndex >= total - 1;
    }
}
