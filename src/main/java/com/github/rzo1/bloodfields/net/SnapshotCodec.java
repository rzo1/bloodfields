package com.github.rzo1.bloodfields.net;

import com.github.rzo1.bloodfields.cli.HeadlessGame;
import com.github.rzo1.bloodfields.engine.Corpse;
import com.github.rzo1.bloodfields.engine.FireField;
import com.github.rzo1.bloodfields.engine.GameState;
import com.github.rzo1.bloodfields.engine.Structure;
import com.github.rzo1.bloodfields.engine.World;
import com.github.rzo1.bloodfields.model.Army;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.Projectile;
import com.github.rzo1.bloodfields.model.Terrain;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitType;
import com.github.rzo1.bloodfields.net.proto.ArmyState;
import com.github.rzo1.bloodfields.net.proto.CorpseInfo;
import com.github.rzo1.bloodfields.net.proto.FireTile;
import com.github.rzo1.bloodfields.net.proto.Phase;
import com.github.rzo1.bloodfields.net.proto.ProjectileState;
import com.github.rzo1.bloodfields.net.proto.Snapshot;
import com.github.rzo1.bloodfields.net.proto.StructureState;
import com.github.rzo1.bloodfields.net.proto.Tile;
import com.github.rzo1.bloodfields.net.proto.UnitState;
import com.github.rzo1.bloodfields.ui.MapPreset;
import com.github.rzo1.bloodfields.ui.Maps;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Full match-state snapshots: {@link #capture} serializes a {@link HeadlessGame}
 * (plus the match config and phase) into an opaque, versioned {@link Snapshot};
 * {@link #restore} rebuilds a fresh {@link HeadlessGame} from one. Bytes are
 * produced on SaveGame and accepted back on RestoreGame; version 1 only.
 */
public final class SnapshotCodec {

    public static final int VERSION = 1;

    private SnapshotCodec() {}

    public static Snapshot capture(HeadlessGame game, MatchConfig cfg, Phase phase) {
        return capture(game, cfg, phase, List.of());
    }

    public static Snapshot capture(HeadlessGame game, MatchConfig cfg, Phase phase,
                                   List<String> playerNames) {
        if (game == null) throw new IllegalArgumentException("game");
        if (phase != Phase.DEPLOY && phase != Phase.BATTLE && phase != Phase.ENDED) {
            throw new IllegalArgumentException("cannot snapshot phase " + phase);
        }
        GameState st = game.state();
        Snapshot.Builder b = Snapshot.newBuilder()
                .setVersion(VERSION)
                .setMapId(cfg.mapId())
                .setBudget(cfg.budget())
                .setDragons(cfg.dragons())
                .setPhase(phase)
                .setTick(st.tick)
                .setSimTime(game.simTime())
                .setRngSeed(st.rngSeed)
                .setNextUnitId(st.nextUnitId)
                .setWinner(ProtoMapper.factionKind(st.winner != null ? st.winner : game.winner()))
                .setRed(armyState(st.red))
                .setBlue(armyState(st.blue));
        if (playerNames != null) b.addAllPlayerNames(playerNames);
        for (Unit u : st.red.units()) b.addUnits(unitState(u));
        for (Unit u : st.blue.units()) b.addUnits(unitState(u));
        for (Projectile p : st.projectiles) b.addProjectiles(projectileState(p));
        for (Corpse c : st.corpses.corpses()) {
            b.addCorpses(CorpseInfo.newBuilder()
                    .setX(c.x())
                    .setY(c.y())
                    .setFaction(ProtoMapper.factionKind(c.faction()))
                    .setType(ProtoMapper.unitKind(c.type()))
                    .build());
        }
        for (Map.Entry<Long, Double> e : st.fireField.activeFires().entrySet()) {
            if (e.getValue() != null && e.getValue() > 0.0) {
                b.addFires(FireTile.newBuilder()
                        .setCol(FireField.colOf(e.getKey()))
                        .setRow(FireField.rowOf(e.getKey()))
                        .setRemainingSeconds(e.getValue())
                        .build());
            }
        }
        for (Map.Entry<Long, Boolean> e : st.fireField.scorchedTiles().entrySet()) {
            if (Boolean.TRUE.equals(e.getValue())) {
                b.addScorched(Tile.newBuilder()
                        .setCol(FireField.colOf(e.getKey()))
                        .setRow(FireField.rowOf(e.getKey()))
                        .build());
            }
        }
        for (Structure s : st.structures.structures()) {
            b.addStructures(StructureState.newBuilder()
                    .setId(s.id())
                    .setType(ProtoMapper.structureKind(s.type()))
                    .setX(s.x())
                    .setY(s.y())
                    .setWidth(s.width())
                    .setHeight(s.height())
                    .setDestroyed(st.structures.isDestroyed(s))
                    .setOpen(st.structures.isOpen(s))
                    .setHp(st.structures.hpOf(s))
                    .build());
        }
        return b.build();
    }

    public static HeadlessGame restore(Snapshot snap) {
        if (snap.getVersion() != VERSION) {
            throw new IllegalArgumentException("unsupported snapshot version: " + snap.getVersion());
        }
        Phase phase = snap.getPhase();
        if (phase != Phase.DEPLOY && phase != Phase.BATTLE && phase != Phase.ENDED) {
            throw new IllegalArgumentException("cannot restore phase " + phase);
        }
        World world = worldFor(snap.getMapId());
        List<Structure> structureList = new ArrayList<>();
        for (StructureState ss : snap.getStructuresList()) {
            double hp = ss.getHp();
            if (ss.getDestroyed()) hp = Math.max(hp, 1.0); // keep damage() able to destroy
            structureList.add(new Structure(ss.getId(), ss.getX(), ss.getY(),
                    ss.getWidth(), ss.getHeight(),
                    ProtoMapper.structureType(ss.getType()), hp));
        }
        HeadlessGame game = HeadlessGame.fromMap(world, snap.getBudget(), structureList);
        GameState st = game.state();
        applyArmyState(st.red, snap.getRed());
        applyArmyState(st.blue, snap.getBlue());
        st.rngSeed = snap.getRngSeed();
        st.nextUnitId = snap.getNextUnitId();
        st.tick = snap.getTick();
        st.winner = ProtoMapper.faction(snap.getWinner());
        for (int i = 0; i < snap.getStructuresCount(); i++) {
            StructureState ss = snap.getStructures(i);
            Structure s = structureList.get(i);
            if (ss.getDestroyed()) {
                st.structures.damage(s, Math.max(ss.getHp(), 1.0) + 1.0);
            }
            if (ss.getOpen()) {
                st.structures.setGateOpen(s, true);
            }
        }
        Map<Long, Unit> byId = new HashMap<>();
        for (UnitState us : snap.getUnitsList()) {
            UnitType type = ProtoMapper.unitType(us.getType());
            Faction faction = ProtoMapper.faction(us.getFaction());
            Unit u = new Unit(us.getId(), type, faction, us.getX(), us.getY(), 1.0, us.getVeteranRank());
            u.vx = us.getVx();
            u.vy = us.getVy();
            u.maxHp = us.getMaxHp();
            u.hp = us.getHp();
            u.attackCooldownRemaining = us.getAttackCooldown();
            u.routed = us.getRouted();
            u.garrisoned = us.getGarrisoned();
            u.burningSeconds = us.getBurningSeconds();
            u.burningDamagePerSec = us.getBurningDamagePerSec();
            boolean dead = us.getDead() || us.getHp() <= 0.0;
            u.state = dead
                    ? com.github.rzo1.bloodfields.model.UnitState.DEAD
                    : com.github.rzo1.bloodfields.model.UnitState.IDLE;
            st.armyOf(faction).add(u);
            byId.put(u.id, u);
        }
        for (UnitState us : snap.getUnitsList()) {
            if (us.getTargetId() == 0 && us.getBurningAttackerId() == 0) continue;
            Unit u = byId.get(us.getId());
            if (u == null) continue;
            u.target = byId.get(us.getTargetId());
            u.burningAttacker = byId.get(us.getBurningAttackerId());
        }
        for (ProjectileState ps : snap.getProjectilesList()) {
            UnitType attackerType = ProtoMapper.unitType(ps.getAttackerType());
            double splash = attackerType != null && attackerType.ranged()
                    ? attackerType.splashRadius() : 0.0;
            Projectile p = new Projectile(ps.getX(), ps.getY(), ps.getVx(), ps.getVy(),
                    ProtoMapper.faction(ps.getFaction()), ps.getDamage(), null, splash, attackerType);
            p.alive = ps.getAlive();
            st.projectiles.add(p);
        }
        for (CorpseInfo ci : snap.getCorpsesList()) {
            st.corpses.recordDeath(0L, ci.getX(), ci.getY(),
                    ProtoMapper.faction(ci.getFaction()), ProtoMapper.unitType(ci.getType()));
        }
        for (FireTile ft : snap.getFiresList()) {
            st.fireField.activeFires().put(FireField.key(ft.getCol(), ft.getRow()), ft.getRemainingSeconds());
        }
        for (Tile t : snap.getScorchedList()) {
            st.fireField.scorchedTiles().put(FireField.key(t.getCol(), t.getRow()), Boolean.TRUE);
        }
        switch (phase) {
            case BATTLE -> st.phase = GameState.Phase.BATTLE;
            case ENDED -> st.phase = GameState.Phase.RESULT;
            default -> st.phase = GameState.Phase.DEPLOYMENT; // DEPLOY
        }
        game.setSimTime(snap.getSimTime());
        return game;
    }

    static World worldFor(String mapId) {
        MapPreset preset = Maps.byId(mapId);
        int cols = (int) Math.ceil(HeadlessGame.WIDTH / HeadlessGame.TILE);
        int rows = (int) Math.ceil(HeadlessGame.HEIGHT / HeadlessGame.TILE);
        Terrain.TileType[][] tiles = preset == null ? null : preset.generator().generate(cols, rows);
        if (tiles == null) {
            return World.battlefield(HeadlessGame.WIDTH, HeadlessGame.HEIGHT, HeadlessGame.TILE);
        }
        return new World(HeadlessGame.WIDTH, HeadlessGame.HEIGHT, HeadlessGame.TILE, tiles);
    }

    private static ArmyState armyState(Army a) {
        return ArmyState.newBuilder()
                .setBudget(a.deploymentBudget())
                .setReserve(a.reserveBudget())
                .setHeroSkill(ProtoMapper.skillKind(a.heroSkill()))
                .setHpMultiplier(a.hpMultiplier())
                .build();
    }

    private static void applyArmyState(Army a, ArmyState s) {
        a.setDeploymentBudget(s.getBudget());
        a.setReserveBudget(s.getReserve());
        a.setHeroSkill(ProtoMapper.skill(s.getHeroSkill()));
        a.setHpMultiplier(s.getHpMultiplier());
    }

    private static UnitState unitState(Unit u) {
        boolean dead = u.state == com.github.rzo1.bloodfields.model.UnitState.DEAD || u.hp <= 0.0;
        return UnitState.newBuilder()
                .setId(u.id)
                .setType(ProtoMapper.unitKind(u.type))
                .setFaction(ProtoMapper.factionKind(u.faction))
                .setX(u.x)
                .setY(u.y)
                .setVx(u.vx)
                .setVy(u.vy)
                .setHp(u.hp)
                .setMaxHp(u.maxHp)
                .setRouted(u.routed)
                .setGarrisoned(u.garrisoned)
                .setVeteranRank(u.veteranRank)
                .setDead(dead)
                .setAttackCooldown(u.attackCooldownRemaining)
                .setBurningSeconds(u.burningSeconds)
                .setBurningDamagePerSec(u.burningDamagePerSec)
                .setBurningAttackerId(u.burningAttacker == null ? 0L : u.burningAttacker.id)
                .setTargetId(u.target == null ? 0L : u.target.id)
                .build();
    }

    private static ProjectileState projectileState(Projectile p) {
        return ProjectileState.newBuilder()
                .setX(p.x)
                .setY(p.y)
                .setVx(p.vx)
                .setVy(p.vy)
                .setDamage(p.damage)
                .setAttackerType(ProtoMapper.unitKind(p.attackerType))
                .setFaction(ProtoMapper.factionKind(p.owner))
                .setAlive(p.alive)
                .build();
    }
}
