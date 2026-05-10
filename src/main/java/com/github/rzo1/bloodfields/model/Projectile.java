package com.github.rzo1.bloodfields.model;

public final class Projectile {
    public double x;
    public double y;
    public double vx;
    public double vy;
    public final Faction owner;
    public final double damage;
    public Unit target;
    public final double splashRadius;
    public final UnitType attackerType;
    public boolean alive;

    public Projectile(double x, double y, double vx, double vy,
                      Faction owner, double damage, Unit target,
                      double splashRadius, UnitType attackerType) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.owner = owner;
        this.damage = damage;
        this.target = target;
        this.splashRadius = splashRadius;
        this.attackerType = attackerType;
        this.alive = true;
    }
}
