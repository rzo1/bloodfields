package com.github.rzo1.bloodfields.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class BattleSummary {

    public enum Mode { CAMPAIGN, VERSUS, SKIRMISH }

    private final Mode mode;
    private final int levelNumber;
    private final Faction winner;
    private final Faction playerFaction;
    private final double simSeconds;
    private final Map<UnitType, Integer> playerArmyComposition;
    private final int playerCasualties;
    private final int enemyCasualties;
    private final int peakCorpsesOnScreen;
    private final int dragonsKilledByPlayer;
    private final int peakDeathsInOneTile;
    private final boolean playerGeneralAlive;
    private final boolean reachedVultureLevel;

    private BattleSummary(Builder b) {
        this.mode = b.mode;
        this.levelNumber = b.levelNumber;
        this.winner = b.winner;
        this.playerFaction = b.playerFaction;
        this.simSeconds = b.simSeconds;
        EnumMap<UnitType, Integer> copy = new EnumMap<>(UnitType.class);
        if (b.playerArmyComposition != null) {
            for (Map.Entry<UnitType, Integer> e : b.playerArmyComposition.entrySet()) {
                if (e.getKey() != null && e.getValue() != null && e.getValue() > 0) {
                    copy.put(e.getKey(), e.getValue());
                }
            }
        }
        this.playerArmyComposition = Collections.unmodifiableMap(copy);
        this.playerCasualties = b.playerCasualties;
        this.enemyCasualties = b.enemyCasualties;
        this.peakCorpsesOnScreen = b.peakCorpsesOnScreen;
        this.dragonsKilledByPlayer = b.dragonsKilledByPlayer;
        this.peakDeathsInOneTile = b.peakDeathsInOneTile;
        this.playerGeneralAlive = b.playerGeneralAlive;
        this.reachedVultureLevel = b.reachedVultureLevel;
    }

    public Mode mode() { return mode; }
    public int levelNumber() { return levelNumber; }
    public Faction winner() { return winner; }
    public Faction playerFaction() { return playerFaction; }
    public double simSeconds() { return simSeconds; }
    public Map<UnitType, Integer> playerArmyComposition() { return playerArmyComposition; }
    public int playerCasualties() { return playerCasualties; }
    public int enemyCasualties() { return enemyCasualties; }
    public int peakCorpsesOnScreen() { return peakCorpsesOnScreen; }
    public int dragonsKilledByPlayer() { return dragonsKilledByPlayer; }
    public int peakDeathsInOneTile() { return peakDeathsInOneTile; }
    public boolean playerGeneralAlive() { return playerGeneralAlive; }
    public boolean reachedVultureLevel() { return reachedVultureLevel; }

    public boolean playerWon() {
        return winner != null && winner == playerFaction;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Mode mode = Mode.CAMPAIGN;
        private int levelNumber;
        private Faction winner;
        private Faction playerFaction = Faction.RED;
        private double simSeconds;
        private Map<UnitType, Integer> playerArmyComposition;
        private int playerCasualties;
        private int enemyCasualties;
        private int peakCorpsesOnScreen;
        private int dragonsKilledByPlayer;
        private int peakDeathsInOneTile;
        private boolean playerGeneralAlive;
        private boolean reachedVultureLevel;

        public Builder mode(Mode m) { this.mode = m; return this; }
        public Builder levelNumber(int n) { this.levelNumber = n; return this; }
        public Builder winner(Faction f) { this.winner = f; return this; }
        public Builder playerFaction(Faction f) { this.playerFaction = f; return this; }
        public Builder simSeconds(double s) { this.simSeconds = s; return this; }
        public Builder playerArmyComposition(Map<UnitType, Integer> m) {
            this.playerArmyComposition = m;
            return this;
        }
        public Builder playerCasualties(int n) { this.playerCasualties = n; return this; }
        public Builder enemyCasualties(int n) { this.enemyCasualties = n; return this; }
        public Builder peakCorpsesOnScreen(int n) { this.peakCorpsesOnScreen = n; return this; }
        public Builder dragonsKilledByPlayer(int n) { this.dragonsKilledByPlayer = n; return this; }
        public Builder peakDeathsInOneTile(int n) { this.peakDeathsInOneTile = n; return this; }
        public Builder playerGeneralAlive(boolean v) { this.playerGeneralAlive = v; return this; }
        public Builder reachedVultureLevel(boolean v) { this.reachedVultureLevel = v; return this; }

        public BattleSummary build() {
            return new BattleSummary(this);
        }
    }
}
