package com.github.rzo1.armyclash.ui;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

public final class HandoverRenderer {

    private static final Color BG = Color.web("#0a0604");
    private static final Color TITLE = Color.web(Theme.TEXT_PRIMARY);
    private static final Color HINT = Color.web(Theme.TEXT_DIM);
    private static final Color SHADOW = Color.rgb(0, 0, 0, 0.7);
    private static final Color ACCENT = Color.web(Theme.TEXT_ACCENT);

    public void render(GraphicsContext g, double w, double h, String message, String hint) {
        g.save();
        g.setFill(BG);
        g.fillRect(0, 0, w, h);

        double cx = w / 2.0;
        double cy = h / 2.0;
        g.setTextAlign(TextAlignment.CENTER);

        g.setStroke(ACCENT);
        g.setLineWidth(2);
        g.strokeLine(cx - 220, cy - 60, cx + 220, cy - 60);
        g.strokeLine(cx - 220, cy + 70, cx + 220, cy + 70);

        Font headline = Font.font("Georgia", FontWeight.BOLD, 40);
        g.setFont(headline);
        g.setFill(SHADOW);
        if (message != null) {
            g.fillText(message, cx + 3, cy - 10 + 3);
        }
        g.setFill(TITLE);
        if (message != null) {
            g.fillText(message, cx, cy - 10);
        }

        if (hint != null) {
            g.setFont(Font.font("Georgia", 18));
            g.setFill(HINT);
            g.fillText(hint, cx, cy + 40);
        }

        g.restore();
    }
}
