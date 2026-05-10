package com.github.rzo1.armyclash.ui;

import com.github.rzo1.armyclash.engine.Structure;
import com.github.rzo1.armyclash.engine.StructureField;
import com.github.rzo1.armyclash.engine.StructureType;
import com.github.rzo1.armyclash.model.Army;
import com.github.rzo1.armyclash.model.Unit;
import com.github.rzo1.armyclash.model.UnitType;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class Hud extends BorderPane {

    private final Army playerArmy;
    private final DeploymentController controller;
    private final Runnable onStartBattle;
    private Runnable onRestoreLast;
    private LastDeploymentMemory deploymentMemory;

    private static final String NO_SELECTION_HINT = "Select a unit type to see its ability.";
    private static final String SIEGE_WARNING_TEXT =
            "⚔ Bring siege (Archer / Mage / Catapult) to break walls.";

    private final Label levelLabel = new Label();
    private final Label budgetLabel = new Label();
    private final Label casualtyLabel = new Label("Casualties: 0 / 0");
    private final Label descriptionLabel = new Label(NO_SELECTION_HINT);
    private final Label siegeWarning = new Label(SIEGE_WARNING_TEXT);
    private final Label speedLabel = new Label("");
    private final Map<UnitType, ToggleButton> unitToggles = new EnumMap<>(UnitType.class);
    private final Button startButton = new Button("Start Battle");
    private final Label title = new Label("DEPLOYMENT");
    private final VBox roster = new VBox(4);
    private final Button autoFill = new Button("Auto-fill");
    private final Button restoreLast = new Button("Same as last");
    private final Label rosterLabel = new Label("Roster");
    private final Separator sep1 = new Separator();
    private final Separator sep2 = new Separator();
    private final Separator sep3 = new Separator();
    private final Separator sepHero = new Separator();
    private final HeroSkillPicker heroPicker;
    private boolean battleMode;

    public Hud(Army playerArmy, DeploymentController controller, Runnable onStartBattle) {
        this(playerArmy, controller, onStartBattle, null);
    }

    public Hud(Army playerArmy, DeploymentController controller, Runnable onStartBattle,
               Set<UnitType> allowedTypes) {
        this.playerArmy = playerArmy;
        this.controller = controller;
        this.onStartBattle = onStartBattle;

        VBox panel = new VBox(8);
        panel.setPadding(new Insets(12));
        panel.setPrefWidth(220);
        panel.setStyle(Theme.panelStyle());
        panel.setAlignment(Pos.TOP_LEFT);

        title.setStyle(Theme.labelTitleStyle() + " -fx-font-size: 16px;");

        levelLabel.setStyle(Theme.labelAccentStyle() + " -fx-font-size: 13px;");
        levelLabel.setVisible(false);
        levelLabel.setManaged(false);
        budgetLabel.setStyle(Theme.labelBodyStyle());
        rosterLabel.setStyle(Theme.labelBodyStyle() + " -fx-font-weight: bold;");
        descriptionLabel.setStyle(Theme.labelDimStyle());
        descriptionLabel.setWrapText(true);
        descriptionLabel.setMaxWidth(200);

        siegeWarning.setStyle(Theme.labelWarnStyle());
        siegeWarning.setWrapText(true);
        siegeWarning.setMaxWidth(200);
        siegeWarning.setVisible(false);
        siegeWarning.setManaged(false);

        ToggleGroup group = new ToggleGroup();
        Set<UnitType> allowed = allowedTypes != null
                ? allowedTypes
                : defaultAllowedTypes();
        for (UnitType t : UnitType.values()) {
            if (!allowed.contains(t)) continue;
            ToggleButton tb = new ToggleButton(t.displayName() + " — " + t.cost() + " pt");
            tb.setMaxWidth(Double.MAX_VALUE);
            tb.setToggleGroup(group);
            tb.setOnAction(e -> {
                if (tb.isSelected()) {
                    controller.setSelectedType(t);
                    descriptionLabel.setText(descriptionFor(t));
                } else {
                    controller.setSelectedType(null);
                    descriptionLabel.setText(NO_SELECTION_HINT);
                }
            });
            unitToggles.put(t, tb);
            roster.getChildren().add(tb);
        }

        autoFill.setMaxWidth(Double.MAX_VALUE);
        autoFill.getStyleClass().add(Theme.BUTTON_CLASS);
        autoFill.setOnAction(e -> autoFill());

        restoreLast.setMaxWidth(Double.MAX_VALUE);
        restoreLast.setDisable(true);
        restoreLast.getStyleClass().add(Theme.BUTTON_CLASS);
        restoreLast.setOnAction(e -> {
            if (onRestoreLast != null) {
                onRestoreLast.run();
                refresh();
            }
        });

        heroPicker = new HeroSkillPicker(skill -> {
            playerArmy.setHeroSkill(skill);
        });
        heroPicker.setVisible(false);
        heroPicker.setManaged(false);
        sepHero.setVisible(false);
        sepHero.setManaged(false);

        startButton.setMaxWidth(Double.MAX_VALUE);
        startButton.getStyleClass().add(Theme.BUTTON_CLASS);
        startButton.setStyle("-fx-font-size: 14px;");
        startButton.setOnAction(e -> {
            if (onStartBattle != null && controller.canStart()) {
                onStartBattle.run();
            }
        });

        casualtyLabel.setStyle(Theme.labelBodyStyle());

        speedLabel.setStyle("-fx-text-fill: " + Theme.TEXT_ACCENT + ";"
                + " -fx-font-family: 'Monospaced'; -fx-font-size: 12px; -fx-font-weight: bold;");
        speedLabel.setMaxWidth(Double.MAX_VALUE);
        speedLabel.setAlignment(Pos.CENTER_RIGHT);
        speedLabel.setVisible(false);
        speedLabel.setManaged(false);

        panel.getChildren().addAll(
                title,
                levelLabel,
                budgetLabel,
                sep1,
                rosterLabel,
                roster,
                descriptionLabel,
                siegeWarning,
                autoFill,
                restoreLast,
                sepHero,
                heroPicker,
                sep2,
                startButton,
                sep3,
                casualtyLabel,
                speedLabel
        );

        setLeft(panel);

        controller.setOnArmyChanged(this::refresh);
        refresh();
    }

    public void update(int redCasualties, int blueCasualties) {
        casualtyLabel.setText("Casualties: " + redCasualties + " / " + blueCasualties);
    }

    public void setLevelInfo(int number, int total, String name) {
        levelLabel.setText("Level " + number + "/" + total + ": " + name);
        levelLabel.setVisible(true);
        levelLabel.setManaged(true);
    }

    public void setPlayerLabel(String text) {
        levelLabel.setText(text);
        levelLabel.setVisible(true);
        levelLabel.setManaged(true);
    }

    public void setTitleText(String text) {
        title.setText(text);
    }

    public void setStartButtonText(String text) {
        startButton.setText(text);
    }

    public static Set<UnitType> defaultAllowedTypes() {
        EnumSet<UnitType> s = EnumSet.noneOf(UnitType.class);
        for (UnitType t : UnitType.values()) {
            if (t.playerSelectable()) s.add(t);
        }
        return s;
    }

    public void enterBattleMode() {
        battleMode = true;
        title.setText("BATTLE");
        budgetLabel.setVisible(false);
        budgetLabel.setManaged(false);
        rosterLabel.setVisible(false);
        rosterLabel.setManaged(false);
        roster.setVisible(false);
        roster.setManaged(false);
        autoFill.setVisible(false);
        autoFill.setManaged(false);
        restoreLast.setVisible(false);
        restoreLast.setManaged(false);
        startButton.setVisible(false);
        startButton.setManaged(false);
        sep1.setVisible(false);
        sep1.setManaged(false);
        sep2.setVisible(false);
        sep2.setManaged(false);
        descriptionLabel.setVisible(false);
        descriptionLabel.setManaged(false);
        siegeWarning.setVisible(false);
        siegeWarning.setManaged(false);
        heroPicker.setVisible(false);
        heroPicker.setManaged(false);
        sepHero.setVisible(false);
        sepHero.setManaged(false);
        speedLabel.setVisible(true);
        speedLabel.setManaged(true);
    }

    public void setSpeedLabel(String text) {
        speedLabel.setText(text == null ? "" : text);
    }

    public void setRestoreLast(LastDeploymentMemory memory, Runnable onRestoreLast) {
        this.deploymentMemory = memory;
        this.onRestoreLast = onRestoreLast;
        refresh();
    }

    public Button restoreLastButton() {
        return restoreLast;
    }

    public Label speedLabel() {
        return speedLabel;
    }

    public Label descriptionLabel() {
        return descriptionLabel;
    }

    private static String descriptionFor(UnitType t) {
        if (t == null) return NO_SELECTION_HINT;
        String d = t.description();
        return (d == null || d.isEmpty()) ? t.displayName() : d;
    }

    private static String toggleTextFor(UnitType t, int count, int cap) {
        String base = t.displayName() + " — " + t.cost() + " pt";
        if (cap == Integer.MAX_VALUE) return base;
        return base + " (" + count + "/" + cap + ")";
    }

    public void refresh() {
        if (battleMode) {
            return;
        }
        budgetLabel.setText("Points: " + playerArmy.spentPoints() + "/" + playerArmy.deploymentBudget());
        int remaining = playerArmy.remainingBudget();
        for (Map.Entry<UnitType, ToggleButton> e : unitToggles.entrySet()) {
            UnitType t = e.getKey();
            boolean affordable = t.cost() <= remaining;
            boolean uniqueExhausted = t.unique() && controller.hasUnit(t);
            int cap = playerArmy.capFor(t);
            int count = playerArmy.countOf(t);
            boolean capExhausted = count >= cap;
            boolean enabled = affordable && !uniqueExhausted && !capExhausted;
            ToggleButton tb = e.getValue();
            tb.setText(toggleTextFor(t, count, cap));
            tb.setDisable(!enabled);
            if (!enabled && tb.isSelected()) {
                tb.setSelected(false);
                if (controller.getSelectedType() == t) {
                    controller.setSelectedType(null);
                }
            }
        }
        boolean showWarning = mapHasStructures() && armyHasNoRanged();
        siegeWarning.setVisible(showWarning);
        siegeWarning.setManaged(showWarning);

        boolean canRestore = deploymentMemory != null
                && deploymentMemory.hasSnapshot()
                && onRestoreLast != null
                && playerArmy.units().isEmpty();
        restoreLast.setDisable(!canRestore);

        boolean hasGeneral = armyHasGeneral();
        heroPicker.setVisible(hasGeneral);
        heroPicker.setManaged(hasGeneral);
        sepHero.setVisible(hasGeneral);
        sepHero.setManaged(hasGeneral);
        if (!hasGeneral && playerArmy.heroSkill() != null) {
            playerArmy.setHeroSkill(null);
            heroPicker.setSelected(null);
        } else if (hasGeneral && heroPicker.selected() != playerArmy.heroSkill()) {
            heroPicker.setSelected(playerArmy.heroSkill());
        }
        startButton.setDisable(!controller.canStart());
    }

    private boolean armyHasGeneral() {
        for (Unit u : playerArmy.units()) {
            if (u.type == UnitType.GENERAL) return true;
        }
        return false;
    }

    private boolean armyHasNoRanged() {
        for (Unit u : playerArmy.units()) {
            if (u.type != null && u.type.ranged()) return false;
        }
        return true;
    }

    private boolean mapHasStructures() {
        StructureField field = controller.structures();
        return field != null && !field.structures().isEmpty();
    }

    public Label siegeWarning() {
        return siegeWarning;
    }

    public HeroSkillPicker heroPicker() {
        return heroPicker;
    }

    private void autoFill() {
        DeploymentZone zone = controller.zone();
        double tile = controller.tileSize();
        UnitType prev = controller.getSelectedType();

        garrisonRangedIntoStructures(zone);

        UnitType filler = UnitType.INFANTRY;
        if (playerArmy.countOf(filler) >= playerArmy.capFor(filler)) {
            controller.setSelectedType(prev);
            return;
        }
        double startX = Math.floor(zone.minX / tile) * tile + tile / 2.0;
        if (startX < zone.minX) {
            startX += tile;
        }
        double startY = Math.floor(zone.minY / tile) * tile + tile / 2.0;
        if (startY < zone.minY) {
            startY += tile;
        }

        boolean placed = true;
        controller.setSelectedType(filler);
        outer:
        while (placed && playerArmy.remainingBudget() >= filler.cost()) {
            placed = false;
            for (double y = startY; y <= zone.maxY; y += tile) {
                for (double x = startX; x <= zone.maxX; x += tile) {
                    if (playerArmy.remainingBudget() < filler.cost()) {
                        break outer;
                    }
                    if (playerArmy.countOf(filler) >= playerArmy.capFor(filler)) {
                        break outer;
                    }
                    if (occupied(x, y)) {
                        continue;
                    }
                    if (controller.tryPlace(x, y)) {
                        placed = true;
                    }
                }
            }
            if (!placed) {
                break;
            }
        }
        controller.setSelectedType(prev);
    }

    private void garrisonRangedIntoStructures(DeploymentZone zone) {
        StructureField field = controller.structures();
        if (field == null) return;
        UnitType ranged = UnitType.ARCHER;
        if (playerArmy.remainingBudget() < ranged.cost()) return;
        for (Structure s : field.structures()) {
            if (s == null) continue;
            if (s.type() == StructureType.GATE) continue;
            double cx = s.x() + s.width() / 2.0;
            double cy = s.y() + s.height() / 2.0;
            if (!zone.contains(cx, cy)) continue;
            int cap = StructureField.garrisonCapacity(s.type());
            int taken = field.garrisonOf(s).size();
            for (int i = taken; i < cap; i++) {
                if (playerArmy.remainingBudget() < ranged.cost()) return;
                if (playerArmy.countOf(ranged) >= playerArmy.capFor(ranged)) return;
                if (!controller.tryGarrison(s, ranged)) break;
            }
        }
    }

    private boolean occupied(double x, double y) {
        for (Unit u : playerArmy.units()) {
            if (Math.abs(u.x - x) < 0.5 && Math.abs(u.y - y) < 0.5) {
                return true;
            }
        }
        return false;
    }
}
