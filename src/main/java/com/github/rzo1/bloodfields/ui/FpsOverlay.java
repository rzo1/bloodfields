package com.github.rzo1.bloodfields.ui;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

public final class FpsOverlay {

    private static final Font FONT = Font.font(14);

    public void render(GraphicsContext gc, double fps) {
        gc.save();
        gc.setFill(Color.WHITE);
        gc.setFont(FONT);
        gc.fillText(String.format("FPS: %.0f", fps), 10, 20);
        gc.restore();
    }
}
