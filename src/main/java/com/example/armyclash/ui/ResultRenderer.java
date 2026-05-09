package com.example.armyclash.ui;

import com.example.armyclash.model.Faction;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

public final class ResultRenderer {

    private static final Color RED_COLOR = Color.web("#c8332e");
    private static final Color BLUE_COLOR = Color.web("#2f5fb8");
    private static final Color SHADOW = Color.rgb(0, 0, 0, 0.45);
    private static final Color PANEL = Color.rgb(0, 0, 0, 0.55);
    private static final Color WHITE = Color.WHITE;
    private static final Color HINT = Color.web("#dddddd");

    public void render(GraphicsContext g, double w, double h, Faction winner,
                       int redKilled, int redTotal, int blueKilled, int blueTotal) {
        render(g, w, h, winner, redKilled, redTotal, blueKilled, blueTotal, null);
    }

    public void render(GraphicsContext g, double w, double h, Faction winner,
                       int redKilled, int redTotal, int blueKilled, int blueTotal,
                       String nextLevelCode) {
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
        double panelHeight = showCode ? 260 : 220;

        g.save();
        g.setFill(PANEL);
        g.fillRect(0, cy - 110, w, panelHeight);

        g.setTextAlign(TextAlignment.CENTER);

        Font banner = Font.font("System", FontWeight.BOLD, 56);
        g.setFont(banner);
        g.setFill(SHADOW);
        g.fillText(headline, cx + 3, cy - 30 + 3);
        g.setFill(headlineColor);
        g.fillText(headline, cx, cy - 30);

        Font stats = Font.font("System", 18);
        g.setFont(stats);
        g.setFill(WHITE);
        String redLine = "RED  killed: " + redKilled + " / " + redTotal;
        String blueLine = "BLUE  killed: " + blueKilled + " / " + blueTotal;
        g.fillText(redLine + "        " + blueLine, cx, cy + 18);

        if (showCode) {
            g.setFont(Font.font("System", FontWeight.BOLD, 18));
            g.setFill(Color.web("#ffd76b"));
            g.fillText("Code for next level: " + nextLevelCode, cx, cy + 50);
        }

        g.setFont(Font.font("System", 14));
        g.setFill(HINT);
        g.fillText("click or SPACE to play again", cx, cy + (showCode ? 90 : 70));

        g.restore();
    }
}
