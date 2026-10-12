package com.github.rzo1.bloodfields.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.rzo1.bloodfields.cli.HeadlessGame;
import com.github.rzo1.bloodfields.engine.Structure;
import com.github.rzo1.bloodfields.engine.World;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.HeroSkill;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitType;
import com.github.rzo1.bloodfields.net.proto.BattleState;
import com.github.rzo1.bloodfields.net.proto.DeployState;
import com.github.rzo1.bloodfields.net.proto.LobbyState;
import com.github.rzo1.bloodfields.net.proto.Skill;
import com.github.rzo1.bloodfields.net.proto.StructureKind;
import com.github.rzo1.bloodfields.net.proto.UnitInfo;
import com.github.rzo1.bloodfields.net.proto.UnitKind;
import com.github.rzo1.bloodfields.ui.MapStructures;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Supplier;

class ProtoMapperTest {

    private static HeadlessGame grassGame() {
        return HeadlessGame.fromMap(
                World.grass(HeadlessGame.WIDTH, HeadlessGame.HEIGHT, HeadlessGame.TILE),
                1000, List.of());
    }

    private static Supplier<Long> ids() {
        long[] seed = {-1L};
        return () -> seed[0]--;
    }

    @Test
    void enumMappingsRoundTrip() {
        assertNull(ProtoMapper.unitType(UnitKind.UNIT_UNSPECIFIED));
        assertEquals(UnitType.DRAGON, ProtoMapper.unitType(UnitKind.DRAGON));
        assertEquals(UnitKind.UNIT_UNSPECIFIED, ProtoMapper.unitKind(null));
        assertEquals(UnitKind.PIKEMAN, ProtoMapper.unitKind(UnitType.PIKEMAN));
        assertNull(ProtoMapper.skill(Skill.SKILL_UNSPECIFIED));
        assertEquals(HeroSkill.SWIFT_FEET, ProtoMapper.skill(Skill.SWIFT_FEET));
        assertEquals(Skill.SKILL_UNSPECIFIED, ProtoMapper.skillKind(null));
        assertEquals(Skill.VAMPIRIC_BANNER, ProtoMapper.skillKind(HeroSkill.VAMPIRIC_BANNER));
        assertNull(ProtoMapper.faction(com.github.rzo1.bloodfields.net.proto.Faction.FACTION_UNSPECIFIED));
        assertEquals(Faction.BLUE, ProtoMapper.faction(com.github.rzo1.bloodfields.net.proto.Faction.BLUE));
        assertEquals(com.github.rzo1.bloodfields.net.proto.Faction.FACTION_UNSPECIFIED,
                ProtoMapper.factionKind(null));
        assertEquals(com.github.rzo1.bloodfields.net.proto.Faction.RED,
                ProtoMapper.factionKind(Faction.RED));
        assertNull(ProtoMapper.structureType(StructureKind.STRUCTURE_UNSPECIFIED));
        assertEquals(com.github.rzo1.bloodfields.engine.StructureType.TOWER,
                ProtoMapper.structureType(StructureKind.TOWER));
        assertEquals(StructureKind.STRUCTURE_UNSPECIFIED, ProtoMapper.structureKind(null));
        assertEquals(StructureKind.GATE, ProtoMapper.structureKind(
                com.github.rzo1.bloodfields.engine.StructureType.GATE));
    }

    @Test
    void unitInfoCarriesMutableFields() {
        Unit u = new Unit(7, UnitType.MAGE, Faction.BLUE, 100, 200, 1.5, 2);
        u.hp = 31.5;
        u.burningSeconds = 3.0;
        u.routed = true;
        UnitInfo info = ProtoMapper.toUnitInfo(u);
        assertEquals(7, info.getId());
        assertEquals(UnitKind.MAGE, info.getType());
        assertEquals(com.github.rzo1.bloodfields.net.proto.Faction.BLUE, info.getFaction());
        assertEquals(31.5, info.getHp(), 1e-9);
        assertEquals(u.maxHp, info.getMaxHp(), 1e-9);
        assertTrue(info.getBurning());
        assertTrue(info.getAlive());
        assertTrue(info.getRouted());
        assertEquals(2, info.getVeteranRank());
    }

