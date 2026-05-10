package com.github.rzo1.armyclash.ui;

import com.github.rzo1.armyclash.model.Army;
import com.github.rzo1.armyclash.model.Faction;
import com.github.rzo1.armyclash.model.Terrain.TileType;
import com.github.rzo1.armyclash.model.Unit;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class DeploymentDemo extends Application {

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 800;
    private static final double TILE = 32.0;
    private static final int BUDGET = 100;

    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();
        root.setStyle(Theme.rootBackgroundStyle());
        Canvas canvas = new Canvas(WIDTH - 220, HEIGHT);
        root.setCenter(canvas);

        Army playerArmy = new Army(Faction.BLUE, BUDGET);
        DeploymentZone zone = new DeploymentZone(0, HEIGHT / 2.0, canvas.getWidth(), HEIGHT);
        AtomicLong ids = new AtomicLong(1);
        DeploymentController controller = new DeploymentController(canvas, playerArmy, zone, TILE, ids::getAndIncrement);
        Hud hud = new Hud(playerArmy, controller, () -> System.out.println("Start battle clicked: units=" + playerArmy.units().size()));
        root.setLeft(hud);

        Scene scene = new Scene(root, WIDTH, HEIGHT);
        Theme.applyStylesheet(scene);
        stage.setTitle("Bloodfield — Deployment Demo");
        stage.setScene(scene);
        stage.show();

        GraphicsContext gc = canvas.getGraphicsContext2D();
        Renderer renderer = new Renderer();
        DeploymentOverlayRenderer overlay = new DeploymentOverlayRenderer();
        Camera camera = new Camera();

        TileType[][] terrain = new TileType[(int) Math.ceil(HEIGHT / TILE)][(int) Math.ceil(canvas.getWidth() / TILE)];
        for (int r = 0; r < terrain.length; r++) {
            for (int c = 0; c < terrain[r].length; c++) {
                terrain[r][c] = TileType.GRASS;
            }
        }

        new AnimationTimer() {
            @Override
            public void handle(long now) {
                List<Unit> units = playerArmy.units();
                renderer.render(gc, canvas.getWidth(), canvas.getHeight(),
                        terrain, TILE, units, List.of(), camera);
                overlay.render(gc, controller, zone, camera);
            }
        }.start();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
