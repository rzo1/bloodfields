package com.github.rzo1.bloodfields.engine;

import com.github.rzo1.bloodfields.model.Army;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.Projectile;
import com.github.rzo1.bloodfields.model.Unit;

import java.util.ArrayList;
import java.util.List;

public final class GameState {
    public enum Phase { MAIN_MENU, DEPLOYMENT, BATTLE, RESULT }

    public Phase phase;
    public World world;
    public Army red;
    public Army blue;
    public final List<Projectile> projectiles;
    public final SpatialHashGrid grid;
    public long tick;
    public long nextUnitId;
    public Faction winner;
    public CorpseField corpses;
    public StructureField structures = new StructureField();
    public FireField fireField = new FireField();
    public final List<WallHit> wallHits = new ArrayList<>();

    public GameState(World world, Army red, Army blue, SpatialHashGrid grid) {
        this.phase = Phase.MAIN_MENU;
        this.world = world;
        this.red = red;
        this.blue = blue;
        this.projectiles = new ArrayList<>();
        this.grid = grid;
        this.tick = 0L;
        this.nextUnitId = 1L;
        this.winner = null;
        this.corpses = new CorpseField();
    }

    public Army armyOf(Faction faction) {
        return faction == Faction.RED ? red : blue;
    }

    public Faction checkVictory() {
        boolean redAlive = anyAlive(red);
        boolean blueAlive = anyAlive(blue);
        if (redAlive && !blueAlive) return Faction.RED;
        if (blueAlive && !redAlive) return Faction.BLUE;
        return null;
    }

    private static boolean anyAlive(Army army) {
        if (army == null) return false;
        for (Unit u : army.units()) {
            if (u.isAlive()) return true;
        }
        return false;
    }
}
