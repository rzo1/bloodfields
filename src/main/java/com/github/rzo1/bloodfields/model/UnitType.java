package com.github.rzo1.bloodfields.model;

public enum UnitType {
    INFANTRY("Infantry", 50.0, 10.0, 18.0, 40.0, 1.0, false, 10, 0.0, true, 0.0, false, false, false,
            "Tough melee line. Counters archers."),
    ARCHER("Archer", 30.0, 8.0, 180.0, 35.0, 1.5, true, 15, 0.0, true, 0.0, false, false, false,
            "Long-range. Strong vs cavalry."),
    CAVALRY("Cavalry", 80.0, 15.0, 20.0, 80.0, 1.2, false, 25, 0.0, true, 0.0, false, false, false,
            "Fast charger. Crushes infantry, dies to archers."),
    MAGE("Mage", 35.0, 18.0, 140.0, 30.0, 2.0, true, 30, 50.0, true, 0.0, false, false, false,
            "Ranged AoE splash. Slow and fragile."),
    DRAGON("Dragon", 600.0, 80.0, 110.0, 55.0, 1.4, true, 999, 45.0, false, 0.0, false, false, true,
            "Flying boss. Ignores terrain and walls. Massive HP and fire breath. (Enemy/opt-in only.)"),
    NECROMANCER("Necromancer", 60.0, 8.0, 130.0, 30.0, 3.0, true, 50, 0.0, true, 80.0, false, false, false,
            "Revives a corpse for your side or detonates it (friendly fire)."),
    HEALER("Healer", 40.0, 0.0, 80.0, 35.0, 1.5, true, 35, 0.0, true, 80.0, false, false, false,
            "Heals nearby allies. No direct damage."),
    CATAPULT("Catapult", 120.0, 35.0, 240.0, 15.0, 4.0, true, 60, 70.0, true, 0.0, false, false, false,
            "Slow siege engine. Massive AoE."),
    ASSASSIN("Assassin", 25.0, 22.0, 18.0, 110.0, 0.6, false, 30, 0.0, true, 0.0, true, false, false,
            "Fast melee. High damage, low HP. Ignores armor."),
    GOLEM("Golem", 300.0, 20.0, 22.0, 20.0, 2.0, false, 80, 0.0, true, 0.0, false, false, false,
            "Slow walking tank. Devastating in melee."),
    PIKEMAN("Pikeman", 55.0, 11.0, 25.0, 38.0, 1.0, false, 15, 0.0, true, 0.0, false, false, false,
            "Anti-cavalry spearman."),
    GENERAL("General", 180.0, 18.0, 22.0, 45.0, 1.5, false, 100, 0.0, true, 120.0, false, true, false,
            "Hero. Allies in range gain 25% damage. (Unique — one per army.)");

    private final String displayName;
    private final double maxHp;
    private final double damage;
    private final double attackRange;
    private final double speed;
    private final double attackCooldownSeconds;
    private final boolean ranged;
    private final int cost;
    private final double splashRadius;
    private final boolean playerSelectable;
    private final double abilityRadius;
    private final boolean armorPiercing;
    private final boolean unique;
    private final boolean flying;
    private final String description;

    UnitType(String displayName, double maxHp, double damage, double attackRange,
             double speed, double attackCooldownSeconds, boolean ranged, int cost,
             double splashRadius, boolean playerSelectable, double abilityRadius,
             boolean armorPiercing, boolean unique, boolean flying, String description) {
        this.displayName = displayName;
        this.maxHp = maxHp;
        this.damage = damage;
        this.attackRange = attackRange;
        this.speed = speed;
        this.attackCooldownSeconds = attackCooldownSeconds;
        this.ranged = ranged;
        this.cost = cost;
        this.splashRadius = splashRadius;
        this.playerSelectable = playerSelectable;
        this.abilityRadius = abilityRadius;
        this.armorPiercing = armorPiercing;
        this.unique = unique;
        this.flying = flying;
        this.description = description;
    }

    public String displayName() { return displayName; }
    public double maxHp() { return maxHp; }
    public double damage() { return damage; }
    public double attackRange() { return attackRange; }
    public double speed() { return speed; }
    public double attackCooldownSeconds() { return attackCooldownSeconds; }
    public boolean ranged() { return ranged; }
    public int cost() { return cost; }
    public double splashRadius() { return splashRadius; }
    public boolean playerSelectable() { return playerSelectable; }
    public double abilityRadius() { return abilityRadius; }
    public boolean armorPiercing() { return armorPiercing; }
    public boolean unique() { return unique; }
    public boolean flying() { return flying; }
    public String description() { return description; }
}
