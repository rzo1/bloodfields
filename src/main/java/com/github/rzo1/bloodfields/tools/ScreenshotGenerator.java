package com.github.rzo1.bloodfields.tools;

import com.github.rzo1.bloodfields.ai.UnitAI;
import com.github.rzo1.bloodfields.engine.GameLoop;
import com.github.rzo1.bloodfields.engine.GameState;
import com.github.rzo1.bloodfields.engine.SpatialHashGrid;
import com.github.rzo1.bloodfields.engine.Structure;
import com.github.rzo1.bloodfields.engine.World;
import com.github.rzo1.bloodfields.model.Army;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.HeroSkill;
import com.github.rzo1.bloodfields.model.Terrain;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitType;
import com.github.rzo1.bloodfields.ui.AssetLoader;
import com.github.rzo1.bloodfields.ui.BattleSmoke;
import com.github.rzo1.bloodfields.ui.BloodyTiles;
import com.github.rzo1.bloodfields.ui.Hud;
import com.github.rzo1.bloodfields.ui.Camera;
import com.github.rzo1.bloodfields.ui.CameraShake;
import com.github.rzo1.bloodfields.ui.CrowFlock;
import com.github.rzo1.bloodfields.ui.DeathTracker;
import com.github.rzo1.bloodfields.ui.DeploymentOverlayRenderer;
import com.github.rzo1.bloodfields.ui.DeploymentZone;
import com.github.rzo1.bloodfields.ui.LimbField;
import com.github.rzo1.bloodfields.ui.MainMenuRenderer;
import com.github.rzo1.bloodfields.ui.MapStructures;
import com.github.rzo1.bloodfields.ui.ParticleSystem;
import com.github.rzo1.bloodfields.ui.RagdollOverlay;
import com.github.rzo1.bloodfields.ui.Renderer;
import com.github.rzo1.bloodfields.ui.ScorchMarks;
import com.github.rzo1.bloodfields.ui.StuckArrows;
import com.github.rzo1.bloodfields.ui.Theme;
import com.github.rzo1.bloodfields.ui.Vulture;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Headless screenshot generator. Produces representative PNGs of Bloodfield
 * scenarios into {@code docs/screenshots/}. Intended for README + GitHub Pages
 * use; runs without a display server when {@code glass.platform=Monocle}.
 *
 * Run via the {@code screenshots} Maven profile:
 *   mvn -Pscreenshots verify -DskipTests
 */
public final class ScreenshotGenerator {

    public static final String OUT_DIR = "docs/screenshots";

    public static final List<String> SCENARIOS = List.of(
            "menu.png",
            "deployment.png",
            "battle.png",
            "fortress.png",
            "finale.png"
    );

    private static final int GAME_WIDTH = 1280;
    private static final int GAME_HEIGHT = 800;
    private static final int HUD_WIDTH = 220;
    private static final int FULL_WIDTH = GAME_WIDTH + HUD_WIDTH;
    private static final double TILE = 32.0;
    private static final double GRID_CELL = 64.0;

    private ScreenshotGenerator() {}

    public static void main(String[] args) throws Exception {
        bootHeadlessFx();

        Path outDir = Paths.get(args.length > 0 ? args[0] : OUT_DIR);
        Files.createDirectories(outDir);

        CountDownLatch startup = new CountDownLatch(1);
        AtomicReference<Throwable> bootError = new AtomicReference<>();
        try {
            Platform.startup(startup::countDown);
        } catch (IllegalStateException alreadyStarted) {
            startup.countDown();
        } catch (Throwable t) {
            bootError.set(t);
            startup.countDown();
        }
        if (!startup.await(15, TimeUnit.SECONDS)) {
            throw new IllegalStateException("JavaFX Platform.startup did not complete");
        }
        if (bootError.get() != null) {
            throw new IllegalStateException("JavaFX boot failed: " + bootError.get(), bootError.get());
        }

        try {
            renderScenarioOnFxThread(() -> renderMenu(outDir));
            renderScenarioOnFxThread(() -> renderDeployment(outDir));
            renderScenarioOnFxThread(() -> renderBattle(outDir));
            renderScenarioOnFxThread(() -> renderFortress(outDir));
            renderScenarioOnFxThread(() -> renderFinale(outDir));
        } finally {
            Platform.exit();
        }
        System.out.println("Wrote screenshots to " + outDir.toAbsolutePath());
    }

