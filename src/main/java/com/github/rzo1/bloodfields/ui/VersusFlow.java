package com.github.rzo1.bloodfields.ui;

public final class VersusFlow {

    public static final int MIN_BUDGET = 100;
    public static final int MAX_BUDGET = 10000;
    public static final int BUDGET_STEP = 50;
    public static final int DEFAULT_BUDGET = 250;

    public static final int MIN_RESERVE = 0;
    public static final int MAX_RESERVE = 50;
    public static final int RESERVE_STEP = 10;
    public static final int DEFAULT_RESERVE = 20;

    public static final int MIN_HANDICAP = 0;
    public static final int MAX_HANDICAP = 200;
    public static final int HANDICAP_STEP = 25;
    public static final int DEFAULT_HANDICAP = 0;

    public static final int CAP_UNLIMITED = 0;
    public static final int[] CAP_OPTIONS = {CAP_UNLIMITED, 4, 6, 8, 12, 16};
    public static final int DEFAULT_PER_TYPE_CAP = CAP_UNLIMITED;

    public enum VersusPhase {
        OPT_IN,
        DEPLOY_P1,
        HANDOVER_TO_P2,
        DEPLOY_P2,
        HANDOVER_TO_BATTLE,
        BATTLE,
        DEPLOY_RESERVES_P1,
        DEPLOY_RESERVES_P2,
        HANDOVER_TO_BATTLE_RESUME,
        RESULT,
        CANCELLED
    }

    private VersusPhase phase = VersusPhase.OPT_IN;
    private boolean p1OptIn;
    private boolean p2OptIn;
    private int budget = DEFAULT_BUDGET;
    private int reservePercent = DEFAULT_RESERVE;
    private int p1HandicapPercent = DEFAULT_HANDICAP;
    private int p2HandicapPercent = DEFAULT_HANDICAP;
    private MapPreset selectedMap = Maps.defaultPreset();
    private Weather selectedWeather = Weather.CLEAR;
    private boolean skirmish;
    private int perTypeCap = DEFAULT_PER_TYPE_CAP;

    public boolean isSkirmish() {
        return skirmish;
    }

    public void setSkirmish(boolean v) {
        this.skirmish = v;
    }

    public VersusPhase phase() {
        return phase;
    }

    public boolean p1OptIn() {
        return p1OptIn;
    }

    public boolean p2OptIn() {
        return p2OptIn;
    }

    public void setP1OptIn(boolean v) {
        this.p1OptIn = v;
    }

    public void setP2OptIn(boolean v) {
        this.p2OptIn = v;
    }

    public boolean dragonsAllowed() {
        if (skirmish) {
            return p1OptIn;
        }
        return p1OptIn && p2OptIn;
    }

    public int budget() {
        return budget;
    }

    public void setBudget(int requested) {
        int clamped = Math.max(MIN_BUDGET, Math.min(MAX_BUDGET, requested));
        int snapped = Math.round((clamped - MIN_BUDGET) / (float) BUDGET_STEP) * BUDGET_STEP + MIN_BUDGET;
        if (snapped > MAX_BUDGET) snapped = MAX_BUDGET;
        if (snapped < MIN_BUDGET) snapped = MIN_BUDGET;
        this.budget = snapped;
    }

    public int reservePercent() {
        return reservePercent;
    }

    public void setReservePercent(int p) {
        int clamped = Math.max(MIN_RESERVE, Math.min(MAX_RESERVE, p));
        int snapped = Math.round(clamped / (float) RESERVE_STEP) * RESERVE_STEP;
        if (snapped > MAX_RESERVE) snapped = MAX_RESERVE;
        if (snapped < MIN_RESERVE) snapped = MIN_RESERVE;
        this.reservePercent = snapped;
    }

    public double reserveFraction() {
        return reservePercent / 100.0;
    }

    public int reserveBudgetFor(int totalBudget) {
        return (int) (totalBudget * reserveFraction());
    }

    public int initialBudgetFor(int totalBudget) {
        return totalBudget - reserveBudgetFor(totalBudget);
    }

    public int p1HandicapPercent() {
        return p1HandicapPercent;
    }

