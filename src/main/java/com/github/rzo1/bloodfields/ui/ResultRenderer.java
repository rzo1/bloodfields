package com.github.rzo1.bloodfields.ui;

import com.github.rzo1.bloodfields.model.Achievement;
import com.github.rzo1.bloodfields.model.Faction;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.util.Collections;
import java.util.List;

public final class ResultRenderer {

    private static final Color RED_COLOR = Color.web(Theme.TEXT_ACCENT);
    private static final Color BLUE_COLOR = Color.web("#2f5fb8");
    private static final Color SHADOW = Color.rgb(0, 0, 0, 0.7);
    private static final Color PANEL = Color.rgb(20, 14, 12, 0.82);
    private static final Color BORDER = Color.web(Theme.BUTTON_BORDER);
    private static final Color WHITE = Color.web(Theme.TEXT_PRIMARY);
    private static final Color HINT = Color.web(Theme.TEXT_DIM);
    private static final Color CODE = Color.web(Theme.TEXT_WARN);
    private static final Color GOLD = Color.web("#e8c870");
    private static final double ACHIEVEMENT_LINE_HEIGHT = 22.0;
    private static final double ACHIEVEMENT_HEADER_GAP = 30.0;
    private static final String DEFAULT_HINT = "click or SPACE to play again";

    public void render(GraphicsContext g, double w, double h, Faction winner,
                       int redKilled, int redTotal, int blueKilled, int blueTotal) {
        render(g, w, h, winner, redKilled, redTotal, blueKilled, blueTotal, null,
                Collections.emptyList(), DEFAULT_HINT);
    }

    public void render(GraphicsContext g, double w, double h, Faction winner,
                       int redKilled, int redTotal, int blueKilled, int blueTotal,
                       String nextLevelCode) {
        render(g, w, h, winner, redKilled, redTotal, blueKilled, blueTotal, nextLevelCode,
                Collections.emptyList(), DEFAULT_HINT);
    }

    public void render(GraphicsContext g, double w, double h, Faction winner,
                       int redKilled, int redTotal, int blueKilled, int blueTotal,
                       String nextLevelCode, List<Achievement> newlyUnlocked) {
        render(g, w, h, winner, redKilled, redTotal, blueKilled, blueTotal, nextLevelCode,
                newlyUnlocked, DEFAULT_HINT);
    }

    public void render(GraphicsContext g, double w, double h, Faction winner,
                       int redKilled, int redTotal, int blueKilled, int blueTotal,
                       String nextLevelCode, List<Achievement> newlyUnlocked,
                       String hintText) {
        String headline;
        Color headlineColor;
        if (winner == Faction.RED) {
            headline = "RED VICTORY";
            headlineColor = RED_COLOR;
        } else if (winner == Faction.BLUE) {
            headline = "BLUE VICTORY";
            headlineColor = BLUE_COLOR;
        } else {
            headline = "DRAW";
            headlineColor = WHITE;
        }

        double cx = w / 2.0;
        double cy = h / 2.0;
        boolean showCode = nextLevelCode != null && !nextLevelCode.isEmpty();
        List<Achievement> achievements = newlyUnlocked == null ? Collections.emptyList() : newlyUnlocked;
        boolean showAchievements = !achievements.isEmpty();
        double achievementsHeight = showAchievements
                ? ACHIEVEMENT_HEADER_GAP + achievements.size() * ACHIEVEMENT_LINE_HEIGHT + 8
                : 0;
        double basePanelHeight = showCode ? 260 : 220;
        double panelHeight = basePanelHeight + achievementsHeight;

        g.save();
        g.setFill(PANEL);
        g.fillRect(0, cy - 110, w, panelHeight);
        g.setStroke(BORDER);
        g.setLineWidth(1.5);
        g.strokeLine(0, cy - 110, w, cy - 110);
        g.strokeLine(0, cy - 110 + panelHeight, w, cy - 110 + panelHeight);

        g.setTextAlign(TextAlignment.CENTER);

        Font banner = Font.font("Georgia", FontWeight.BOLD, 56);
        g.setFont(banner);
        g.setFill(SHADOW);
        g.fillText(headline, cx + 3, cy - 30 + 3);
        g.setFill(headlineColor);
        g.fillText(headline, cx, cy - 30);

        Font stats = Font.font("Georgia", 18);
        g.setFont(stats);
        g.setFill(WHITE);
        String redLine = "RED  killed: " + redKilled + " / " + redTotal;
        String blueLine = "BLUE  killed: " + blueKilled + " / " + blueTotal;
        g.fillText(redLine + "        " + blueLine, cx, cy + 18);

        double cursorY = cy + 18;
        if (showCode) {
            g.setFont(Font.font("Georgia", FontWeight.BOLD, 18));
            g.setFill(CODE);
            cursorY = cy + 50;
            g.fillText("Code for next level: " + nextLevelCode, cx, cursorY);
        }

        if (showAchievements) {
            double headerY = cursorY + ACHIEVEMENT_HEADER_GAP;
            g.setFont(Font.font("Georgia", FontWeight.BOLD, 16));
            g.setFill(GOLD);
            g.fillText("Achievements unlocked", cx, headerY);
            g.setFont(Font.font("Georgia", 13));
            for (int i = 0; i < achievements.size(); i++) {
                Achievement a = achievements.get(i);
                double y = headerY + (i + 1) * ACHIEVEMENT_LINE_HEIGHT;
                g.setFill(GOLD);
                g.fillText(a.displayName() + " — " + a.description(), cx, y);
            }
            cursorY = headerY + achievements.size() * ACHIEVEMENT_LINE_HEIGHT;
        }

        g.setFont(Font.font("Georgia", 14));
        g.setFill(HINT);
        double hintY = showAchievements
                ? cursorY + 28
                : cy + (showCode ? 90 : 70);
        String hint = (hintText == null || hintText.isEmpty()) ? DEFAULT_HINT : hintText;
        g.fillText(hint, cx, hintY);

        g.restore();
    }
}