    private static void bootHeadlessFx() {
        if (System.getProperty("glass.platform") == null) {
            System.setProperty("glass.platform", "Monocle");
        }
        if (System.getProperty("monocle.platform") == null) {
            System.setProperty("monocle.platform", "Headless");
        }
        if (System.getProperty("prism.order") == null) {
            System.setProperty("prism.order", "sw");
        }
        if (System.getProperty("prism.text") == null) {
            System.setProperty("prism.text", "t2k");
        }
        if (System.getProperty("javafx.platform") == null) {
            System.setProperty("javafx.platform", "monocle");
        }
        System.setProperty("java.awt.headless", "true");
    }

    private static void renderScenarioOnFxThread(FxTask task) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> err = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                err.set(t);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(60, TimeUnit.SECONDS)) {
            throw new IllegalStateException("FX scenario did not finish in time");
        }
        if (err.get() != null) {
            throw new RuntimeException(err.get());
        }
    }

    private interface FxTask {
        void run() throws Exception;
    }

    // ---- scenarios -------------------------------------------------------

    private static void renderMenu(Path outDir) throws IOException {
        MainMenuRenderer pane = new MainMenuRenderer();
        pane.setOnCampaign(() -> {});
        pane.setOnSkirmish(() -> {});
        pane.setOnVersus(() -> {});
        pane.setOnLoadLevel(idx -> {});

        BorderPane root = new BorderPane();
        root.setStyle(Theme.rootBackgroundStyle());
        root.setCenter(pane);
        root.setPrefSize(FULL_WIDTH, GAME_HEIGHT);

        Scene scene = new Scene(root, FULL_WIDTH, GAME_HEIGHT);
        Theme.applyStylesheet(scene);

        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.web("#100806"));
        WritableImage img = new WritableImage(FULL_WIDTH, GAME_HEIGHT);
        scene.snapshot(img);

        writePng(img, outDir.resolve("menu.png"));
    }

    private static void renderDeployment(Path outDir) throws IOException {
        // Mage Cabal-style enemy + a half-army of mixed friendly units placed
        // inside the player's lower deployment zone. HUD on the left.
        World world = World.battlefield(GAME_WIDTH, GAME_HEIGHT, TILE);
        SpatialHashGrid grid = new SpatialHashGrid(GAME_WIDTH, GAME_HEIGHT, GRID_CELL);
        Army red = new Army(Faction.RED, 520);
        Army blue = new Army(Faction.BLUE, 10000);
        GameState state = new GameState(world, red, blue, grid);
        state.phase = GameState.Phase.DEPLOYMENT;

        // Enemy in upper half (mage cabal flavour).
        spawnMageCabal(blue, state);

        // Player units, placed in the bottom half.
        placePlayerArmyMixed(red, state);

        Canvas canvas = new Canvas(FULL_WIDTH, GAME_HEIGHT);
        GraphicsContext g = canvas.getGraphicsContext2D();

        // Solid HUD-coloured strip on the left so the snapshot includes a HUD
        // band even though we don't render the live Hud node onto the canvas.
        g.setFill(Color.web("#141210"));
        g.fillRect(0, 0, HUD_WIDTH, GAME_HEIGHT);
        drawHudPlaceholder(g, "DEPLOYMENT", "Level 5/11: Mage Cabal",
                "Points: " + red.spentPoints() + "/" + red.deploymentBudget());

        // Translate so the world canvas sits to the right of the HUD strip.
        g.save();
        g.translate(HUD_WIDTH, 0);

        Camera camera = new Camera();
        Renderer renderer = new Renderer();
        renderer.setStructureField(state.structures);
        List<Unit> all = collectAllUnits(state);
        renderer.render(g, GAME_WIDTH, GAME_HEIGHT,
                world.terrain, world.tileSize,
                all, state.projectiles, null,
                null, state.corpses, null,
                camera, 5, false,
                null, null, null, null, null, null, null);

        // Deployment zone overlay (lower half).
        DeploymentZone zone = new DeploymentZone(0, GAME_HEIGHT / 2.0,
                GAME_WIDTH, GAME_HEIGHT);
        DeploymentOverlayRenderer overlay = new DeploymentOverlayRenderer();
        overlay.render(g, null, zone, camera);
        g.restore();

        WritableImage img = snapshot(canvas, FULL_WIDTH, GAME_HEIGHT);
        writePng(img, outDir.resolve("deployment.png"));
    }

    private static void renderBattle(Path outDir) throws IOException {
        World world = World.battlefield(GAME_WIDTH, GAME_HEIGHT, TILE);
        SpatialHashGrid grid = new SpatialHashGrid(GAME_WIDTH, GAME_HEIGHT, GRID_CELL);
        Army red = new Army(Faction.RED, 10000);
        Army blue = new Army(Faction.BLUE, 10000);
        GameState state = new GameState(world, red, blue, grid);
        state.phase = GameState.Phase.BATTLE;

        spawnCavalryChargeFriendly(red, state);
        spawnCavalryChargeEnemy(blue, state);

        BattleScene scene = simulate(state, 6.0);
        WritableImage img = renderInBattle(scene, /* sky */ false, /* level */ 4);
        writePng(img, outDir.resolve("battle.png"));
    }

    private static void renderFortress(Path outDir) throws IOException {
        // fortress_wall map: structures, garrisoned tower archers, blood pools.
        World world = new World(GAME_WIDTH, GAME_HEIGHT, TILE,
                allGrass(GAME_WIDTH, GAME_HEIGHT, TILE));
        SpatialHashGrid grid = new SpatialHashGrid(GAME_WIDTH, GAME_HEIGHT, GRID_CELL);
        Army red = new Army(Faction.RED, 10000);
        Army blue = new Army(Faction.BLUE, 10000);
        GameState state = new GameState(world, red, blue, grid);
        state.phase = GameState.Phase.BATTLE;

        long[] nextStructId = {-1L};
        for (Structure s : MapStructures.forPreset("fortress_wall",
                GAME_WIDTH, GAME_HEIGHT, () -> nextStructId[0]--)) {
            state.structures.add(s);
        }

        spawnSiegeAttackers(red, state);
        spawnFortressDefenders(blue, state);

        for (Structure s : state.structures.structures()) {
            if (s.type() == com.github.rzo1.bloodfields.engine.StructureType.TOWER) {
                int cap = com.github.rzo1.bloodfields.engine.StructureField.garrisonCapacity(s.type());
                int placed = 0;
                for (int i = 0; i < cap && placed < 3; i++) {
                    double cx = s.x() + s.width() / 2.0;
                    double cy = s.y() + s.height() / 2.0;
                    Unit garrisoned = new Unit(state.nextUnitId++, UnitType.ARCHER,
                            Faction.BLUE, cx, cy, 1.0);
                    blue.add(garrisoned);
                    state.structures.garrison(s, garrisoned);
                    placed++;
                }
            }
        }

        BattleScene scene = simulate(state, 14.0);
        WritableImage img = renderInBattle(scene, /* sky */ false, /* level */ 9);
        writePng(img, outDir.resolve("fortress.png"));
    }

    private static void renderFinale(Path outDir) throws IOException {
        World world = new World(GAME_WIDTH, GAME_HEIGHT, TILE,
                allGrass(GAME_WIDTH, GAME_HEIGHT, TILE));
        SpatialHashGrid grid = new SpatialHashGrid(GAME_WIDTH, GAME_HEIGHT, GRID_CELL);
        Army red = new Army(Faction.RED, 10000);
        Army blue = new Army(Faction.BLUE, 10000);
        GameState state = new GameState(world, red, blue, grid);
        state.phase = GameState.Phase.BATTLE;

        long[] nextStructId = {-1L};
        for (Structure s : MapStructures.forPreset("fortress_wall",
                GAME_WIDTH, GAME_HEIGHT, () -> nextStructId[0]--)) {
            state.structures.add(s);
        }
        spawnFinaleAttackers(red, state);
        spawnFinaleDefenders(blue, state);
        blue.setHeroSkill(HeroSkill.BATTLE_LUST);
        red.setHeroSkill(HeroSkill.IRON_DISCIPLINE);

        BattleScene scene = simulate(state, 30.0);
        WritableImage img = renderInBattle(scene, /* sky */ true, /* level */ 6);
        writePng(img, outDir.resolve("finale.png"));
    }

    // ---- shared rendering ------------------------------------------------

    private static final class BattleScene {
        final GameState state;
        final ParticleSystem particles;
        final BloodyTiles bloody;
        final CrowFlock crows;
        final Vulture vulture;
        final LimbField limbs;
        final StuckArrows stuckArrows;
        final RagdollOverlay ragdolls;
        final ScorchMarks scorchMarks;
        final BattleSmoke smoke;
        final double simTime;
        final int redInitial;
        final int blueInitial;

        BattleScene(GameState state, ParticleSystem particles, BloodyTiles bloody,
                    CrowFlock crows, Vulture vulture, LimbField limbs,
                    StuckArrows stuckArrows, RagdollOverlay ragdolls,
                    ScorchMarks scorchMarks, BattleSmoke smoke, double simTime,
                    int redInitial, int blueInitial) {
            this.state = state;
            this.particles = particles;
            this.bloody = bloody;
            this.crows = crows;
            this.vulture = vulture;
            this.limbs = limbs;
            this.stuckArrows = stuckArrows;
            this.ragdolls = ragdolls;
            this.scorchMarks = scorchMarks;
            this.smoke = smoke;
            this.simTime = simTime;
            this.redInitial = redInitial;
            this.blueInitial = blueInitial;
        }
    }

    /**
     * Run a simulated battle and accumulate visual side-effects (corpses,
     * blood pools, bloody tiles, crows, etc.) the way GameApp does each frame,
     * so the final snapshot has visible carnage even though no display server
     * was ever attached.
     */
    private static BattleScene simulate(GameState state, double seconds) {
        GameLoop loop = new GameLoop(state, new UnitAI());
        ParticleSystem particles = new ParticleSystem(new Random(0xB100D5L));
        BloodyTiles bloody = new BloodyTiles();
        CrowFlock crows = new CrowFlock(new Random(0xC404L));
        Vulture vulture = new Vulture();
        LimbField limbs = new LimbField();
        StuckArrows stuckArrows = new StuckArrows();
        RagdollOverlay ragdolls = new RagdollOverlay();
        ScorchMarks scorchMarks = new ScorchMarks();
        BattleSmoke smoke = new BattleSmoke();
        smoke.setWorldBounds(GAME_WIDTH, GAME_HEIGHT);
        DeathTracker deathTracker = new DeathTracker(new Random(0xDEADL));
        Map<Long, UnitType> killerByUnitId = new HashMap<>();

        int redInitial = aliveCount(state.red);
        int blueInitial = aliveCount(state.blue);

        double dt = 1.0 / 60.0;
        int steps = (int) Math.round(seconds / dt);
        double simTime = 0.0;
        double tile = state.world.tileSize;
        for (int i = 0; i < steps; i++) {
            loop.step(dt);
            simTime += dt;
            List<Unit> all = collectAllUnits(state);
            particles.update(dt);
            stuckArrows.update(dt);
            ragdolls.update(dt);
            scorchMarks.update(dt, particles, tile);
            crows.update(dt, bloody, tile, simTime);
            vulture.update(dt, bloody, tile, simTime);
            smoke.update(dt, state.corpses != null ? state.corpses.size() : 0);
            deathTracker.detectDeaths(all, particles, state.corpses, bloody,
                    tile, simTime, limbs, ragdolls, null, killerByUnitId, scorchMarks);
        }
        return new BattleScene(state, particles, bloody, crows, vulture,
                limbs, stuckArrows, ragdolls, scorchMarks, smoke, simTime,
                redInitial, blueInitial);
    }

    private static WritableImage renderInBattle(BattleScene scene, boolean redSky,
                                                int campaignLevel) {
        GameState state = scene.state;
        Canvas canvas = new Canvas(FULL_WIDTH, GAME_HEIGHT);
        GraphicsContext g = canvas.getGraphicsContext2D();

        g.setFill(Color.web("#141210"));
        g.fillRect(0, 0, HUD_WIDTH, GAME_HEIGHT);
        int redCas = Math.max(0, scene.redInitial - aliveCount(state.red));
        int blueCas = Math.max(0, scene.blueInitial - aliveCount(state.blue));
        drawHudPlaceholder(g, "BATTLE", null,
                "Casualties: " + redCas + " / " + blueCas);

        g.save();
        g.translate(HUD_WIDTH, 0);

        Camera camera = new Camera();
        Renderer renderer = new Renderer();
        renderer.setAuraContext(state, scene.simTime);
        renderer.setStructureField(state.structures);
        renderer.corpseRenderer().update(0.5, state.corpses);

        List<Unit> all = collectAllUnits(state);
        renderer.render(g, GAME_WIDTH, GAME_HEIGHT,
                state.world.terrain, state.world.tileSize,
                all, state.projectiles, scene.particles.pools(),
                scene.bloody, state.corpses, scene.crows,
                camera, campaignLevel, redSky,
                scene.limbs, scene.stuckArrows, scene.ragdolls,
                scene.scorchMarks, scene.smoke, scene.vulture, new CameraShake());
        scene.particles.render(g, camera);
        g.restore();

        return snapshot(canvas, FULL_WIDTH, GAME_HEIGHT);
    }

    private static int aliveCount(Army army) {
        int n = 0;
        for (Unit u : army.units()) {
            if (u != null && u.isAlive()) n++;
        }
        return n;
    }

    // ---- army setup helpers ---------------------------------------------

    private static void spawnMageCabal(Army army, GameState state) {
        double cx = GAME_WIDTH / 2.0;
        double infantrySpacing = 55.0;
        for (int i = 0; i < 16; i++) {
            double x = cx + (i - 7.5) * infantrySpacing;
            place(army, state, UnitType.INFANTRY, x, 230.0);
        }
        for (int i = 0; i < 14; i++) {
            double x = cx + (i - 6.5) * 70.0;
            place(army, state, UnitType.ARCHER, x, 150.0);
        }
        for (int i = 0; i < 10; i++) {
            double x = cx + (i - 4.5) * 95.0;
            place(army, state, UnitType.MAGE, x, 70.0);
        }
    }

    private static void placePlayerArmyMixed(Army army, GameState state) {
        double cx = GAME_WIDTH / 2.0;
        // Mixed half-army inside the lower-half deployment zone (y >= HEIGHT/2).
        for (int i = 0; i < 12; i++) {
            place(army, state, UnitType.INFANTRY, cx + (i - 5.5) * 40.0, 720.0);
        }
        for (int i = 0; i < 8; i++) {
            place(army, state, UnitType.PIKEMAN, cx + (i - 3.5) * 55.0, 660.0);
        }
        for (int i = 0; i < 8; i++) {
            place(army, state, UnitType.ARCHER, cx + (i - 3.5) * 75.0, 600.0);
        }
        for (int i = 0; i < 4; i++) {
            place(army, state, UnitType.MAGE, cx + (i - 1.5) * 110.0, 540.0);
        }
        place(army, state, UnitType.GENERAL, cx, 500.0);
    }

    private static void spawnCavalryChargeFriendly(Army army, GameState state) {
        double cx = GAME_WIDTH / 2.0;
        for (int i = 0; i < 14; i++) {
            place(army, state, UnitType.INFANTRY, cx + (i - 6.5) * 50.0, 600.0);
        }
        for (int i = 0; i < 10; i++) {
            place(army, state, UnitType.ARCHER, cx + (i - 4.5) * 70.0, 660.0);
        }
        for (int i = 0; i < 8; i++) {
            place(army, state, UnitType.PIKEMAN, cx + (i - 3.5) * 60.0, 540.0);
        }
        place(army, state, UnitType.GENERAL, cx, 700.0);
    }

    private static void spawnCavalryChargeEnemy(Army army, GameState state) {
        double cx = GAME_WIDTH / 2.0;
        for (int i = 0; i < 14; i++) {
            place(army, state, UnitType.CAVALRY, cx + (i - 6.5) * 60.0, 240.0);
        }
        for (int i = 0; i < 10; i++) {
            place(army, state, UnitType.INFANTRY, cx + (i - 4.5) * 60.0, 130.0);
        }
        for (int i = 0; i < 6; i++) {
            place(army, state, UnitType.ARCHER, cx + (i - 2.5) * 90.0, 70.0);
        }
    }

    private static void spawnSiegeAttackers(Army army, GameState state) {
        double cx = GAME_WIDTH / 2.0;
        for (int i = 0; i < 14; i++) {
            place(army, state, UnitType.INFANTRY, cx + (i - 6.5) * 45.0, 700.0);
        }
        for (int i = 0; i < 10; i++) {
            place(army, state, UnitType.ARCHER, cx + (i - 4.5) * 65.0, 640.0);
        }
        for (int i = 0; i < 4; i++) {
            place(army, state, UnitType.CATAPULT, cx + (i - 1.5) * 130.0, 580.0);
        }
        for (int i = 0; i < 4; i++) {
            place(army, state, UnitType.GOLEM, cx + (i - 1.5) * 140.0, 760.0);
        }
        place(army, state, UnitType.GENERAL, cx, 760.0);
    }

    private static void spawnFortressDefenders(Army army, GameState state) {
        double cx = GAME_WIDTH / 2.0;
        // Behind (above) the wall — the wall sits at HEIGHT/2.
        for (int i = 0; i < 12; i++) {
            place(army, state, UnitType.INFANTRY, cx + (i - 5.5) * 50.0, 350.0);
        }
        for (int i = 0; i < 8; i++) {
            place(army, state, UnitType.PIKEMAN, cx + (i - 3.5) * 60.0, 305.0);
        }
        for (int i = 0; i < 8; i++) {
            place(army, state, UnitType.ARCHER, cx + (i - 3.5) * 70.0, 220.0);
        }
        for (int i = 0; i < 4; i++) {
            place(army, state, UnitType.MAGE, cx + (i - 1.5) * 110.0, 150.0);
        }
        place(army, state, UnitType.GENERAL, cx, 80.0);
    }

    private static void spawnFinaleAttackers(Army army, GameState state) {
        double cx = GAME_WIDTH / 2.0;
        for (int i = 0; i < 18; i++) {
            place(army, state, UnitType.INFANTRY, cx + (i - 8.5) * 38.0, 700.0);
        }
        for (int i = 0; i < 12; i++) {
            place(army, state, UnitType.ARCHER, cx + (i - 5.5) * 55.0, 640.0);
        }
        for (int i = 0; i < 6; i++) {
            place(army, state, UnitType.CATAPULT, cx + (i - 2.5) * 110.0, 580.0);
        }
        for (int i = 0; i < 5; i++) {
            place(army, state, UnitType.MAGE, cx + (i - 2.0) * 90.0, 750.0);
        }
        for (int i = 0; i < 3; i++) {
            place(army, state, UnitType.NECROMANCER, cx + (i - 1.0) * 200.0, 760.0);
        }
        place(army, state, UnitType.GENERAL, cx, 770.0);
    }

    private static void spawnFinaleDefenders(Army army, GameState state) {
        double cx = GAME_WIDTH / 2.0;
        for (int i = 0; i < 20; i++) {
            place(army, state, UnitType.INFANTRY, cx + (i - 9.5) * 45.0, 350.0);
        }
        for (int i = 0; i < 14; i++) {
            place(army, state, UnitType.PIKEMAN, cx + (i - 6.5) * 50.0, 305.0);
        }
        for (int i = 0; i < 6; i++) {
            place(army, state, UnitType.GOLEM, cx + (i - 2.5) * 130.0, 260.0);
        }
        for (int i = 0; i < 14; i++) {
            place(army, state, UnitType.ARCHER, cx + (i - 6.5) * 60.0, 200.0);
        }
        for (int i = 0; i < 8; i++) {
            place(army, state, UnitType.MAGE, cx + (i - 3.5) * 90.0, 140.0);
        }
        for (int i = 0; i < 5; i++) {
            place(army, state, UnitType.CATAPULT, cx + (i - 2.0) * 110.0, 90.0);
        }
        for (int i = 0; i < 4; i++) {
            place(army, state, UnitType.NECROMANCER, cx + (i - 1.5) * 150.0, 50.0);
        }
        place(army, state, UnitType.DRAGON, cx + 80.0, 35.0);
        place(army, state, UnitType.GENERAL, cx - 80.0, 35.0);
    }

    private static void place(Army army, GameState state, UnitType type, double x, double y) {
        army.add(new Unit(state.nextUnitId++, type, army.faction(), x, y, army.hpMultiplier()));
    }

    private static List<Unit> collectAllUnits(GameState state) {
        List<Unit> all = new ArrayList<>(state.red.units().size() + state.blue.units().size());
        for (Unit u : state.red.units()) {
            if (u != null && !u.garrisoned) all.add(u);
        }
        for (Unit u : state.blue.units()) {
            if (u != null && !u.garrisoned) all.add(u);
        }
        return all;
    }

    private static Terrain.TileType[][] allGrass(double width, double height, double tile) {
        int cols = (int) Math.ceil(width / tile);
        int rows = (int) Math.ceil(height / tile);
        Terrain.TileType[][] tiles = new Terrain.TileType[cols][rows];
        for (int c = 0; c < cols; c++) {
            for (int r = 0; r < rows; r++) {
                tiles[c][r] = Terrain.TileType.GRASS;
            }
        }
        return tiles;
    }

    private static void drawHudPlaceholder(GraphicsContext g, String title,
                                           String levelLine, String budgetLine) {
        g.setFill(Color.web("#5a1a14"));
        g.fillRect(HUD_WIDTH - 1, 0, 1, GAME_HEIGHT);

        g.setFill(Color.web("#e8d8c8"));
        g.setFont(javafx.scene.text.Font.font("Georgia",
                javafx.scene.text.FontWeight.BOLD, 20));
        g.fillText(title, 16, 36);

        if (levelLine != null) {
            g.setFill(Color.web("#c8332e"));
            g.setFont(javafx.scene.text.Font.font("Georgia",
                    javafx.scene.text.FontWeight.BOLD, 13));
            g.fillText(levelLine, 16, 64);
        }
        if (budgetLine != null) {
            g.setFill(Color.web("#e8d8c8"));
            g.setFont(javafx.scene.text.Font.font("Georgia",
                    javafx.scene.text.FontWeight.NORMAL, 13));
            g.fillText(budgetLine, 16, levelLine != null ? 84 : 64);
        }
    }

    // ---- snapshot + PNG --------------------------------------------------

    private static WritableImage snapshot(Canvas canvas, int w, int h) {
        // Wrap the canvas in a Scene so SnapshotParameters can apply a fill.
        StackPane wrap = new StackPane(canvas);
        wrap.setStyle("-fx-background-color: #100806;");
        Scene scene = new Scene(wrap, w, h, Color.web("#100806"));
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.web("#100806"));
        WritableImage img = new WritableImage(w, h);
        scene.snapshot(img);
        return img;
    }

    private static void writePng(WritableImage src, Path target) throws IOException {
        int w = (int) src.getWidth();
        int h = (int) src.getHeight();
        BufferedImage buf = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = new int[w * h];
        src.getPixelReader().getPixels(0, 0, w, h,
                PixelFormat.getIntArgbInstance(), IntBuffer.wrap(pixels), w);
        buf.setRGB(0, 0, w, h, pixels, 0, w);
        File out = target.toFile();
        File parent = out.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        ImageIO.write(buf, "png", out);
    }

}
