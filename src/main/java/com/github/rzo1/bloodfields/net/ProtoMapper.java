package com.github.rzo1.bloodfields.net;

import com.github.rzo1.bloodfields.cli.HeadlessGame;
import com.github.rzo1.bloodfields.engine.FireField;
import com.github.rzo1.bloodfields.engine.Structure;
import com.github.rzo1.bloodfields.engine.StructureField;
import com.github.rzo1.bloodfields.engine.StructureType;
import com.github.rzo1.bloodfields.engine.World;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.HeroSkill;
import com.github.rzo1.bloodfields.model.Projectile;
import com.github.rzo1.bloodfields.model.Terrain;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitType;
import com.github.rzo1.bloodfields.net.proto.BattleState;
import com.github.rzo1.bloodfields.net.proto.CorpseInfo;
import com.github.rzo1.bloodfields.net.proto.DeployState;
import com.github.rzo1.bloodfields.net.proto.LobbyState;
import com.github.rzo1.bloodfields.net.proto.PlayerInfo;
import com.github.rzo1.bloodfields.net.proto.ProjectileInfo;
import com.github.rzo1.bloodfields.net.proto.Skill;
import com.github.rzo1.bloodfields.net.proto.StructureInfo;
import com.github.rzo1.bloodfields.net.proto.StructureKind;
import com.github.rzo1.bloodfields.net.proto.Tile;
import com.github.rzo1.bloodfields.net.proto.UnitInfo;
import com.github.rzo1.bloodfields.net.proto.UnitKind;
import com.github.rzo1.bloodfields.net.proto.ZoneRect;
import com.github.rzo1.bloodfields.telnet.BattleDriver;
import com.github.rzo1.bloodfields.ui.MapPreset;
import com.github.rzo1.bloodfields.ui.MapStructures;
import com.github.rzo1.bloodfields.ui.Maps;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Engine-to-proto mapping helpers: units, deploy/battle/lobby frames, terrain
 * rows, structures, and the enum conversions between model and proto types
 * (proto {@code *_UNSPECIFIED} maps to {@code null}).
 */
public final class ProtoMapper {

    private ProtoMapper() {}

    public static UnitType unitType(UnitKind k) {
        if (k == null || k == UnitKind.UNIT_UNSPECIFIED || k == UnitKind.UNRECOGNIZED) return null;
        return UnitType.valueOf(k.name());
    }

    public static UnitKind unitKind(UnitType t) {
        return t == null ? UnitKind.UNIT_UNSPECIFIED : UnitKind.valueOf(t.name());
    }

    public static HeroSkill skill(Skill s) {
        if (s == null || s == Skill.SKILL_UNSPECIFIED || s == Skill.UNRECOGNIZED) return null;
        return HeroSkill.valueOf(s.name());
    }

    public static Skill skillKind(HeroSkill s) {
        return s == null ? Skill.SKILL_UNSPECIFIED : Skill.valueOf(s.name());
    }

    public static Faction faction(com.github.rzo1.bloodfields.net.proto.Faction f) {
        if (f == com.github.rzo1.bloodfields.net.proto.Faction.RED) return Faction.RED;
        if (f == com.github.rzo1.bloodfields.net.proto.Faction.BLUE) return Faction.BLUE;
        return null;
    }

    public static com.github.rzo1.bloodfields.net.proto.Faction factionKind(Faction f) {
        if (f == null) return com.github.rzo1.bloodfields.net.proto.Faction.FACTION_UNSPECIFIED;
        return f == Faction.RED
                ? com.github.rzo1.bloodfields.net.proto.Faction.RED
                : com.github.rzo1.bloodfields.net.proto.Faction.BLUE;
    }

    public static StructureType structureType(StructureKind k) {
        if (k == null || k == StructureKind.STRUCTURE_UNSPECIFIED || k == StructureKind.UNRECOGNIZED) return null;
        return StructureType.valueOf(k.name());
    }

    public static StructureKind structureKind(StructureType t) {
        return t == null ? StructureKind.STRUCTURE_UNSPECIFIED : StructureKind.valueOf(t.name());
    }

    public static UnitInfo toUnitInfo(Unit u) {
        return UnitInfo.newBuilder()
                .setId(u.id)
                .setType(unitKind(u.type))
                .setFaction(factionKind(u.faction))
                .setX(u.x)
                .setY(u.y)
                .setHp(u.hp)
                .setMaxHp(u.maxHp)
                .setRouted(u.routed)
                .setGarrisoned(u.garrisoned)
                .setBurning(u.burningSeconds > 0.0)
                .setAlive(u.isAlive())
                .setVeteranRank(u.veteranRank)
                .build();
    }

    public static StructureInfo toStructureInfo(Structure s, StructureField field) {
        return StructureInfo.newBuilder()
                .setId(s.id())
                .setType(structureKind(s.type()))
                .setX(s.x())
                .setY(s.y())
                .setWidth(s.width())
                .setHeight(s.height())
                .setDestroyed(field != null && field.isDestroyed(s))
                .setOpen(field != null && field.isOpen(s))
                .build();
    }

