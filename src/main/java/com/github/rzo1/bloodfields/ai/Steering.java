package com.github.rzo1.bloodfields.ai;

import com.github.rzo1.bloodfields.model.Unit;

import java.util.List;

public final class Steering {

    private Steering() {}

    public static void seek(Unit u, double targetX, double targetY, double speed) {
        double dx = targetX - u.x;
        double dy = targetY - u.y;
        double mag = Math.sqrt(dx * dx + dy * dy);
        if (mag <= 1.0e-9) {
            u.vx = 0.0;
            u.vy = 0.0;
            return;
        }
        double inv = speed / mag;
        u.vx = dx * inv;
        u.vy = dy * inv;
    }

    public static void applySeparation(Unit u, List<Unit> neighbors, double radius, double weight) {
        applySeparation(u, neighbors, radius, weight, u.type.speed());
    }

    public static void applySeparation(Unit u, List<Unit> neighbors, double radius, double weight,
                                       double maxSpeed) {
        if (neighbors == null || neighbors.isEmpty() || radius <= 0.0) {
            clampSpeed(u, maxSpeed);
            return;
        }
        double r2 = radius * radius;
        for (int i = 0, n = neighbors.size(); i < n; i++) {
            Unit other = neighbors.get(i);
            if (other == null || other == u) continue;
            double dx = u.x - other.x;
            double dy = u.y - other.y;
            double d2 = dx * dx + dy * dy;
            if (d2 >= r2 || d2 <= 0.0) continue;
            double d = Math.sqrt(d2);
            double push = weight * (radius - d) / radius;
            u.vx += (dx / d) * push;
            u.vy += (dy / d) * push;
        }
        clampSpeed(u, maxSpeed);
    }

    /**
     * Allocation-free variant: takes a (possibly mixed-faction) neighbor list
     * and filters by ally faction inline.
     */
    public static void applyAllySeparation(Unit u, List<Unit> neighbors, double radius, double weight,
                                           double maxSpeed) {
        if (neighbors == null || neighbors.isEmpty() || radius <= 0.0) {
            clampSpeed(u, maxSpeed);
            return;
        }
        double r2 = radius * radius;
        for (int i = 0, n = neighbors.size(); i < n; i++) {
            Unit other = neighbors.get(i);
            if (other == null || other == u) continue;
            if (other.faction != u.faction) continue;
            double dx = u.x - other.x;
            double dy = u.y - other.y;
            double d2 = dx * dx + dy * dy;
            if (d2 >= r2 || d2 <= 0.0) continue;
            double d = Math.sqrt(d2);
            double push = weight * (radius - d) / radius;
            u.vx += (dx / d) * push;
            u.vy += (dy / d) * push;
        }
        clampSpeed(u, maxSpeed);
    }

    public static void stop(Unit u) {
        u.vx = 0.0;
        u.vy = 0.0;
    }

    private static void clampSpeed(Unit u, double maxSpeed) {
        double mag = Math.sqrt(u.vx * u.vx + u.vy * u.vy);
        if (mag > maxSpeed && mag > 0.0) {
            double s = maxSpeed / mag;
            u.vx *= s;
            u.vy *= s;
        }
    }
}
