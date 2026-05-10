package com.github.rzo1.bloodfields.ui;

public final class LevelCodes {

    private static final String[] CODES = {
            "SKIRMISH",
            "PIKES",
            "FLANKS",
            "CHARGE",
            "CABAL",
            "STONES",
            "VIGIL",
            "SHADOW",
            "SIEGE",
            "FUCKERY",
            "NIGHTMARE"
    };

    private LevelCodes() {}

    public static String codeFor(int levelIndex) {
        if (levelIndex < 0 || levelIndex >= CODES.length) {
            return null;
        }
        return CODES[levelIndex];
    }

    public static int indexFor(String code) {
        if (code == null) {
            return -1;
        }
        String trimmed = code.trim();
        if (trimmed.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < CODES.length; i++) {
            if (CODES[i].equalsIgnoreCase(trimmed)) {
                return i;
            }
        }
        return -1;
    }

    public static int count() {
        return CODES.length;
    }
}
