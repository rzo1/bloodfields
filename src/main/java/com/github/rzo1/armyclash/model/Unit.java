package com.github.rzo1.armyclash.model;

public final class Unit {
    public final long id;
    public final UnitType type;
    public final Faction faction;

    public double x;
    public double y;
    public double vx;
    public double vy;
    public double hp;
    public double maxHp;

    public UnitState state;
    public Unit target;
    public double attackCooldownRemaining;
    public boolean routed;
    public boolean garrisoned;

    public Unit(long id, UnitType type, Faction faction, double x, double y) {
        this(id, type, faction, x, y, 1.0);
    }

    public Unit(long id, UnitType type, Faction faction, double x, double y, double hpMultiplier) {
        this.id = id;
        this.type = type;
        this.faction = faction;
        this.x = x;
        this.y = y;
        this.vx = 0.0;
        this.vy = 0.0;
        double mult = hpMultiplier > 0.0 ? hpMultiplier : 1.0;
        this.maxHp = type.maxHp() * mult;
        this.hp = this.maxHp;
        this.state = UnitState.IDLE;
        this.target = null;
        this.attackCooldownRemaining = 0.0;
        this.routed = false;
        this.garrisoned = false;
    }

    public boolean isAlive() {
        return state != UnitState.DEAD && hp > 0.0;
    }

    public void takeDamage(double amount) {
        if (state == UnitState.DEAD) {
            return;
        }
        hp -= amount;
        if (hp <= 0.0) {
            hp = 0.0;
            state = UnitState.DEAD;
            target = null;
            vx = 0.0;
            vy = 0.0;
        }
    }

    public double distanceTo(Unit other) {
        double dx = other.x - x;
        double dy = other.y - y;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