    @Test
    void terrainRowsAreFortyCharsPerRow() {
        List<String> plains = ProtoMapper.terrainRows(SnapshotCodec.worldFor("plains"));
        assertEquals(25, plains.size());
        for (String row : plains) {
            assertEquals(40, row.length());
            assertTrue(row.chars().allMatch(c -> c == '.'));
        }
        List<String> bridge = ProtoMapper.terrainRows(SnapshotCodec.worldFor("bridge"));
        assertTrue(bridge.stream().anyMatch(r -> r.chars().anyMatch(c -> c == '#')));
    }

    @Test
    void lobbyStateCarriesTerrainAndStructurePreview() {
        MatchConfig cfg = new MatchConfig("fortress_wall", 750, true);
        LobbyState lobby = ProtoMapper.toLobbyState(cfg, List.of());
        assertEquals("fortress_wall", lobby.getMapId());
        assertEquals(750, lobby.getBudget());
        assertTrue(lobby.getDragons());
        assertEquals(25, lobby.getTerrainRowsCount());
        assertFalse(lobby.getStructuresList().isEmpty());
        assertFalse(lobby.getStructures(0).getDestroyed());
        assertFalse(lobby.getStructures(0).getOpen());
    }

    @Test
    void deployStateCarriesBudgetsSkillsReadinessAndZones() {
        HeadlessGame game = grassGame();
        game.place(Faction.RED, UnitType.INFANTRY, 600, 700);
        game.place(Faction.BLUE, UnitType.ARCHER, 600, 100);
        game.state().red.setHeroSkill(HeroSkill.BATTLE_LUST);
        DeployState d = ProtoMapper.toDeployState(game, true, false);
        assertEquals(2, d.getUnitsCount());
        assertEquals(990, d.getRedRemaining());
        assertEquals(10000 - 15, d.getBlueRemaining());
        assertEquals(1000, d.getBudget());
        assertEquals(Skill.BATTLE_LUST, d.getRedSkill());
        assertEquals(Skill.SKILL_UNSPECIFIED, d.getBlueSkill());
        assertTrue(d.getRedReady());
        assertFalse(d.getBlueReady());
        assertEquals(400.0, d.getRedZone().getMinY(), 1e-9);
        assertEquals(400.0, d.getBlueZone().getMaxY(), 1e-9);
    }

    @Test
    void battleStateCarriesCountsAndDriverFlags() {
        HeadlessGame game = grassGame();
        game.place(Faction.RED, UnitType.INFANTRY, 600, 700);
        game.enemy().add(new Unit(game.state().nextUnitId++, UnitType.INFANTRY,
                Faction.BLUE, 640, 100, 1.0));
        game.start();
        game.step(1.0 / 60.0);
        BattleState b = ProtoMapper.toBattleState(game, null, null);
        assertEquals(1, b.getRedAlive());
        assertEquals(1, b.getBlueAlive());
        assertFalse(b.getPaused());
        assertEquals(1, b.getSpeedIndex());
        assertEquals(1, b.getTick());
        assertEquals(com.github.rzo1.bloodfields.net.proto.Faction.FACTION_UNSPECIFIED, b.getWinner());
        assertEquals(game.simTime(), b.getSimTime(), 1e-9);
    }

    @Test
    void structureInfoReflectsFieldState() {
        HeadlessGame game = HeadlessGame.fromMap(SnapshotCodec.worldFor("fortress_wall"), 1000,
                MapStructures.forPreset("fortress_wall", HeadlessGame.WIDTH, HeadlessGame.HEIGHT, ids()));
        Structure gate = null;
        for (Structure s : game.state().structures.structures()) {
            if (s.type().name().equals("GATE")) {
                gate = s;
                break;
            }
        }
        assertTrue(gate != null);
        game.state().structures.setGateOpen(gate, true);
        var info = ProtoMapper.toStructureInfo(gate, game.state().structures);
        assertTrue(info.getOpen());
        assertFalse(info.getDestroyed());
        assertEquals(StructureKind.GATE, info.getType());
        assertEquals(gate.id(), info.getId());
    }
}
