package com.github.rzo1.armyclash.ai;

import com.github.rzo1.armyclash.engine.GameState;
import com.github.rzo1.armyclash.engine.World;
import com.github.rzo1.armyclash.model.Army;
import com.github.rzo1.armyclash.model.HeroSkill;
import com.github.rzo1.armyclash.model.Unit;
import com.github.rzo1.armyclash.model.UnitType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class SkirmishBot {

    private static final long DEFAULT_SEED = 0xB07_F1A75L;
    private static final double FRONT_ROW_Y = 180.0;
    private static final double MID_ROW_Y = 120.0;
    private static final double BACK_ROW_Y = 60.0;
    private static final double UNIT_SPACING = 50.0;
    private static final int DRAGON_BUDGET_THRESHOLD = 250;

    private final Random random;

    public SkirmishBot() {
        this(new Random(DEFAULT_SEED));
    }

    public SkirmishBot(Random random) {
        this.random = random == null ? new Random(DEFAULT_SEED) : random;
    }

    public void deployArmy(Army botArmy, GameState state, int width, int height,
                           int budget, boolean dragonsAllowed) {
        if (botArmy == null || state == null || budget <= 0) {
            return;
        }
        Composition comp = compose(budget, dragonsAllowed);
        placeUnits(botArmy, state, width, height, comp);
        assignHeroSkillIfGeneralPresent(botArmy);
    }

    private void assignHeroSkillIfGeneralPresent(Army botArmy) {
        boolean hasGeneral = false;
        for (Unit u : botArmy.units()) {
            if (u.type == UnitType.GENERAL) {
                hasGeneral = true;
                break;
            }
        }
        if (!hasGeneral) return;
        HeroSkill[] skills = HeroSkill.values();
        if (skills.length == 0) return;
        botArmy.setHeroSkill(skills[random.nextInt(skills.length)]);
    }

    private Composition compose(int budget, boolean dragonsAllowed) {
        List<UnitType> picks = new ArrayList<>();
        int spent = 0;

        UnitType dragon = findType("DRAGON");
        boolean addDragon = dragonsAllowed && dragon != null
                && budget >= DRAGON_BUDGET_THRESHOLD
                && dragon.cost() <= budget;
        if (addDragon) {
            picks.add(dragon);
            spent += dragon.cost();
        }

        UnitType general = findSelectable("GENERAL");
        if (general != null && spent + general.cost() <= budget) {
            picks.add(general);
            spent += general.cost();
        }

        Type[] mix = {
                new Type(findSelectable("INFANTRY"), 0.32),
                new Type(findSelectable("PIKEMAN"), 0.27),
                new Type(findSelectable("ARCHER"), 0.16),
                new Type(findSelectable("MAGE"), 0.10),
                new Type(findSelectable("CAVALRY"), 0.10),
                new Type(findSelectable("HEALER"), 0.05),
        };

        List<Type> available = new ArrayList<>();
        double weightSum = 0.0;
        for (Type t : mix) {
            if (t.type != null) {
                available.add(t);
                weightSum += t.weight;
            }
        }
        if (available.isEmpty()) {
            for (UnitType ut : UnitType.values()) {
                if (ut.playerSelectable() && ut != general) {
                    available.add(new Type(ut, 1.0));
                    weightSum += 1.0;
                }
            }
        }

        int safety = 0;
        while (spent < budget && !available.isEmpty() && safety < 10000) {
            safety++;
            UnitType chosen = pickWeighted(available, weightSum);
            if (chosen == null) break;
            if (spent + chosen.cost() > budget) {
                Type cheapest = null;
                for (Type t : available) {
                    if (cheapest == null || t.type.cost() < cheapest.type.cost()) {
                        cheapest = t;
                    }
                }
                if (cheapest == null || spent + cheapest.type.cost() > budget) {
                    break;
                }
                chosen = cheapest.type;
            }
            picks.add(chosen);
            spent += chosen.cost();
        }
        return new Composition(picks);
    }

    private UnitType pickWeighted(List<Type> options, double weightSum) {
        if (options.isEmpty()) return null;
        double r = random.nextDouble() * weightSum;
        double cum = 0.0;
        for (Type t : options) {
            cum += t.weight;
            if (r <= cum) return t.type;
        }
        return options.get(options.size() - 1).type;
    }

    private void placeUnits(Army botArmy, GameState state, int width, int height,
                            Composition comp) {
        List<UnitType> front = new ArrayList<>();
        List<UnitType> mid = new ArrayList<>();
        List<UnitType> back = new ArrayList<>();
        for (UnitType t : comp.units) {
            if (rowFor(t) == 0) front.add(t);
            else if (rowFor(t) == 1) mid.add(t);
            else back.add(t);
        }
        double maxY = height / 2.0 - 10.0;
        placeRow(botArmy, state, width, Math.min(FRONT_ROW_Y, maxY), front);
        placeRow(botArmy, state, width, Math.min(MID_ROW_Y, maxY), mid);
        placeRow(botArmy, state, width, Math.min(BACK_ROW_Y, maxY), back);
    }

    private void placeRow(Army botArmy, GameState state, int width, double y,
                          List<UnitType> row) {
        if (row.isEmpty()) return;
        double cx = width / 2.0;
        int n = row.size();
        for (int i = 0; i < n; i++) {
            double x = cx + (i - (n - 1) / 2.0) * UNIT_SPACING;
            place(botArmy, state, row.get(i), x, y);
        }
    }

    private void place(Army botArmy, GameState state, UnitType type, double x, double y) {
        double rx = x;
        double ry = y;
        World world = state.world;
        if (world != null && !world.passableAt(x, y)) {
            int col = (int) (x / world.tileSize);
            int row = (int) (y / world.tileSize);
            if (col < 0) col = 0;
            if (row < 0) row = 0;
            if (col >= world.cols()) col = world.cols() - 1;
            if (row >= world.rows()) row = world.rows() - 1;
            int[] nearest = Pathfinding.findNearestPassable(world, col, row);
            if (nearest != null) {
                rx = (nearest[0] + 0.5) * world.tileSize;
                ry = (nearest[1] + 0.5) * world.tileSize;
            }
        }
        botArmy.add(new Unit(state.nextUnitId++, type, botArmy.faction(), rx, ry,
                botArmy.hpMultiplier()));
    }

    private static int rowFor(UnitType t) {
        if (t.ranged()) return 2;
        if (t.speed() >= 70.0) return 0;
        return 0;
    }

    private static UnitType findSelectable(String name) {
        UnitType t = findType(name);
        return (t != null && t.playerSelectable()) ? t : null;
    }

    private static UnitType findType(String name) {
        for (UnitType t : UnitType.values()) {
            if (t.name().equals(name)) return t;
        }
        return null;
    }

    private static final class Type {
        final UnitType type;
        final double weight;
        Type(UnitType type, double weight) {
            this.type = type;
            this.weight = weight;
        }
    }

    private static final class Composition {
        final List<UnitType> units;
        Composition(List<UnitType> units) {
            this.units = units;
        }
    }
}
