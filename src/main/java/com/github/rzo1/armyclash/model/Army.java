package com.github.rzo1.armyclash.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class Army {

    public static final double MIN_HP_MULTIPLIER = 1.0;
    public static final double MAX_HP_MULTIPLIER = 3.0;

    private final Faction faction;
    private final List<Unit> units;
    private int deploymentBudget;
    private int reserveBudget;
    private HeroSkill heroSkill;
    private double hpMultiplier = 1.0;
    private final Map<UnitType, Integer> caps = new EnumMap<>(UnitType.class);

    public Army(Faction faction, int deploymentBudget) {
        this.faction = faction;
        this.deploymentBudget = deploymentBudget;
        this.units = new ArrayList<>();
        this.reserveBudget = 0;
        this.heroSkill = null;
    }

    public double hpMultiplier() {
        return hpMultiplier;
    }

    public void setHpMultiplier(double m) {
        if (Double.isNaN(m)) {
            return;
        }
        if (m < MIN_HP_MULTIPLIER) m = MIN_HP_MULTIPLIER;
        if (m > MAX_HP_MULTIPLIER) m = MAX_HP_MULTIPLIER;
        this.hpMultiplier = m;
    }

    public HeroSkill heroSkill() { return heroSkill; }

    public void setHeroSkill(HeroSkill skill) { this.heroSkill = skill; }

    public boolean hasGeneralAlive() {
        for (Unit u : units) {
            if (u.type == UnitType.GENERAL && u.isAlive()) return true;
        }
        return false;
    }

    public Faction faction() { return faction; }

    public int deploymentBudget() { return deploymentBudget; }

    public int reserveBudget() { return reserveBudget; }

    public void setReserveBudget(int v) {
        this.reserveBudget = Math.max(0, v);
    }

    public void activateReserves() {
        if (reserveBudget > 0) {
            deploymentBudget += reserveBudget;
            reserveBudget = 0;
        }
    }

    public List<Unit> units() {
        return Collections.unmodifiableList(units);
    }

    public void add(Unit unit) {
        if (unit.faction != faction) {
            throw new IllegalArgumentException("Unit faction " + unit.faction + " does not match army faction " + faction);
        }
        units.add(unit);
    }

    public boolean remove(Unit unit) {
        return units.remove(unit);
    }

    public int spentPoints() {
        int total = 0;
        for (Unit u : units) {
            total += u.type.cost();
        }
        return total;
    }

    public int remainingBudget() {
        return deploymentBudget - spentPoints();
    }

    public void setCap(UnitType type, int max) {
        if (type == null) return;
        if (max < 0) max = 0;
        caps.put(type, max);
    }

    public int capFor(UnitType type) {
        if (type == null) return Integer.MAX_VALUE;
        Integer v = caps.get(type);
        return v == null ? Integer.MAX_VALUE : v;
    }

    public int countOf(UnitType type) {
        if (type == null) return 0;
        int n = 0;
        for (Unit u : units) {
            if (u.type == type) n++;
        }
        return n;
    }
}
