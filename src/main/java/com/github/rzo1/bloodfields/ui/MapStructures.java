package com.github.rzo1.bloodfields.ui;

import com.github.rzo1.bloodfields.engine.Structure;
import com.github.rzo1.bloodfields.engine.StructureType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class MapStructures {

    private MapStructures() {}

    public static List<Structure> forPreset(String mapId, double width, double height,
                                            Supplier<Long> idSource) {
        List<Structure> out = new ArrayList<>();
        if (mapId == null) return out;
        if (mapId.equals("fortress_wall")) {
            populateFortressWall(out, width, height, idSource);
        }
        return out;
    }

    private static void populateFortressWall(List<Structure> out, double width, double height,
                                             Supplier<Long> idSource) {
        double midY = height / 2.0;
        double wallH = 32.0;
        double towerW = 64.0;
        double centerGateW = 96.0;
        double flankGateW = 64.0;
        double y = midY - wallH / 2.0;

        double leftTowerX = 0.0;
        double rightTowerX = width - towerW;
        double centerGateX = (width - centerGateW) / 2.0;
        double flankGateX = leftTowerX + towerW + (centerGateX - leftTowerX - towerW - flankGateW) / 2.0;

        out.add(new Structure(idSource.get(), leftTowerX, y, towerW, wallH, StructureType.TOWER,
                StructureType.TOWER.maxHp()));
        out.add(new Structure(idSource.get(), rightTowerX, y, towerW, wallH, StructureType.TOWER,
                StructureType.TOWER.maxHp()));

        addContinuousWall(out, leftTowerX + towerW, flankGateX, y, wallH, idSource);
        out.add(new Structure(idSource.get(), flankGateX, y, flankGateW, wallH, StructureType.GATE,
                StructureType.GATE.maxHp()));

        addContinuousWall(out, flankGateX + flankGateW, centerGateX, y, wallH, idSource);
        out.add(new Structure(idSource.get(), centerGateX, y, centerGateW, wallH, StructureType.GATE,
                StructureType.GATE.maxHp()));

        addContinuousWall(out, centerGateX + centerGateW, rightTowerX, y, wallH, idSource);
    }

    private static void addContinuousWall(List<Structure> out, double startX, double endX,
                                          double y, double wallH, Supplier<Long> idSource) {
        double span = endX - startX;
        if (span <= 0.0) return;
        double segW = 64.0;
        int segments = (int) Math.ceil(span / segW);
        if (segments <= 0) return;
        double actualSegW = span / segments;
        for (int i = 0; i < segments; i++) {
            double x = startX + i * actualSegW;
            out.add(new Structure(idSource.get(), x, y, actualSegW, wallH, StructureType.WALL,
                    StructureType.WALL.maxHp()));
        }
    }

}
