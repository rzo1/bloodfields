package com.github.rzo1.bloodfields.ui;

/**
 * Centralized dark/gore palette + CSS snippets for Bloodfield's UI.
 * Apply by attaching {@link #STYLESHEET} to the Scene's stylesheets and adding
 * {@link #BUTTON_CLASS} (or {@link #PANEL_CLASS} etc.) to the relevant nodes,
 * or use the inline-style helpers for one-off labels.
 */
public final class Theme {

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