    public int p2HandicapPercent() {
        return p2HandicapPercent;
    }

    public void setP1HandicapPercent(int p) {
        this.p1HandicapPercent = clampHandicap(p);
    }

    public void setP2HandicapPercent(int p) {
        this.p2HandicapPercent = clampHandicap(p);
    }

    public double p1HpMultiplier() {
        return 1.0 + p1HandicapPercent / 100.0;
    }

    public double p2HpMultiplier() {
        return 1.0 + p2HandicapPercent / 100.0;
    }

    private static int clampHandicap(int p) {
        int clamped = Math.max(MIN_HANDICAP, Math.min(MAX_HANDICAP, p));
        int snapped = Math.round(clamped / (float) HANDICAP_STEP) * HANDICAP_STEP;
        if (snapped > MAX_HANDICAP) snapped = MAX_HANDICAP;
        if (snapped < MIN_HANDICAP) snapped = MIN_HANDICAP;
        return snapped;
    }

    public MapPreset selectedMap() {
        return selectedMap;
    }

    public void setSelectedMap(MapPreset preset) {
        if (preset != null) {
            this.selectedMap = preset;
        }
    }

    public Weather selectedWeather() {
        return selectedWeather;
    }

    public void setSelectedWeather(Weather w) {
        if (w != null) {
            this.selectedWeather = w;
        }
    }

    public void optInComplete() {
        if (phase == VersusPhase.OPT_IN) {
            phase = VersusPhase.DEPLOY_P1;
        }
    }

    public void endP1Turn() {
        if (phase == VersusPhase.DEPLOY_P1) {
            phase = skirmish ? VersusPhase.HANDOVER_TO_BATTLE : VersusPhase.HANDOVER_TO_P2;
        }
    }

    public void handoverToP2Done() {
        if (phase == VersusPhase.HANDOVER_TO_P2) {
            phase = VersusPhase.DEPLOY_P2;
        }
    }

    public void endP2Turn() {
        if (phase == VersusPhase.DEPLOY_P2) {
            phase = VersusPhase.HANDOVER_TO_BATTLE;
        }
    }

    public void handoverToBattleDone() {
        if (phase == VersusPhase.HANDOVER_TO_BATTLE) {
            phase = VersusPhase.BATTLE;
        }
    }

    public void battleEnded() {
        if (phase == VersusPhase.BATTLE) {
            phase = VersusPhase.RESULT;
        }
    }

    public void requestReservesP1() {
        if (phase == VersusPhase.BATTLE) {
            phase = VersusPhase.DEPLOY_RESERVES_P1;
        }
    }

    public void requestReservesP2() {
        if (phase == VersusPhase.BATTLE) {
            phase = VersusPhase.DEPLOY_RESERVES_P2;
        }
    }

    public void endReservesTurn() {
        if (phase == VersusPhase.DEPLOY_RESERVES_P1 || phase == VersusPhase.DEPLOY_RESERVES_P2) {
            phase = VersusPhase.HANDOVER_TO_BATTLE_RESUME;
        }
    }

    public void handoverToBattleResumeDone() {
        if (phase == VersusPhase.HANDOVER_TO_BATTLE_RESUME) {
            phase = VersusPhase.BATTLE;
        }
    }

    public void cancel() {
        phase = VersusPhase.CANCELLED;
    }

    public int perTypeCap() {
        return perTypeCap;
    }

    public void setPerTypeCap(int v) {
        for (int allowed : CAP_OPTIONS) {
            if (allowed == v) {
                this.perTypeCap = v;
                return;
            }
        }
        this.perTypeCap = DEFAULT_PER_TYPE_CAP;
    }

    public boolean hasPerTypeCap() {
        return perTypeCap > 0;
    }

    public void applyCapsTo(com.github.rzo1.bloodfields.model.Army army) {
        if (army == null || !hasPerTypeCap()) return;
        for (com.github.rzo1.bloodfields.model.UnitType t : com.github.rzo1.bloodfields.model.UnitType.values()) {
            if (!t.playerSelectable()) continue;
            if (t.unique()) continue;
            army.setCap(t, perTypeCap);
        }
    }
}
