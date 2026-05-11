package com.github.rzo1.bloodfields.ui;

import com.github.rzo1.bloodfields.engine.GameState;
import com.github.rzo1.bloodfields.model.Army;
import com.github.rzo1.bloodfields.model.Unit;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

public final class FpsOverlay {

    private static final Font FONT = Font.font(14);
    private static final double LINE_HEIGHT = 16.0;

    private GameState state;
    private ParticleSystem particles;

    public void setState(GameState state) {
        this.state = state;
    }

    public void setParticles(ParticleSystem particles) {
        this.particles = particles;
    }

    public void render(GraphicsContext gc, double fps) {
        gc.save();
        gc.setFill(Color.WHITE);
        gc.setFont(FONT);
        double y = 20.0;
        gc.fillText(String.format("FPS: %.0f", fps), 10, y);
        if (state != null) {
            y += LINE_HEIGHT;
            gc.fillText("RED:  " + countAlive(state.red), 10, y);
            y += LINE_HEIGHT;
            gc.fillText("BLUE: " + countAlive(state.blue), 10, y);
            y += LINE_HEIGHT;
            int projCount = state.projectiles != null ? state.projectiles.size() : 0;
            gc.fillText("PROJ: " + projCount, 10, y);
        }
        if (particles != null) {
            y += LINE_HEIGHT;
            gc.fillText("PART: " + particles.size(), 10, y);
        }
        gc.restore();
    }

    private static int countAlive(Army army) {
        if (army == null) return 0;
        int n = 0;
        for (Unit u : army.units()) {
            if (u.isAlive()) n++;
        }
        return n;
    }
}
