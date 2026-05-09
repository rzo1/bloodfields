package com.example.armyclash.ui;

import com.example.armyclash.engine.CorpseField;
import com.example.armyclash.model.Faction;
import com.example.armyclash.model.Unit;
import com.example.armyclash.model.UnitState;
import com.example.armyclash.model.UnitType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class DeathTracker {

    private static final class Snapshot {
        final double x;
        final double y;
        final Faction faction;
        final UnitType type;
        final double maxHp;

        Snapshot(double x, double y, Faction faction, UnitType type, double maxHp) {
            this.x = x;
            this.y = y;
            this.faction = faction;
            this.type = type;
            this.maxHp = maxHp;
        }
    }

    private final Map<Long, Snapshot> lastSeen = new HashMap<>();
    private final Random rotationRandom;

    public DeathTracker() {
        this(new Random());
    }

    public DeathTracker(Random rotationRandom) {
        this.rotationRandom = rotationRandom != null ? rotationRandom : new Random();
    }

    public void detectDeaths(List<Unit> currentUnits, ParticleSystem particles) {
        detectDeaths(currentUnits, particles, null, null, 0.0, 0.0);
    }

    public void detectDeaths(List<Unit> currentUnits, ParticleSystem particles,
                             CorpseField corpses, BloodyTiles tiles,
                             double tileSize, double simTime) {
        detectDeaths(currentUnits, particles, corpses, tiles, tileSize, simTime,
                null, null, null, null, null);
    }

    public void detectDeaths(List<Unit> currentUnits, ParticleSystem particles,
                             CorpseField corpses, BloodyTiles tiles,
                             double tileSize, double simTime,
                             LimbField limbs, RagdollOverlay ragdolls,
                             CorpseRenderer corpseRenderer,
                             Map<Long, UnitType> killerByUnitId,
                             ScorchMarks scorchMarks) {
        Set<Long> currentIds = new HashSet<>();
        if (currentUnits != null) {
            for (Unit u : currentUnits) {
                if (u == null || u.state == UnitState.DEAD) {
                    continue;
                }
                currentIds.add(u.id);
            }
        }
        for (Map.Entry<Long, Snapshot> entry : lastSeen.entrySet()) {
            if (!currentIds.contains(entry.getKey())) {
                Snapshot s = entry.getValue();
                if (s == null) {
                    continue;
                }
                long id = entry.getKey();
                UnitType killer = killerByUnitId != null ? killerByUnitId.get(id) : null;
                if (particles != null) {
                    particles.spawnBloodSplash(s.x, s.y, s.maxHp, killer);
                    particles.spawnBloodPool(s.x, s.y, s.maxHp);
                }
                if (corpses != null) {
                    corpses.recordDeath(id, s.x, s.y, s.faction, s.type);
                    if (corpseRenderer != null) {
                        corpseRenderer.noteKiller(id, killer);
                    }
                }
                if (tiles != null && tileSize > 0.0) {
                    tiles.recordDeath(s.x, s.y, tileSize, simTime);
                }
                if (limbs != null) {
                    limbs.recordDeath(s.x, s.y, s.faction, s.maxHp);
                }
                if (ragdolls != null) {
                    double rot = (Math.PI / 6.0) + rotationRandom.nextDouble() * (Math.PI / 3.0);
                    if (rotationRandom.nextBoolean()) {
                        rot = -rot;
                    }
                    ragdolls.recordDeath(s.x, s.y, rot, s.faction, s.type);
                }
                if (scorchMarks != null && killer == UnitType.DRAGON && tileSize > 0.0) {
                    scorchMarks.recordImpact(s.x, s.y, tileSize);
                }
            }
        }
        lastSeen.clear();
        if (currentUnits != null) {
            for (Unit u : currentUnits) {
                if (u == null || u.state == UnitState.DEAD) {
                    continue;
                }
                lastSeen.put(u.id, new Snapshot(u.x, u.y, u.faction, u.type,
                        u.maxHp > 0.0 ? u.maxHp : (u.type != null ? u.type.maxHp() : 0.0)));
            }
        }
        if (killerByUnitId != null) {
            killerByUnitId.clear();
        }
    }

    public int trackedCount() {
        return lastSeen.size();
    }

    public void clear() {
        lastSeen.clear();
    }
}
