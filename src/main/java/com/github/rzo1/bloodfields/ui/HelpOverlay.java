package com.github.rzo1.bloodfields.ui;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

public final class HelpOverlay {

    private static final double PANEL_WIDTH_RATIO = 0.70;
    private static final double PANEL_HEIGHT_RATIO = 0.80;

    private static final Font HEADER_FONT = Font.font("Georgia", FontWeight.BOLD, 22);
    private static final Font SECTION_FONT = Font.font("Georgia", FontWeight.BOLD, 16);
    private static final Font BODY_FONT = Font.font("Georgia", FontWeight.NORMAL, 13);
    private static final Font HINT_FONT = Font.font("Georgia", FontWeight.NORMAL, 12);

    private static final Color PANEL_FILL = Color.rgb(20, 14, 12, 0.92);
    private static final Color BORDER = Color.web(Theme.BUTTON_BORDER);
    private static final Color HEADER_COLOR = Color.web(Theme.TEXT_ACCENT);
    private static final Color SECTION_COLOR = Color.web(Theme.TEXT_ACCENT);
    private static final Color BODY_COLOR = Color.web(Theme.TEXT_PRIMARY);
    private static final Color DIM_COLOR = Color.web(Theme.TEXT_DIM);

    private boolean visible;

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean v) {
        this.visible = v;
    }

    public void toggle() {
        this.visible = !this.visible;
    }

    public void render(GraphicsContext gc, double width, double height) {
        if (!visible) return;

        double pw = width * PANEL_WIDTH_RATIO;
        double ph = height * PANEL_HEIGHT_RATIO;
        double px = (width - pw) / 2.0;
        double py = (height - ph) / 2.0;

        gc.save();
        gc.setFill(PANEL_FILL);
        gc.fillRect(px, py, pw, ph);
        gc.setStroke(BORDER);
        gc.setLineWidth(2);
        gc.strokeRect(px, py, pw, ph);

        double padX = 24;
        double padY = 24;
        double cursorY = py + padY + 22;

        gc.setFill(HEADER_COLOR);
        gc.setFont(HEADER_FONT);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("BLOODFIELD — CONTROLS & HELP", px + pw / 2.0, cursorY);
        cursorY += 12;

        gc.setFont(HINT_FONT);
        gc.setFill(DIM_COLOR);
        gc.fillText(BuildInfo.displayString(), px + pw / 2.0, cursorY + 6);
        cursorY += 14;

        gc.setStroke(BORDER);
        gc.setLineWidth(1);
        gc.strokeLine(px + padX, cursorY, px + pw - padX, cursorY);
        cursorY += 24;

        double leftX = px + padX;
        double rightX = px + pw / 2.0 + 8;
        double colWidth = pw / 2.0 - padX - 8;

        gc.setTextAlign(TextAlignment.LEFT);

        double leftY = cursorY;
        leftY = renderSection(gc, leftX, leftY, "Controls",
                new String[][]{
                        {"ESC", "exit / back to menu"},
                        {"ENTER / SPACE", "confirm / start"}
                });
        leftY += 8;

        leftY = renderSection(gc, leftX, leftY, "Battle",
                new String[][]{
                        {"1", "0.5x speed"},
                        {"2", "1x speed (normal)"},
                        {"3", "2x speed"},
                        {"4", "4x speed"},
                        {"P", "toggle pause"},
                        {"R", "deploy RED reserves (versus)"},
                        {"B", "deploy BLUE reserves (versus)"},
                        {"? / H", "toggle this help overlay"}
                });
        leftY += 8;

        leftY = renderSection(gc, leftX, leftY, "Deployment",
                new String[][]{
                        {"Left click", "place selected unit"},
                        {"Right click", "remove unit"},
                        {"Tower / Wall", "click with ranged unit to garrison"}
                });

        double rightY = cursorY;
        rightY = renderTextSection(gc, rightX, rightY, colWidth, "Modes", new String[]{
                "CAMPAIGN — 6 levels, escalating difficulty. Win to earn the next-level code.",
                "VERSUS — 2-player hot-seat: each picks budget, dragons, hero skill, hidden from the other.",
                "SKIRMISH — single-player vs bot."
        });
        rightY += 8;

        rightY = renderTextSection(gc, rightX, rightY, colWidth, "Tips", new String[]{
                "Heroes (GENERAL) provide auras to nearby allies — pick one of 5 skills.",
                "Healers stay near the army and heal wounded allies.",
                "Necromancers consume corpses to revive units or detonate them.",
                "Catapults are slow but deal massive AoE.",
                "Towers protect garrisoned ranged units from melee."
        });

        gc.setFont(HINT_FONT);
        gc.setFill(DIM_COLOR);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("Press ? or H to close", px + pw / 2.0, py + ph - 14);

        gc.restore();
    }

    private double renderSection(GraphicsContext gc, double x, double y, String title, String[][] rows) {
        gc.setFont(SECTION_FONT);
        gc.setFill(SECTION_COLOR);
        gc.fillText(title, x, y);
        y += 20;
        gc.setFont(BODY_FONT);
        gc.setFill(BODY_COLOR);
        for (String[] row : rows) {
            gc.fillText(row[0], x, y);
            gc.fillText("— " + row[1], x + 130, y);
            y += 18;
        }
        return y;
    }

    private double renderTextSection(GraphicsContext gc, double x, double y, double maxWidth,
                                     String title, String[] paragraphs) {
        gc.setFont(SECTION_FONT);
        gc.setFill(SECTION_COLOR);
        gc.fillText(title, x, y);
        y += 20;
        gc.setFont(BODY_FONT);
        gc.setFill(BODY_COLOR);
        for (String p : paragraphs) {
            y = wrapText(gc, p, x, y, maxWidth);
            y += 6;
        }
        return y;
    }

    private double wrapText(GraphicsContext gc, String text, double x, double y, double maxWidth) {
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        double charWidth = 7.0;
        int maxChars = (int) Math.max(20, maxWidth / charWidth);
        for (String w : words) {
            if (line.length() + 1 + w.length() > maxChars && line.length() > 0) {
                gc.fillText(line.toString(), x, y);
                y += 16;
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(w);
        }
        if (line.length() > 0) {
            gc.fillText(line.toString(), x, y);
            y += 16;
        }
        return y;
    }

    public void renderHint(GraphicsContext gc, double width, double height) {
        if (visible) return;
        gc.save();
        gc.setFont(HINT_FONT);
        gc.setFill(Color.rgb(122, 108, 92, 0.6));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("Press ? for help", width / 2.0, height - 8);
        gc.restore();
    }
}
