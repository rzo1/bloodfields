package com.github.rzo1.bloodfields.ui;

import com.github.rzo1.bloodfields.model.Faction;
import javafx.scene.paint.Color;

/**
 * Centralized dark/gore palette + CSS snippets for Bloodfield's UI.
 * Apply by attaching {@link #STYLESHEET} to the Scene's stylesheets and adding
 * {@link #BUTTON_CLASS} (or {@link #PANEL_CLASS} etc.) to the relevant nodes,
 * or use the inline-style helpers for one-off labels.
 *
 * <p>Faction accessors ({@link #factionFill(Faction)}, {@link #factionStroke(Faction)})
 * resolve through {@link AccessibilitySettings} on each call, so a runtime
 * toggle of the colour-blind palette takes effect immediately — do NOT cache
 * the returned Color in a static field.
 */
public final class Theme {

    // Default (lore) faction palette.
    private static final Color RED_FILL_DEFAULT = Color.web("#8a1a14");
    private static final Color BLUE_FILL_DEFAULT = Color.web("#1a3d7a");
    private static final Color RED_STROKE_DEFAULT = Color.web("#3d0907");
    private static final Color BLUE_STROKE_DEFAULT = Color.web("#0a1f44");

    // Colour-blind-safe palette: orange/cyan is the canonical deutan+protan-safe pair
    // (cf. Wong 2011 "Points of view: Color blindness", and the IBM Carbon CB palette).
    private static final Color RED_FILL_CB = Color.web("#e08000");
    private static final Color BLUE_FILL_CB = Color.web("#0098c8");
    private static final Color RED_STROKE_CB = Color.web("#6b3a00");
    private static final Color BLUE_STROKE_CB = Color.web("#004a64");

    // Hero/elite ring — gold, visible in either palette. Drawn as a shape cue
    // so a player can identify their hero by ring, not just by faction colour.
    private static final Color HERO_RING_COLOR = Color.web("#e8c84a");

    public static final String PANEL_BG = "rgba(20, 18, 16, 0.92)";
    public static final String BUTTON_BG = "linear-gradient(to bottom, #2a2018, #1a1310)";
    public static final String BUTTON_HOVER = "linear-gradient(to bottom, #3a2820, #2a1815)";
    public static final String BUTTON_PRESSED = "linear-gradient(to bottom, #1a1310, #0a0807)";
    public static final String BUTTON_BORDER = "#5a1a14";
    public static final String TEXT_PRIMARY = "#e8d8c8";
    public static final String TEXT_DIM = "#7a6c5c";
    public static final String TEXT_ACCENT = "#c8332e";
    public static final String TEXT_WARN = "#ffa040";
    public static final String DIVIDER = "#3a2520";
    public static final String SHADOW = "rgba(0,0,0,0.6)";

    public static final String ROOT_BG = "linear-gradient(to bottom, #100806, #1f100c)";

    public static final String STYLESHEET = "/bloodfield.css";

    public static final String BUTTON_CLASS = "gore-button";
    public static final String PANEL_CLASS = "gore-panel";
    public static final String TITLE_CLASS = "gore-title";
    public static final String BODY_CLASS = "gore-body";
    public static final String DIM_CLASS = "gore-dim";
    public static final String ACCENT_CLASS = "gore-accent";

    private Theme() {}

    /** Live faction fill — branches on the current colour-blind palette toggle. */
    public static Color factionFill(Faction faction) {
        boolean cb = AccessibilitySettings.get().colourBlindPalette();
        if (faction == Faction.RED) return cb ? RED_FILL_CB : RED_FILL_DEFAULT;
        return cb ? BLUE_FILL_CB : BLUE_FILL_DEFAULT;
    }

    /** Live faction outline — branches on the current colour-blind palette toggle. */
    public static Color factionStroke(Faction faction) {
        boolean cb = AccessibilitySettings.get().colourBlindPalette();
        if (faction == Faction.RED) return cb ? RED_STROKE_CB : RED_STROKE_DEFAULT;
        return cb ? BLUE_STROKE_CB : BLUE_STROKE_DEFAULT;
    }

