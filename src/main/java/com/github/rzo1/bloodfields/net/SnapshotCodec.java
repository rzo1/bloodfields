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
import com.github.rzo1.bloodfields.ui.Maps;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Full match-state snapshots: {@link #capture} serializes a {@link HeadlessGame}
 * (plus the match config and phase) into an opaque, versioned {@link Snapshot};
 * {@link #restore} rebuilds a fresh {@link HeadlessGame} from one. Bytes are
 * produced on SaveGame and accepted back on RestoreGame; version 2 only
 * (restores are additionally HMAC-verified by the match layer, and every
 * field is validated here before it can touch the engine — a snapshot is
 * untrusted input even when a familiar face carries it).
 */
public final class SnapshotCodec {

    public static final int VERSION = 2;

    static final int MAX_UNITS = 4096;
    static final int MAX_PROJECTILES = 8192;
    static final int MAX_CORPSES = 8192;
    static final int MAX_STRUCTURES = 512;
    static final long MAX_POKE_DRAWS = 100_000_000L;

    private SnapshotCodec() {}

    public static Snapshot capture(HeadlessGame game, MatchConfig cfg, Phase phase) {
        return capture(game, cfg, phase, List.of());
    }

    public static Snapshot capture(HeadlessGame game, MatchConfig cfg, Phase phase,
                                   List<String> playerNames) {
        return capture(game, cfg, phase, playerNames, null);
    }

    /**
     * @param decidedWinner winner already decided outside the game state
     *                      (e.g. by resignation, which leaves both armies
     *                      alive); recorded for ENDED snapshots
     */
    public static Snapshot capture(HeadlessGame game, MatchConfig cfg, Phase phase,
                                   List<String> playerNames,
                                   com.github.rzo1.bloodfields.model.Faction decidedWinner) {
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
                .setWinner(ProtoMapper.factionKind(captureWinner(game, phase, decidedWinner)))
                .setRed(armyState(st.red))
                .setBlue(armyState(st.blue))
                .setPokeRngDraws(pokeDraws(game));
        for (com.github.rzo1.bloodfields.model.Faction f
                : com.github.rzo1.bloodfields.model.Faction.values()) {
            var sum = st.battleStats.summary();
            b.addStats(com.github.rzo1.bloodfields.net.proto.FactionStats.newBuilder()
                    .setFaction(ProtoMapper.factionKind(f))
                    .setKills(sum.kills(f))
                    .setDeaths(sum.deaths(f))
                    .setDamageDealt(sum.damageDealt(f))
                    .setDamageReceived(sum.damageReceived(f))
                    .setBiggestHit(sum.biggestHit(f))
                    .setLastFallTick(sum.lastFallTick(f))
                    .addAllCreditedKillIds(st.battleStats.creditedKillIds())
                    .build());
        }
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
        if (snap == null || snap.getVersion() != VERSION) {
            throw new IllegalArgumentException("unsupported snapshot version: "
                    + (snap == null ? "null" : snap.getVersion()));
        }
        Phase phase = snap.getPhase();
        if (phase != Phase.DEPLOY && phase != Phase.BATTLE && phase != Phase.ENDED) {
            throw new IllegalArgumentException("cannot restore phase " + phase);
        }
        validate(snap);
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
            if (dead) {
                u.state = com.github.rzo1.bloodfields.model.UnitState.DEAD;
            } else if (snap.getVersion() >= 2 && us.getModelState() > 0
                    && us.getModelState() < com.github.rzo1.bloodfields.model.UnitState.values().length) {
                u.state = com.github.rzo1.bloodfields.model.UnitState.values()[us.getModelState()];
            } else {
                u.state = com.github.rzo1.bloodfields.model.UnitState.IDLE;
            }
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
            double useSplash = ps.getSplashRadius() > 0.0 ? ps.getSplashRadius() : splash;
            Projectile p = new Projectile(ps.getX(), ps.getY(), ps.getVx(), ps.getVy(),
                    ProtoMapper.faction(ps.getFaction()), ps.getDamage(),
                    byId.get(ps.getTargetId()), useSplash, attackerType,
                    byId.get(ps.getOriginatorId()));
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
        for (var fs : snap.getStatsList()) {
            com.github.rzo1.bloodfields.model.Faction f = ProtoMapper.faction(fs.getFaction());
            if (f != null) {
                st.battleStats.restoreFaction(f, fs.getKills(), fs.getDeaths(),
                        fs.getDamageDealt(), fs.getDamageReceived(), fs.getBiggestHit(),
                        fs.getLastFallTick(), fs.getCreditedKillIdsList());
            }
        }
        if (game.loop().updater() instanceof com.github.rzo1.bloodfields.ai.UnitAI ai) {
            ai.fastForwardPokeRng(st, snap.getPokeRngDraws());
        }
        switch (phase) {
            case BATTLE -> st.phase = GameState.Phase.BATTLE;
            case ENDED -> st.phase = GameState.Phase.RESULT;
            default -> st.phase = GameState.Phase.DEPLOYMENT; // DEPLOY
        }
        game.setSimTime(snap.getSimTime());
        return game;
    }

    private static com.github.rzo1.bloodfields.model.Faction captureWinner(
            HeadlessGame game, Phase phase,
            com.github.rzo1.bloodfields.model.Faction decidedWinner) {
        // In DEPLOY, game.winner() merely means "only one side has placed
        // units so far" — recording it would crown a false king.
        return switch (phase) {
            case DEPLOY -> null;
            case BATTLE -> game.state().winner;
            case ENDED -> decidedWinner != null ? decidedWinner
                    : game.state().winner != null ? game.state().winner : game.winner();
            default -> null;
        };
    }

    private static long pokeDraws(HeadlessGame game) {
        return game.loop().updater() instanceof com.github.rzo1.bloodfields.ai.UnitAI ai
                ? ai.pokeDraws() : 0;
    }

    /**
     * Rejects any snapshot that could wound the engine: non-finite or
     * off-world doubles (one NaN unit froze a match thread inside the
     * spatial grid), absurd counts, duplicate or non-positive ids,
     * hp above max, unknown maps/types, negative time. Called by
     * {@link #restore}; the match layer verifies the HMAC seal first.
     */
    public static void validate(Snapshot snap) {
        if (snap == null) throw new IllegalArgumentException("snapshot is null");
        if (Maps.byId(snap.getMapId()) == null) {
            throw new IllegalArgumentException("unknown map: " + snap.getMapId());
        }
        if (snap.getBudget() < MatchConfig.MIN_BUDGET || snap.getBudget() > MatchConfig.MAX_BUDGET) {
            throw new IllegalArgumentException("budget out of range: " + snap.getBudget());
        }
        finite(snap.getSimTime(), "sim_time");
        if (snap.getSimTime() < 0.0 || snap.getSimTime() > 1.0e7) {
            throw new IllegalArgumentException("sim_time out of range: " + snap.getSimTime());
        }
        if (snap.getTick() < 0) throw new IllegalArgumentException("negative tick");
        if (snap.getPokeRngDraws() < 0 || snap.getPokeRngDraws() > MAX_POKE_DRAWS) {
            throw new IllegalArgumentException("poke_rng_draws out of range");
        }
        count(snap.getUnitsCount(), MAX_UNITS, "units");
        count(snap.getProjectilesCount(), MAX_PROJECTILES, "projectiles");
        count(snap.getCorpsesCount(), MAX_CORPSES, "corpses");
        count(snap.getStructuresCount(), MAX_STRUCTURES, "structures");
        count(snap.getFiresCount(), 40 * 25, "fires");
        count(snap.getScorchedCount(), 40 * 25, "scorched");
        java.util.Set<Long> ids = new java.util.HashSet<>();
        long maxId = 0;
        for (UnitState us : snap.getUnitsList()) {
            if (us.getId() <= 0 || !ids.add(us.getId())) {
                throw new IllegalArgumentException("duplicate or non-positive unit id: " + us.getId());
            }
            maxId = Math.max(maxId, us.getId());
            if (ProtoMapper.unitType(us.getType()) == null) {
                throw new IllegalArgumentException("unknown unit type");
            }
            if (ProtoMapper.faction(us.getFaction()) == null) {
                throw new IllegalArgumentException("unknown unit faction");
            }
            pos(us.getX(), us.getY());
            vel(us.getVx()); vel(us.getVy());
            finite(us.getHp(), "hp"); finite(us.getMaxHp(), "max_hp");
            if (us.getMaxHp() <= 0.0 || us.getMaxHp() > 1.0e6) {
                throw new IllegalArgumentException("max_hp out of range: " + us.getMaxHp());
            }
            if (us.getHp() > us.getMaxHp()) {
                throw new IllegalArgumentException("hp above max_hp");
            }
            finite(us.getAttackCooldown(), "attack_cooldown");
            finite(us.getBurningSeconds(), "burning_seconds");
            finite(us.getBurningDamagePerSec(), "burning_damage_per_sec");
            if (us.getVeteranRank() < 0 || us.getVeteranRank() > 100) {
                throw new IllegalArgumentException("veteran_rank out of range");
            }
        }
        if (snap.getNextUnitId() <= maxId) {
            throw new IllegalArgumentException("next_unit_id must exceed every unit id");
        }
        for (ProjectileState ps : snap.getProjectilesList()) {
            pos(ps.getX(), ps.getY()); vel(ps.getVx()); vel(ps.getVy());
            finite(ps.getDamage(), "damage"); finite(ps.getSplashRadius(), "splash_radius");
        }
        for (CorpseInfo ci : snap.getCorpsesList()) pos(ci.getX(), ci.getY());
        for (FireTile ft : snap.getFiresList()) {
            tile(ft.getCol(), ft.getRow());
            finite(ft.getRemainingSeconds(), "remaining_seconds");
            if (ft.getRemainingSeconds() < 0.0 || ft.getRemainingSeconds() > 3600.0) {
                throw new IllegalArgumentException("remaining_seconds out of range");
            }
        }
        for (Tile t : snap.getScorchedList()) tile(t.getCol(), t.getRow());
        for (StructureState ss : snap.getStructuresList()) {
            pos(ss.getX(), ss.getY());
            finite(ss.getWidth(), "width"); finite(ss.getHeight(), "height");
            finite(ss.getHp(), "structure hp");
            if (ss.getWidth() <= 0.0 || ss.getHeight() <= 0.0
                    || ss.getWidth() > HeadlessGame.WIDTH || ss.getHeight() > HeadlessGame.HEIGHT) {
                throw new IllegalArgumentException("structure size out of range");
            }
            if (ProtoMapper.structureType(ss.getType()) == null) {
                throw new IllegalArgumentException("unknown structure type");
            }
        }
    }

    private static void finite(double v, String field) {
        if (!Double.isFinite(v)) throw new IllegalArgumentException(field + " not finite: " + v);
    }

    private static void pos(double x, double y) {
        finite(x, "x"); finite(y, "y");
        if (x < -64.0 || x > HeadlessGame.WIDTH + 64.0 || y < -64.0 || y > HeadlessGame.HEIGHT + 64.0) {
            throw new IllegalArgumentException("position off world: " + x + "," + y);
        }
    }

    private static void vel(double v) {
        finite(v, "velocity");
        if (Math.abs(v) > 10_000.0) throw new IllegalArgumentException("velocity out of range: " + v);
    }

    private static void tile(int col, int row) {
        if (col < 0 || col >= 40 || row < 0 || row >= 25) {
            throw new IllegalArgumentException("tile off world: " + col + "," + row);
        }
    }

    private static void count(int n, int max, String what) {
        if (n > max) throw new IllegalArgumentException("too many " + what + ": " + n);
    }

    static World worldFor(String mapId) {
        return HeadlessGame.buildWorld(mapId);
    }

    private static ArmyState armyState(Army a) {
        ArmyState.Builder b = ArmyState.newBuilder()
                .setBudget(a.deploymentBudget())
                .setReserve(a.reserveBudget())
                .setHeroSkill(ProtoMapper.skillKind(a.heroSkill()))
                .setHpMultiplier(a.hpMultiplier());
        for (Map.Entry<UnitType, Integer> e : a.caps().entrySet()) {
            b.putCaps(ProtoMapper.unitKind(e.getKey()).getNumber(), e.getValue());
        }
        return b.build();
    }

    private static void applyArmyState(Army a, ArmyState s) {
        a.setDeploymentBudget(s.getBudget());
        a.setReserveBudget(s.getReserve());
        a.setHeroSkill(ProtoMapper.skill(s.getHeroSkill()));
        a.setHpMultiplier(s.getHpMultiplier());
        for (Map.Entry<Integer, Integer> e : s.getCapsMap().entrySet()) {
            UnitType t = ProtoMapper.unitType(
                    com.github.rzo1.bloodfields.net.proto.UnitKind.forNumber(e.getKey()));
            if (t != null) a.setCap(t, e.getValue());
        }
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
                .setModelState(u.state == null ? 0 : u.state.ordinal())
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
                .setTargetId(p.target == null ? 0L : p.target.id)
                .setOriginatorId(p.originator == null ? 0L : p.originator.id)
                .setSplashRadius(p.splashRadius)
                .build();
    }
}
