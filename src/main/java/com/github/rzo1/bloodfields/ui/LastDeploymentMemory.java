package com.github.rzo1.bloodfields.ui;

import com.github.rzo1.bloodfields.model.Army;
import com.github.rzo1.bloodfields.model.HeroSkill;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LastDeploymentMemory {

    public static final class Slot {
        public final UnitType type;
        public final double x;
        public final double y;

        public Slot(UnitType type, double x, double y) {
            this.type = type;
            this.x = x;
            this.y = y;
        }
    }

    private List<Slot> slots;
    private HeroSkill heroSkill;

    public void snapshot(Army army) {
        if (army == null || army.units().isEmpty()) {
            return;
        }
        List<Slot> snap = new ArrayList<>(army.units().size());
        for (Unit u : army.units()) {
            snap.add(new Slot(u.type, u.x, u.y));
        }
        this.slots = snap;
        this.heroSkill = army.heroSkill();
    }

    public boolean hasSnapshot() {
        return slots != null && !slots.isEmpty();
    }

    public List<Slot> slots() {
        return slots == null ? Collections.emptyList() : Collections.unmodifiableList(slots);
    }

    public HeroSkill heroSkill() {
        return heroSkill;
    }

    public void clear() {
        slots = null;
        heroSkill = null;
    }
}
