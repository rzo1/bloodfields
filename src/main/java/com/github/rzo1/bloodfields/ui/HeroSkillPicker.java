package com.github.rzo1.bloodfields.ui;

import com.github.rzo1.bloodfields.model.HeroSkill;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

public final class HeroSkillPicker extends VBox {

    private static final String NO_SELECTION_HINT = "Pick a hero skill to see what it does.";

    private final Map<HeroSkill, ToggleButton> buttons = new EnumMap<>(HeroSkill.class);
    private final ToggleGroup group = new ToggleGroup();
    private final Label title = new Label("Hero Skill");
    private final Label descriptionLabel = new Label(NO_SELECTION_HINT);
    private final Consumer<HeroSkill> onPicked;

    public HeroSkillPicker(Consumer<HeroSkill> onPicked) {
        super(4);
        this.onPicked = onPicked;
        setPadding(new Insets(4, 0, 4, 0));
        title.setStyle(Theme.labelAccentStyle());
        descriptionLabel.setStyle(Theme.labelDimStyle());
        descriptionLabel.setWrapText(true);
        descriptionLabel.setMaxWidth(200);
        getChildren().add(title);
        for (HeroSkill skill : HeroSkill.values()) {
            ToggleButton tb = new ToggleButton(skill.displayName());
            tb.setMaxWidth(Double.MAX_VALUE);
            tb.setToggleGroup(group);
            tb.setTooltip(new Tooltip(skill.description()));
            tb.setOnAction(e -> {
                if (tb.isSelected()) {
                    descriptionLabel.setText(skill.description());
                    fire(skill);
                } else {
                    descriptionLabel.setText(NO_SELECTION_HINT);
                    fire(null);
                }
            });
            buttons.put(skill, tb);
            getChildren().add(tb);
        }
        getChildren().add(descriptionLabel);
    }

    public void setSelected(HeroSkill skill) {
        for (Map.Entry<HeroSkill, ToggleButton> e : buttons.entrySet()) {
            e.getValue().setSelected(e.getKey() == skill);
        }
        descriptionLabel.setText(skill == null ? NO_SELECTION_HINT : skill.description());
    }

    public HeroSkill selected() {
        for (Map.Entry<HeroSkill, ToggleButton> e : buttons.entrySet()) {
            if (e.getValue().isSelected()) return e.getKey();
        }
        return null;
    }

    public ToggleButton buttonFor(HeroSkill skill) {
        return buttons.get(skill);
    }

    public Label descriptionLabel() {
        return descriptionLabel;
    }

    private void fire(HeroSkill skill) {
        if (onPicked != null) onPicked.accept(skill);
    }
}
