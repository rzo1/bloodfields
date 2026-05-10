package com.github.rzo1.bloodfields.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class Achievements {

    public static final String FIRST_BLOOD = "first_blood";
    public static final String FLAWLESS = "flawless";
    public static final String NIGHTMARE_CLEAR = "nightmare_clear";
    public static final String DRAGON_SLAYER_GRUNTS = "dragon_slayer_grunts";
    public static final String SIEGE_MASTER = "siege_master";
    public static final String CORPSE_PILE = "corpse_pile";
    public static final String CROW_FEAST = "crow_feast";
    public static final String BONE_YARD = "bone_yard";
    public static final String GENERAL_ALIVE = "general_alive";
    public static final String SPEED_RUNNER = "speed_runner";

    private static final Map<String, Achievement> CATALOG = new LinkedHashMap<>();
    private static final Map<String, Predicate<BattleSummary>> PREDICATES = new LinkedHashMap<>();

    static {
        register(new Achievement(FIRST_BLOOD,
                        "First Blood",
                        "Win any campaign level."),
                s -> s.mode() == BattleSummary.Mode.CAMPAIGN && s.playerWon());

        register(new Achievement(FLAWLESS,
                        "Flawless",
                        "Win Final Fuckery without losing any unit."),
                s -> s.mode() == BattleSummary.Mode.CAMPAIGN
                        && s.levelNumber() == 10
                        && s.playerWon()
                        && s.playerCasualties() == 0);

        register(new Achievement(NIGHTMARE_CLEAR,
                        "Nightmare Clear",
                        "Win Simon's Nightmare on any setting."),
                s -> s.mode() == BattleSummary.Mode.CAMPAIGN
                        && s.levelNumber() == 11
                        && s.playerWon());

        register(new Achievement(DRAGON_SLAYER_GRUNTS,
                        "Dragon Slayer (Grunts)",
                        "Kill a Dragon using only INFANTRY, PIKEMAN, or CAVALRY."),
                s -> s.dragonsKilledByPlayer() >= 1
                        && hasOnlyTypes(s, UnitType.INFANTRY, UnitType.PIKEMAN, UnitType.CAVALRY));

        register(new Achievement(SIEGE_MASTER,
                        "Siege Master",
                        "Win a campaign level using only Catapults."),
                s -> s.mode() == BattleSummary.Mode.CAMPAIGN
                        && s.playerWon()
                        && hasOnlyTypes(s, UnitType.CATAPULT));

        register(new Achievement(CORPSE_PILE,
                        "Corpse Pile",
                        "Have 100+ corpses on screen at once during a battle."),
                s -> s.peakCorpsesOnScreen() >= 100);

        register(new Achievement(CROW_FEAST,
                        "Crow Feast",
                        "Witness the vulture descend on a death pile."),
                s -> s.peakDeathsInOneTile() >= 12);

        register(new Achievement(BONE_YARD,
                        "Bone Yard",
                        "Trigger a skull pile (16+ deaths in one tile)."),
                s -> s.peakDeathsInOneTile() >= 16);

        register(new Achievement(GENERAL_ALIVE,
                        "Long Live the General",
                        "Finish a campaign level with the General still alive."),
                s -> s.mode() == BattleSummary.Mode.CAMPAIGN
                        && s.playerWon()
                        && s.playerGeneralAlive()
                        && s.playerArmyComposition().getOrDefault(UnitType.GENERAL, 0) > 0);

        register(new Achievement(SPEED_RUNNER,
                        "Speed Runner",
                        "Win Final Fuckery in under 2 minutes (sim time)."),
                s -> s.mode() == BattleSummary.Mode.CAMPAIGN
                        && s.levelNumber() == 10
                        && s.playerWon()
                        && s.simSeconds() < 120.0);
    }

    private Achievements() {}

    private static void register(Achievement a, Predicate<BattleSummary> predicate) {
        CATALOG.put(a.id(), a);
        PREDICATES.put(a.id(), predicate);
    }

    public static List<Achievement> all() {
        return List.copyOf(CATALOG.values());
    }

    public static Achievement byId(String id) {
        return CATALOG.get(id);
    }

    public static Map<String, Predicate<BattleSummary>> predicates() {
        return Map.copyOf(PREDICATES);
    }

    private static boolean hasOnlyTypes(BattleSummary s, UnitType... allowed) {
        Map<UnitType, Integer> comp = s.playerArmyComposition();
        if (comp.isEmpty()) return false;
        for (UnitType t : comp.keySet()) {
            boolean ok = false;
            for (UnitType a : allowed) {
                if (a == t) { ok = true; break; }
            }
            if (!ok) return false;
        }
        return true;
    }
}
