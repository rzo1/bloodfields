package com.github.rzo1.bloodfields.ui;

import com.github.rzo1.bloodfields.engine.BattleStats;
import com.github.rzo1.bloodfields.model.Faction;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

/**
 * Renders the post-battle stats panel: winner banner, time-to-victory,
 * per-faction kills/deaths/damage/biggest-hit/last-to-fall. Two columns
 * (RED / BLUE) on a centred panel. Matches ResultRenderer's typography.
 */
public final class StatsScreen {

    private static final Color SHADOW = Color.rgb(0, 0, 0, 0.7);
    private static final Color PANEL = Color.rgb(20, 14, 12, 0.88);
    private static final Color BORDER = Color.web(Theme.BUTTON_BORDER);
    private static final Color WHITE = Color.web(Theme.TEXT_PRIMARY);
    private static final Color DIM = Color.web(Theme.TEXT_DIM);
    private static final Color HINT = Color.web(Theme.TEXT_DIM);

    private static final double LINE_HEIGHT = 22.0;
    private static final double PANEL_WIDTH = 560.0;
    private static final double PANEL_PADDING = 24.0;

    private static final String[] ROW_LABELS = {
            "Kills",
            "Deaths",
            "Damage dealt",
            "Biggest hit",
            "Last alive (s)"
    };

    public void render(GraphicsContext g, double w, double h,
                       Faction winner, long totalTicks, double secondsPerTick,
                       BattleStats.Summary summary) {
        if (summary == null) return;

        // Resolve faction colours per-render so the colour-blind toggle takes
        // effect without restarting the result screen.
        Color redColor = Theme.factionFill(Faction.RED);
        Color blueColor = Theme.factionFill(Faction.BLUE);

        String headline;
        Color headlineColor;
        if (winner == Faction.RED) {
            headline = "RED VICTORY";
            headlineColor = redColor;
        } else if (winner == Faction.BLUE) {
            headline = "BLUE VICTORY";
            headlineColor = blueColor;
        } else {
            headline = "DRAW";
            headlineColor = WHITE;
        }

        double rows = ROW_LABELS.length;
        double panelHeight = 70.0 + 36.0 + rows * LINE_HEIGHT + 40.0;
        double panelX = (w - PANEL_WIDTH) / 2.0;
        double panelY = (h - panelHeight) / 2.0;

        g.save();
        g.setFill(PANEL);
        g.fillRect(panelX, panelY, PANEL_WIDTH, panelHeight);
        g.setStroke(BORDER);
        g.setLineWidth(1.5);
        g.strokeRect(panelX, panelY, PANEL_WIDTH, panelHeight);

        double cx = w / 2.0;

        Font banner = Font.font("Georgia", FontWeight.BOLD, 36);
        g.setFont(banner);
        g.setTextAlign(TextAlignment.CENTER);
        g.setFill(SHADOW);
        g.fillText(headline, cx + 2, panelY + 44 + 2);
        g.setFill(headlineColor);
        g.fillText(headline, cx, panelY + 44);

        Font sub = Font.font("Georgia", 14);
        g.setFont(sub);
        g.setFill(DIM);
        String time = String.format("%d ticks (%.1fs)", totalTicks,
                Math.max(0.0, totalTicks * secondsPerTick));
        g.fillText("Battle length: " + time, cx, panelY + 64);

        double headerY = panelY + 96;
        Font colHeader = Font.font("Georgia", FontWeight.BOLD, 16);
        g.setFont(colHeader);
        double leftCol = panelX + PANEL_WIDTH * 0.32;
        double rightCol = panelX + PANEL_WIDTH * 0.72;

        g.setTextAlign(TextAlignment.CENTER);
        g.setFill(redColor);
        g.fillText("RED", leftCol, headerY);
        g.setFill(blueColor);
        g.fillText("BLUE", rightCol, headerY);

        Font label = Font.font("Georgia", 14);
        Font value = Font.font("Georgia", FontWeight.BOLD, 14);
        double labelX = panelX + PANEL_PADDING;
        for (int i = 0; i < ROW_LABELS.length; i++) {
            double y = headerY + 24 + i * LINE_HEIGHT;
            g.setFont(label);
            g.setFill(DIM);
            g.setTextAlign(TextAlignment.LEFT);
            g.fillText(ROW_LABELS[i], labelX, y);

            g.setFont(value);
            g.setFill(WHITE);
            g.setTextAlign(TextAlignment.CENTER);
            g.fillText(valueFor(i, Faction.RED, summary, secondsPerTick), leftCol, y);
            g.fillText(valueFor(i, Faction.BLUE, summary, secondsPerTick), rightCol, y);
        }

        Font hint = Font.font("Georgia", 12);
        g.setFont(hint);
        g.setFill(HINT);
        g.setTextAlign(TextAlignment.CENTER);
        g.fillText("click or SPACE to continue", cx, panelY + panelHeight - 14);

        g.restore();
    }

    private static String valueFor(int row, Faction f, BattleStats.Summary s, double secondsPerTick) {
        switch (row) {
            case 0: return Integer.toString(s.kills(f));
            case 1: return Integer.toString(s.deaths(f));
            case 2: return formatDouble(s.damageDealt(f));
            case 3: return formatDouble(s.biggestHit(f));
            case 4: return String.format("%.1f", Math.max(0.0, s.lastFallTick(f) * secondsPerTick));
            default: return "?";
        }
    }

    private static String formatDouble(double v) {
        if (v >= 1000.0) {
            return String.format("%.0f", v);
        }
        return String.format("%.1f", v);
    }
}