    /**
     * Gold ring colour used as a shape cue around heroes/elites. Independent
     * of palette so the hero is identifiable regardless of colour-blindness.
     */
    public static Color heroRing() {
        return HERO_RING_COLOR;
    }

    public static String buttonStyle() {
        return "-fx-background-color: " + BUTTON_BG + ";"
                + "-fx-text-fill: " + TEXT_PRIMARY + ";"
                + "-fx-border-color: " + BUTTON_BORDER + ";"
                + "-fx-border-width: 1px;"
                + "-fx-border-radius: 2px;"
                + "-fx-background-radius: 2px;"
                + "-fx-padding: 6 12 6 12;"
                + "-fx-font-family: 'Georgia', 'Serif';"
                + "-fx-font-weight: bold;"
                + "-fx-cursor: hand;";
    }

    public static String panelStyle() {
        return "-fx-background-color: " + PANEL_BG + ";"
                + "-fx-border-color: " + BUTTON_BORDER + ";"
                + "-fx-border-width: 0 1 0 0;";
    }

    public static String labelTitleStyle() {
        return "-fx-text-fill: " + TEXT_PRIMARY + ";"
                + "-fx-font-family: 'Georgia', 'Serif';"
                + "-fx-font-size: 16px;"
                + "-fx-font-weight: bold;"
                + "-fx-effect: dropshadow(gaussian, " + SHADOW + ", 4, 0.4, 1, 1);";
    }

    public static String labelBodyStyle() {
        return "-fx-text-fill: " + TEXT_PRIMARY + ";"
                + "-fx-font-family: 'Georgia', 'Serif';"
                + "-fx-font-size: 13px;";
    }

    public static String labelDimStyle() {
        return "-fx-text-fill: " + TEXT_DIM + ";"
                + "-fx-font-family: 'Georgia', 'Serif';"
                + "-fx-font-size: 12px;"
                + "-fx-font-style: italic;";
    }

    public static String labelAccentStyle() {
        return "-fx-text-fill: " + TEXT_ACCENT + ";"
                + "-fx-font-family: 'Georgia', 'Serif';"
                + "-fx-font-weight: bold;";
    }

    public static String labelWarnStyle() {
        return "-fx-text-fill: " + TEXT_WARN + ";"
                + "-fx-font-family: 'Georgia', 'Serif';"
                + "-fx-font-size: 12px;"
                + "-fx-font-style: italic;";
    }

    public static String menuTitleStyle() {
        return "-fx-text-fill: " + TEXT_PRIMARY + ";"
                + "-fx-font-family: 'Georgia', 'Serif';"
                + "-fx-effect: dropshadow(gaussian, " + TEXT_ACCENT + ", 12, 0.6, 0, 0);";
    }

    public static String dividerStyle() {
        return "-fx-background-color: " + DIVIDER + ";";
    }

    public static String rootBackgroundStyle() {
        return "-fx-background-color: " + ROOT_BG + ";";
    }

    public static String optInWrapStyle() {
        return "-fx-background-color: linear-gradient(to bottom, #0a0604, #1a0e0a);";
    }

    public static String actionRowStyle() {
        return "-fx-background-color: rgba(0,0,0,0.55);"
                + "-fx-border-color: " + BUTTON_BORDER + " transparent transparent transparent;"
                + "-fx-border-width: 1 0 0 0;";
    }

    public static String cardStyle(boolean selected) {
        String border = selected ? TEXT_ACCENT : DIVIDER;
        return "-fx-border-color: " + border + ";"
                + "-fx-border-width: 2;"
                + "-fx-background-color: rgba(20, 14, 12, 0.85);"
                + "-fx-padding: 4;";
    }

    /** Apply Theme stylesheet + gore-button class to a Scene+root recursively. Idempotent. */
    public static void applyStylesheet(javafx.scene.Scene scene) {
        if (scene == null) return;
        String url = Theme.class.getResource(STYLESHEET) != null
                ? Theme.class.getResource(STYLESHEET).toExternalForm()
                : null;
        if (url != null && !scene.getStylesheets().contains(url)) {
            scene.getStylesheets().add(url);
        }
    }
}