    public static DeployState toDeployState(HeadlessGame game, boolean redReady, boolean blueReady) {
        DeployState.Builder b = DeployState.newBuilder()
                .setRedRemaining(game.state().red.remainingBudget())
                .setBlueRemaining(game.state().blue.remainingBudget())
                .setBudget(game.state().red.deploymentBudget())
                .setRedSkill(skillKind(game.state().red.heroSkill()))
                .setBlueSkill(skillKind(game.state().blue.heroSkill()))
                .setRedReady(redReady)
                .setBlueReady(blueReady)
                .setRedZone(zoneRect(game.zoneFor(Faction.RED)))
                .setBlueZone(zoneRect(game.zoneFor(Faction.BLUE)));
        for (Unit u : game.state().red.units()) b.addUnits(toUnitInfo(u));
        for (Unit u : game.state().blue.units()) b.addUnits(toUnitInfo(u));
        return b.build();
    }

    public static BattleState toBattleState(HeadlessGame game, BattleDriver driver, Faction decidedWinner) {
        BattleState.Builder b = BattleState.newBuilder()
                .setSimTime(game.simTime())
                .setRedAlive(game.aliveCount(Faction.RED))
                .setBlueAlive(game.aliveCount(Faction.BLUE))
                .setPaused(driver != null && driver.paused())
                .setSpeedIndex(driver != null ? driver.speedIndex() : 1)
                .setWinner(factionKind(decidedWinner != null ? decidedWinner : game.winner()))
                .setTick(game.state().tick);
        for (Unit u : game.state().red.units()) b.addUnits(toUnitInfo(u));
        for (Unit u : game.state().blue.units()) b.addUnits(toUnitInfo(u));
        for (Projectile p : game.state().projectiles) {
            b.addProjectiles(ProjectileInfo.newBuilder()
                    .setX(p.x)
                    .setY(p.y)
                    .setAttackerType(unitKind(p.attackerType))
                    .build());
        }
        for (com.github.rzo1.bloodfields.engine.Corpse c : game.state().corpses.corpses()) {
            b.addCorpses(CorpseInfo.newBuilder()
                    .setX(c.x())
                    .setY(c.y())
                    .setFaction(factionKind(c.faction()))
                    .setType(unitKind(c.type()))
                    .build());
        }
        FireField fire = game.state().fireField;
        if (fire != null) {
            for (Map.Entry<Long, Double> e : fire.activeFires().entrySet()) {
                if (e.getValue() != null && e.getValue() > 0.0) {
                    b.addFires(Tile.newBuilder()
                            .setCol(FireField.colOf(e.getKey()))
                            .setRow(FireField.rowOf(e.getKey()))
                            .build());
                }
            }
            for (Map.Entry<Long, Boolean> e : fire.scorchedTiles().entrySet()) {
                if (Boolean.TRUE.equals(e.getValue())) {
                    b.addScorched(Tile.newBuilder()
                            .setCol(FireField.colOf(e.getKey()))
                            .setRow(FireField.rowOf(e.getKey()))
                            .build());
                }
            }
        }
        for (Structure s : game.state().structures.structures()) {
            b.addStructures(toStructureInfo(s, game.state().structures));
        }
        return b.build();
    }

    public static LobbyState toLobbyState(MatchConfig cfg, List<PlayerInfo> players) {
        World world = SnapshotCodec.worldFor(cfg.mapId());
        MapPreset preset = Maps.byId(cfg.mapId());
        LobbyState.Builder b = LobbyState.newBuilder()
                .addAllPlayers(players)
                .setMapId(cfg.mapId())
                .setMapName(preset == null ? "" : preset.name())
                .setBudget(cfg.budget())
                .setDragons(cfg.dragons())
                .addAllTerrainRows(terrainRows(world));
        long[] idSeed = {-1L};
        for (Structure s : MapStructures.forPreset(cfg.mapId(),
                HeadlessGame.WIDTH, HeadlessGame.HEIGHT, () -> idSeed[0]--)) {
            b.addStructures(toStructureInfo(s, null));
        }
        return b.build();
    }

    /** 25 rows of 40 chars ('.' grass, '*' forest, '#' river), sampled at tile centers. */
    public static List<String> terrainRows(World world) {
        List<String> rows = new ArrayList<>();
        int cols = (int) (world.width / world.tileSize);
        int rowCount = (int) (world.height / world.tileSize);
        for (int r = 0; r < rowCount; r++) {
            StringBuilder sb = new StringBuilder(cols);
            for (int c = 0; c < cols; c++) {
                Terrain.TileType t = world.tileAt(c * world.tileSize + world.tileSize / 2.0,
                        r * world.tileSize + world.tileSize / 2.0);
                sb.append(t == Terrain.TileType.FOREST ? '*' : t == Terrain.TileType.RIVER ? '#' : '.');
            }
            rows.add(sb.toString());
        }
        return rows;
    }

    private static ZoneRect zoneRect(HeadlessGame.ZoneRect z) {
        return ZoneRect.newBuilder()
                .setMinX(z.minX)
                .setMinY(z.minY)
                .setMaxX(z.maxX)
                .setMaxY(z.maxY)
                .build();
    }
}
