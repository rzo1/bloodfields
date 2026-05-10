package com.github.rzo1.bloodfields.ui;

import com.github.rzo1.bloodfields.ai.AiTuning;
import com.github.rzo1.bloodfields.ai.UnitAI;
import com.github.rzo1.bloodfields.engine.CorpseField;
import com.github.rzo1.bloodfields.engine.FixedTimestepDriver;
import com.github.rzo1.bloodfields.engine.GameLoop;
import com.github.rzo1.bloodfields.engine.GameState;
import com.github.rzo1.bloodfields.engine.SpatialHashGrid;
import com.github.rzo1.bloodfields.engine.World;
import com.github.rzo1.bloodfields.model.Army;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitType;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class GameApp extends Application {

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 800;
    static final int HUD_WIDTH = 220;
    private static final double TILE = 32.0;
    private static final double GRID_CELL = 64.0;
    private static final int ENEMY_BUDGET = 10000;
    private static final int VICTORY_DEBOUNCE_TICKS = 30;
    private static final double FRAME_CLAMP_SECONDS = 0.1;
    private static final Faction PLAYER_FACTION = Faction.RED;

    private enum Mode { CAMPAIGN, VERSUS, SKIRMISH }

    private GameState state;
    private GameLoop loop;
    private FixedTimestepDriver driver;
    private Camera camera;
    private Renderer renderer;
    private DeploymentOverlayRenderer overlay;
    private FpsOverlay fpsOverlay;
    private MainMenuRenderer menuPane;
    private ResultRenderer resultRenderer;
    private HandoverRenderer handoverRenderer;
    private SoundService sounds;
    private ParticleSystem particles;
    private DeathTracker deathTracker;
    private HitTracker hitTracker;
    private BloodyTiles bloodyTiles;
    private CrowFlock crows;
    private LimbField limbs;
    private StuckArrows stuckArrows;
    private RagdollOverlay ragdolls;
    private ScorchMarks scorchMarks;
    private BattleSmoke battleSmoke;
    private Vulture vulture;
    private CameraShake cameraShake;
    private java.util.Map<Long, com.github.rzo1.bloodfields.model.UnitType> killerByUnitId;
    private double simTime;
    private boolean victorySoundPlayed;
    private SpeedControl speed;
    private WeatherSystem weather = new WeatherSystem();
    private HelpOverlay helpOverlay = new HelpOverlay();
    private final LastDeploymentMemory lastDeployment = new LastDeploymentMemory();

    private Canvas canvas;
    private BorderPane root;
    private Hud hud;
    private DeploymentZone deploymentZone;
    private DeploymentController deploymentController;

    private int initialRedCount;
    private int initialBlueCount;
    private int victoryFrames;

    private List<Level> levels;
    private int currentLevelIndex;
    private boolean campaignComplete;
    private String earnedNextCode;

    private Mode mode = Mode.CAMPAIGN;
    private VersusFlow versus;

    private long lastNs;
    private long fpsFrames;
    private long fpsAccumNs;
    private double fps;

    @Override
    public void start(Stage stage) {
        canvas = new Canvas(WIDTH, HEIGHT);
        root = new BorderPane();
        root.setStyle(Theme.rootBackgroundStyle());

        renderer = new Renderer();
        overlay = new DeploymentOverlayRenderer();
        fpsOverlay = new FpsOverlay();
        resultRenderer = new ResultRenderer();
        handoverRenderer = new HandoverRenderer();
        sounds = new SoundService();
        particles = new ParticleSystem();
        deathTracker = new DeathTracker();
        hitTracker = new HitTracker();
        bloodyTiles = new BloodyTiles();
        crows = new CrowFlock();
        limbs = new LimbField();
        stuckArrows = new StuckArrows();
        ragdolls = new RagdollOverlay();
        scorchMarks = new ScorchMarks();
        battleSmoke = new BattleSmoke();
        battleSmoke.setWorldBounds(WIDTH, HEIGHT);
        vulture = new Vulture();
        cameraShake = new CameraShake();
        killerByUnitId = new java.util.HashMap<>();
        speed = new SpeedControl();
        simTime = 0.0;
        camera = new Camera();
        driver = new FixedTimestepDriver(1.0 / 60.0);
        levels = Levels.all();
        currentLevelIndex = 0;
        campaignComplete = false;

        menuPane = new MainMenuRenderer();
        menuPane.setOnCampaign(this::startCampaignFlow);
        menuPane.setOnSkirmish(this::startSkirmishFlow);
        menuPane.setOnVersus(this::startVersusFlow);
        menuPane.setOnLoadLevel(idx -> {
            currentLevelIndex = idx;
            campaignComplete = false;
            startCampaignFlow();
        });

        bootMainMenu();

        canvas.setFocusTraversable(true);
        canvas.addEventHandler(MouseEvent.MOUSE_PRESSED, this::onCanvasMousePressed);
        Scene scene = new Scene(root, WIDTH + HUD_WIDTH, HEIGHT);
        Theme.applyStylesheet(scene);
        scene.setOnKeyPressed(e -> {
            KeyCode code = e.getCode();
            if (code == KeyCode.H || code == KeyCode.SLASH) {
                helpOverlay.toggle();
                return;
            }
            if (helpOverlay.isVisible()) {
                if (code == KeyCode.ESCAPE) {
                    helpOverlay.setVisible(false);
                }
                return;
            }
            if (isVersusLikeMode() && versus != null
                    && state != null && state.phase == GameState.Phase.BATTLE
                    && versus.phase() == VersusFlow.VersusPhase.BATTLE
                    && handleReserveHotkey(code)) {
                return;
            }
            if (state != null && state.phase == GameState.Phase.BATTLE && handleSpeedHotkey(code)) {
                return;
            }
            if (isVersusLikeMode() && versus != null) {
                if (code == KeyCode.ESCAPE) {
                    cancelVersus();
                } else if (code == KeyCode.SPACE) {
                    if (versus.phase() == VersusFlow.VersusPhase.HANDOVER_TO_P2) {
                        progressVersus();
                    } else if (versus.phase() == VersusFlow.VersusPhase.HANDOVER_TO_BATTLE) {
                        progressVersus();
                    } else if (versus.phase() == VersusFlow.VersusPhase.HANDOVER_TO_BATTLE_RESUME) {
                        progressVersus();
                    } else if (state.phase == GameState.Phase.RESULT) {
                        bootMainMenu();
                    }
                }
            } else if (state.phase == GameState.Phase.MAIN_MENU) {
                if (code == KeyCode.ESCAPE) {
                    Platform.exit();
                } else if (code == KeyCode.ENTER || code == KeyCode.SPACE) {
                    startCampaignFlow();
                }
            } else if (state.phase == GameState.Phase.RESULT && code == KeyCode.SPACE) {
                startCampaignFlow();
            }
        });

        stage.setTitle("Bloodfield");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();

        GraphicsContext g = canvas.getGraphicsContext2D();
        new AnimationTimer() {
            @Override
            public void handle(long now) {
                tick(now, g);
            }
        }.start();
    }

    private boolean handleReserveHotkey(KeyCode code) {
        if (versus == null) return false;
        if (code == KeyCode.R) {
            if (canDeployReservesFor(state.red)) {
                versus.requestReservesP1();
                beginVersusReservesDeploy(state.red, "PLAYER 1 — RESERVES (RED)");
            }
            return true;
        }
        if (code == KeyCode.B) {
            if (canDeployReservesFor(state.blue)) {
                versus.requestReservesP2();
                beginVersusReservesDeploy(state.blue, "PLAYER 2 — RESERVES (BLUE)");
            }
            return true;
        }
        return false;
    }

    private boolean canDeployReservesFor(Army army) {
        if (army == null) return false;
        if (army.reserveBudget() <= 0) return false;
        return aliveCount(army) > 0;
    }

    private boolean handleSpeedHotkey(KeyCode code) {
        if (speed == null) return false;
        if (code == KeyCode.P) {
            speed.togglePause();
            updateSpeedLabel();
            return true;
        }
        if (code == KeyCode.DIGIT1) {
            speed.set(SpeedControl.Speed.HALF);
            updateSpeedLabel();
            return true;
        }
        if (code == KeyCode.DIGIT2) {
            speed.set(SpeedControl.Speed.NORMAL);
            updateSpeedLabel();
            return true;
        }
        if (code == KeyCode.DIGIT3) {
            speed.set(SpeedControl.Speed.DOUBLE);
            updateSpeedLabel();
            return true;
        }
        if (code == KeyCode.DIGIT4) {
            speed.set(SpeedControl.Speed.QUAD);
            updateSpeedLabel();
            return true;
        }
        return false;
    }

    private void updateSpeedLabel() {
        if (hud != null && speed != null) {
            hud.setSpeedLabel(speed.hudText());
        }
    }

    private void tick(long now, GraphicsContext g) {
        double frameSeconds = 0.0;
        if (lastNs != 0L) {
            frameSeconds = (now - lastNs) * 1e-9;
            if (frameSeconds < 0.0) frameSeconds = 0.0;
            if (frameSeconds > FRAME_CLAMP_SECONDS) frameSeconds = FRAME_CLAMP_SECONDS;
            fpsAccumNs += now - lastNs;
            fpsFrames++;
            if (fpsAccumNs >= 500_000_000L) {
                fps = fpsFrames * 1_000_000_000.0 / fpsAccumNs;
                fpsFrames = 0;
                fpsAccumNs = 0;
            }
        }
        lastNs = now;

        if (state.phase == GameState.Phase.MAIN_MENU) {
            return;
        }

        if (isVersusLikeMode() && versus != null) {
            VersusFlow.VersusPhase vp = versus.phase();
            if (vp == VersusFlow.VersusPhase.OPT_IN) {
                return;
            }
            if (vp == VersusFlow.VersusPhase.HANDOVER_TO_P2) {
                handoverRenderer.render(g, WIDTH, HEIGHT,
                        "PASS THE LAPTOP TO PLAYER 2",
                        "click or press SPACE when ready");
                return;
            }
            if (vp == VersusFlow.VersusPhase.HANDOVER_TO_BATTLE) {
                handoverRenderer.render(g, WIDTH, HEIGHT,
                        "BOTH ARMIES READY",
                        "click or press SPACE to start the battle");
                return;
            }
            if (vp == VersusFlow.VersusPhase.HANDOVER_TO_BATTLE_RESUME) {
                handoverRenderer.render(g, WIDTH, HEIGHT,
                        "RESERVES DEPLOYED",
                        "click or press SPACE to resume the battle");
                return;
            }
        }

        boolean helpPaused = helpOverlay.isVisible();
        double speedMul = (state.phase == GameState.Phase.BATTLE && speed != null) ? speed.multiplier() : 1.0;
        if (helpPaused) speedMul = 0.0;
        double scaledFrame = frameSeconds * speedMul;

        if (state.phase == GameState.Phase.BATTLE) {
            driver.advance(scaledFrame, () -> loop.step(driver.tickSeconds()));
            checkBattleEnd();
            simTime += scaledFrame;
            weather.update(scaledFrame);
        }

        particles.update(scaledFrame);
        crows.update(scaledFrame, bloodyTiles, state.world.tileSize, simTime);
        stuckArrows.update(scaledFrame);
        ragdolls.update(scaledFrame);
        scorchMarks.update(scaledFrame, particles, state.world.tileSize);
        vulture.update(scaledFrame, bloodyTiles, state.world.tileSize, simTime);
        cameraShake.update(scaledFrame);

        List<Unit> all = collectUnitsForRender();
        battleSmoke.update(scaledFrame, state.corpses != null ? state.corpses.size() : 0);
        renderer.corpseRenderer().update(scaledFrame, state.corpses);
        hitTracker.detectHits(all, particles, state.projectiles,
                stuckArrows, cameraShake, killerByUnitId, scorchMarks, state.world.tileSize);
        deathTracker.detectDeaths(all, particles, state.corpses, bloodyTiles, state.world.tileSize, simTime,
                limbs, ragdolls, renderer.corpseRenderer(), killerByUnitId, scorchMarks);
        int campaignLevel = mode == Mode.CAMPAIGN ? currentLevelIndex + 1 : 0;
        boolean redSky = mode == Mode.CAMPAIGN && currentLevelIndex >= 4;
        renderer.setAuraContext(state, simTime);
        renderer.setStructureField(state.structures);
        renderer.render(g, WIDTH, HEIGHT, state.world.terrain, state.world.tileSize,
                all, state.projectiles, particles.pools(),
                bloodyTiles, state.corpses, crows, camera, campaignLevel, redSky,
                limbs, stuckArrows, ragdolls, scorchMarks, battleSmoke, vulture, cameraShake);
        particles.render(g, camera);

        if (state.phase == GameState.Phase.DEPLOYMENT && deploymentController != null) {
            overlay.render(g, deploymentController, deploymentZone, camera);
        }

        if (state.phase == GameState.Phase.BATTLE || state.phase == GameState.Phase.RESULT) {
            if (hud != null) {
                hud.update(redCasualties(), blueCasualties());
                if (speed != null) {
                    hud.setSpeedLabel(speed.hudText());
                }
            }
        }

        weather.render(g, WIDTH, HEIGHT, camera);

        fpsOverlay.render(g, fps);

        if (state.phase == GameState.Phase.RESULT) {
            String code = (mode == Mode.CAMPAIGN && state.winner == PLAYER_FACTION && !campaignComplete)
                    ? earnedNextCode
                    : null;
            resultRenderer.render(g, WIDTH, HEIGHT, state.winner,
                    redCasualties(), initialRedCount,
                    blueCasualties(), initialBlueCount, code);
            if (mode == Mode.CAMPAIGN && campaignComplete && state.winner == PLAYER_FACTION) {
                renderCampaignBanner(g);
            }
        }

        if (state.phase == GameState.Phase.DEPLOYMENT || state.phase == GameState.Phase.BATTLE) {
            helpOverlay.renderHint(g, WIDTH, HEIGHT);
        }
        helpOverlay.render(g, WIDTH, HEIGHT);
    }

    private void renderCampaignBanner(GraphicsContext g) {
        g.save();
        g.setFill(javafx.scene.paint.Color.rgb(0, 0, 0, 0.7));
        g.fillRect(0, HEIGHT / 2.0 + 100, WIDTH, 60);
        g.setFill(javafx.scene.paint.Color.web(Theme.TEXT_ACCENT));
        g.setFont(javafx.scene.text.Font.font("Georgia", javafx.scene.text.FontWeight.BOLD, 28));
        g.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
        g.fillText("CAMPAIGN COMPLETE", WIDTH / 2.0, HEIGHT / 2.0 + 140);
        g.restore();
    }

    private void onCanvasMousePressed(MouseEvent e) {
        if (state.phase == GameState.Phase.MAIN_MENU) {
            return;
        }
        if (isVersusLikeMode() && versus != null) {
            VersusFlow.VersusPhase vp = versus.phase();
            if (vp == VersusFlow.VersusPhase.HANDOVER_TO_P2 || vp == VersusFlow.VersusPhase.HANDOVER_TO_BATTLE
                    || vp == VersusFlow.VersusPhase.HANDOVER_TO_BATTLE_RESUME) {
                if (e.getButton() == MouseButton.PRIMARY) {
                    progressVersus();
                }
                return;
            }
            if (state.phase == GameState.Phase.RESULT && e.getButton() == MouseButton.PRIMARY) {
                bootMainMenu();
                return;
            }
        }
        if (state.phase == GameState.Phase.RESULT && e.getButton() == MouseButton.PRIMARY) {
            startCampaignFlow();
        }
    }

    private void bootMainMenu() {
        World world = World.battlefield(WIDTH, HEIGHT, TILE);
        SpatialHashGrid grid = new SpatialHashGrid(WIDTH, HEIGHT, GRID_CELL);
        Army red = new Army(Faction.RED, 0);
        Army blue = new Army(Faction.BLUE, 0);
        state = new GameState(world, red, blue, grid);
        state.phase = GameState.Phase.MAIN_MENU;
        if (hud != null) {
            root.setLeft(null);
            hud = null;
        }
        deploymentController = null;
        deploymentZone = null;
        versus = null;
        mode = Mode.CAMPAIGN;
        root.setCenter(menuPane);
    }

    private void startCampaignFlow() {
        mode = Mode.CAMPAIGN;
        versus = null;
        root.setCenter(canvas);
        startNewMatch();
    }

    private void startNewMatch() {
        Level level = levels.get(currentLevelIndex);
        World world = buildCampaignWorld(level);
        SpatialHashGrid grid = new SpatialHashGrid(WIDTH, HEIGHT, GRID_CELL);
        Army red = new Army(Faction.RED, level.playerBudget());
        Army blue = new Army(Faction.BLUE, ENEMY_BUDGET);
        state = new GameState(world, red, blue, grid);
        state.phase = GameState.Phase.DEPLOYMENT;
        loop = new GameLoop(state, new UnitAI());
        populateStructuresForCampaignLevel(level);

        Army playerArmy = state.armyOf(PLAYER_FACTION);
        Army enemyArmy = state.armyOf(PLAYER_FACTION.opponent());
        level.spawner().spawn(enemyArmy, state, WIDTH, HEIGHT);
        for (java.util.Map.Entry<UnitType, Integer> e : level.playerCaps().entrySet()) {
            playerArmy.setCap(e.getKey(), e.getValue());
        }

        deploymentZone = new DeploymentZone(0, HEIGHT / 2.0, WIDTH, HEIGHT);
        deploymentController = new DeploymentController(canvas, playerArmy, deploymentZone, TILE,
                () -> state.nextUnitId++, world);

        hud = new Hud(playerArmy, deploymentController, this::onStartBattle);
        hud.setLevelInfo(level.number(), levels.size(), level.name());
        hud.setRestoreLast(lastDeployment, () -> restoreLastDeployment(playerArmy));
        root.setLeft(hud);

        initialRedCount = 0;
        initialBlueCount = 0;
        victoryFrames = 0;
        victorySoundPlayed = false;
        if (particles != null) {
            particles.particles().clear();
            particles.pools().clear();
        }
        if (deathTracker != null) {
            deathTracker.clear();
        }
        if (hitTracker != null) {
            hitTracker.clear();
        }
        if (state != null && state.corpses != null) {
            state.corpses.clear();
        }
        if (bloodyTiles != null) {
            bloodyTiles.clear();
        }
        if (crows != null) {
            crows.clear();
        }
        clearGoreOverlays();
        simTime = 0.0;
        driver.reset();
    }

    private void restoreLastDeployment(Army army) {
        if (army == null || !lastDeployment.hasSnapshot()) return;
        java.util.List<Unit> existing = new java.util.ArrayList<>(army.units());
        for (Unit u : existing) army.remove(u);
        for (LastDeploymentMemory.Slot slot : lastDeployment.slots()) {
            if (army.remainingBudget() < slot.type.cost()) break;
            Unit u = new Unit(state.nextUnitId++, slot.type, army.faction(),
                    slot.x, slot.y, army.hpMultiplier());
            army.add(u);
        }
        if (lastDeployment.heroSkill() != null) {
            army.setHeroSkill(lastDeployment.heroSkill());
        }
    }

    private void onStartBattle() {
        if (deploymentController == null || !deploymentController.canStart()) {
            return;
        }
        if (isVersusLikeMode() && versus != null) {
            if (versus.phase() == VersusFlow.VersusPhase.DEPLOY_P1) {
                versus.endP1Turn();
                tearDownDeployment();
                if (mode == Mode.SKIRMISH) {
                    deploySkirmishBot();
                }
                return;
            }
            if (versus.phase() == VersusFlow.VersusPhase.DEPLOY_P2) {
                versus.endP2Turn();
                tearDownDeployment();
                return;
            }
        }
        beginBattle();
    }

    private void beginBattle() {
        state.phase = GameState.Phase.BATTLE;
        if (mode == Mode.CAMPAIGN) {
            lastDeployment.snapshot(state.armyOf(PLAYER_FACTION));
        }
        initialRedCount = aliveCount(state.red);
        initialBlueCount = aliveCount(state.blue);
        victoryFrames = 0;
        if (deploymentController != null) {
            deploymentController.setActive(false);
            deploymentController = null;
        }
        if (speed != null) {
            speed.set(SpeedControl.Speed.NORMAL);
        }
        if (hud != null) {
            hud.enterBattleMode();
            if (speed != null) {
                hud.setSpeedLabel(speed.hudText());
            }
        }
        applyBattleWeather();
        driver.reset();
    }

    private void applyBattleWeather() {
        Weather w;
        if ((mode == Mode.VERSUS || mode == Mode.SKIRMISH) && versus != null) {
            w = versus.selectedWeather();
        } else if (mode == Mode.CAMPAIGN && levels != null
                && currentLevelIndex >= 0 && currentLevelIndex < levels.size()) {
            w = levels.get(currentLevelIndex).weather();
        } else {
            w = Weather.CLEAR;
        }
        if (w == null) w = Weather.CLEAR;
        weather.set(w);
        AiTuning.weatherRangedRangeMult = w.rangedRangeMult();
        AiTuning.weatherSpeedMult = w.speedMult();
    }

    private void tearDownDeployment() {
        if (deploymentController != null) {
            deploymentController.setActive(false);
            deploymentController = null;
        }
        if (hud != null) {
            root.setLeft(null);
            hud = null;
        }
        deploymentZone = null;
    }

    private void checkBattleEnd() {
        Faction survivor = state.checkVictory();
        if (survivor != null) {
            victoryFrames++;
            if (victoryFrames >= VICTORY_DEBOUNCE_TICKS) {
                state.winner = survivor;
                state.phase = GameState.Phase.RESULT;
                if (isVersusLikeMode() && versus != null) {
                    versus.battleEnded();
                }
                if (!victorySoundPlayed && sounds != null) {
                    sounds.playVictory();
                    victorySoundPlayed = true;
                }
                if (mode == Mode.CAMPAIGN) {
                    handleCampaignWinLoss(survivor);
                }
            }
        } else {
            victoryFrames = 0;
        }
    }

    private void handleCampaignWinLoss(Faction survivor) {
        if (survivor == PLAYER_FACTION) {
            boolean wasFinal = LevelProgression.isFinal(currentLevelIndex, levels.size());
            int nextIdx = LevelProgression.onWin(currentLevelIndex, levels.size());
            if (wasFinal) {
                campaignComplete = true;
                earnedNextCode = null;
            } else {
                earnedNextCode = LevelCodes.codeFor(nextIdx);
            }
            currentLevelIndex = nextIdx;
        } else {
            earnedNextCode = null;
            currentLevelIndex = LevelProgression.onLoss(currentLevelIndex);
        }
    }

    private void startVersusFlow() {
        mode = Mode.VERSUS;
        versus = new VersusFlow();
        if (particles != null) {
            particles.particles().clear();
            particles.pools().clear();
        }
        if (deathTracker != null) {
            deathTracker.clear();
        }
        if (hitTracker != null) {
            hitTracker.clear();
        }
        if (state != null && state.corpses != null) {
            state.corpses.clear();
        }
        if (bloodyTiles != null) {
            bloodyTiles.clear();
        }
        if (crows != null) {
            crows.clear();
        }
        clearGoreOverlays();
        simTime = 0.0;
        driver.reset();
        showOptInPane();
    }

    private boolean isVersusLikeMode() {
        return mode == Mode.VERSUS || mode == Mode.SKIRMISH;
    }

    private void startSkirmishFlow() {
        mode = Mode.SKIRMISH;
        versus = new VersusFlow();
        versus.setSkirmish(true);
        if (particles != null) {
            particles.particles().clear();
            particles.pools().clear();
        }
        if (deathTracker != null) {
            deathTracker.clear();
        }
        if (hitTracker != null) {
            hitTracker.clear();
        }
        if (state != null && state.corpses != null) {
            state.corpses.clear();
        }
        if (bloodyTiles != null) {
            bloodyTiles.clear();
        }
        if (crows != null) {
            crows.clear();
        }
        clearGoreOverlays();
        simTime = 0.0;
        driver.reset();
        showSkirmishOptInPane();
    }

    private void initializeSkirmishState() {
        initializeVersusState();
    }

    private void deploySkirmishBot() {
        if (versus == null || state == null) return;
        Army botArmy = state.blue;
        int totalBudget = versus.budget();
        int initialBudget = versus.initialBudgetFor(totalBudget);
        new com.github.rzo1.bloodfields.ai.SkirmishBot()
                .deployArmy(botArmy, state, WIDTH, HEIGHT, initialBudget, versus.dragonsAllowed());
    }

    private void initializeVersusState() {
        int cols = (int) Math.ceil((double) WIDTH / TILE);
        int rows = (int) Math.ceil((double) HEIGHT / TILE);
        World world = new World(WIDTH, HEIGHT, TILE,
                versus.selectedMap().generator().generate(cols, rows));
        SpatialHashGrid grid = new SpatialHashGrid(WIDTH, HEIGHT, GRID_CELL);
        int totalBudget = versus.budget();
        int initialBudget = versus.initialBudgetFor(totalBudget);
        int reserveBudget = versus.reserveBudgetFor(totalBudget);
        Army red = new Army(Faction.RED, initialBudget);
        red.setReserveBudget(reserveBudget);
        red.setHpMultiplier(versus.p1HpMultiplier());
        versus.applyCapsTo(red);
        Army blue = new Army(Faction.BLUE, initialBudget);
        blue.setReserveBudget(reserveBudget);
        blue.setHpMultiplier(versus.isSkirmish() ? 1.0 : versus.p2HpMultiplier());
        versus.applyCapsTo(blue);
        state = new GameState(world, red, blue, grid);
        state.phase = GameState.Phase.DEPLOYMENT;
        loop = new GameLoop(state, new UnitAI());
        populateStructuresForCurrentMap();
    }

    private void populateStructuresForCurrentMap() {
        if (state == null || state.structures == null) return;
        if (versus == null || versus.selectedMap() == null) return;
        String mapId = versus.selectedMap().id();
        long[] nextStructId = {-1L};
        java.util.function.Supplier<Long> idSource = () -> nextStructId[0]--;
        for (com.github.rzo1.bloodfields.engine.Structure s :
                MapStructures.forPreset(mapId, WIDTH, HEIGHT, idSource)) {
            state.structures.add(s);
        }
    }

    private static World buildCampaignWorld(Level level) {
        MapPreset preset = level == null ? null : Maps.byId(level.mapId());
        if (preset == null) {
            return World.battlefield(WIDTH, HEIGHT, TILE);
        }
        int cols = (int) Math.ceil((double) WIDTH / TILE);
        int rows = (int) Math.ceil((double) HEIGHT / TILE);
        return new World(WIDTH, HEIGHT, TILE, preset.generator().generate(cols, rows));
    }

    private void populateStructuresForCampaignLevel(Level level) {
        if (state == null || state.structures == null || level == null) return;
        long[] nextStructId = {-1L};
        java.util.function.Supplier<Long> idSource = () -> nextStructId[0]--;
        for (com.github.rzo1.bloodfields.engine.Structure s :
                MapStructures.forPreset(level.mapId(), WIDTH, HEIGHT, idSource)) {
            state.structures.add(s);
        }
    }

    private static final double SECTION_GAP = 16;
    private static final double ROW_GAP = 8;
    private static final double SLIDER_WIDTH = 420;
    private static final double VALUE_LABEL_WIDTH = 70;

    private void showOptInPane() {
        VBox content = new VBox(SECTION_GAP);
        content.setAlignment(Pos.TOP_CENTER);
        content.setPadding(new Insets(24, 30, 20, 30));
        content.setFillWidth(true);

        Label title = new Label("VERSUS");
        title.setFont(Font.font("Georgia", FontWeight.BOLD, 44));
        title.setStyle(Theme.menuTitleStyle());

        VBox mapSection = buildMapSection();
        VBox armySection = buildArmySection();
        VBox weatherSection = buildWeatherSection();
        VBox dragonSection = buildDragonSection(false);
        VBox handicapSection = buildHandicapSection(false);
        VBox capSection = buildCapSection();

        Label esc = new Label("ESC — back to menu");
        esc.setStyle(Theme.labelDimStyle());

        content.getChildren().addAll(
                title,
                mapSection,
                optInDivider(),
                armySection,
                optInDivider(),
                weatherSection,
                optInDivider(),
                dragonSection,
                optInDivider(),
                handicapSection,
                optInDivider(),
                capSection,
                esc
        );

        Button continueBtn = optInActionButton("Continue");
        continueBtn.setOnAction(e -> {
            initializeVersusState();
            versus.optInComplete();
            beginVersusDeployP1();
        });

        root.setCenter(buildOptInLayout(content, continueBtn));
    }

    private void showSkirmishOptInPane() {
        VBox content = new VBox(SECTION_GAP);
        content.setAlignment(Pos.TOP_CENTER);
        content.setPadding(new Insets(24, 30, 20, 30));
        content.setFillWidth(true);

        Label title = new Label("SKIRMISH");
        title.setFont(Font.font("Georgia", FontWeight.BOLD, 44));
        title.setStyle(Theme.menuTitleStyle());

        Label subtitle = new Label("(vs Bot)");
        subtitle.setStyle(Theme.labelDimStyle());

        VBox titleBox = new VBox(2, title, subtitle);
        titleBox.setAlignment(Pos.CENTER);

        VBox mapSection = buildMapSection();
        VBox armySection = buildArmySection();
        VBox dragonSection = buildDragonSection(true);
        VBox handicapSection = buildHandicapSection(true);
        VBox capSection = buildCapSection();

        Label esc = new Label("ESC — back to menu");
        esc.setStyle(Theme.labelDimStyle());

        content.getChildren().addAll(
                titleBox,
                mapSection,
                optInDivider(),
                armySection,
                optInDivider(),
                dragonSection,
                optInDivider(),
                handicapSection,
                optInDivider(),
                capSection,
                esc
        );

        Button startBtn = optInActionButton("Start Skirmish");
        startBtn.setOnAction(e -> {
            initializeSkirmishState();
            versus.optInComplete();
            beginVersusDeployP1();
        });

        root.setCenter(buildOptInLayout(content, startBtn));
    }

    private static Button optInActionButton(String text) {
        Button b = new Button(text);
        b.setFont(Font.font("Georgia", FontWeight.BOLD, 18));
        b.setPrefWidth(260);
        b.setPrefHeight(40);
        b.getStyleClass().add(Theme.BUTTON_CLASS);
        return b;
    }

    private static Region optInDivider() {
        Region r = new Region();
        r.setMinHeight(1);
        r.setPrefHeight(1);
        r.setMaxHeight(1);
        VBox.setMargin(r, new Insets(2, 60, 2, 60));
        r.setStyle(Theme.dividerStyle());
        return r;
    }

    private BorderPane buildOptInLayout(VBox content, Button actionBtn) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        HBox actionRow = new HBox(actionBtn);
        actionRow.setAlignment(Pos.CENTER);
        actionRow.setPadding(new Insets(14, 20, 18, 20));
        actionRow.setStyle(Theme.actionRowStyle());

        BorderPane wrap = new BorderPane();
        wrap.setStyle(Theme.optInWrapStyle());
        wrap.setCenter(scroll);
        wrap.setBottom(actionRow);
        return wrap;
    }

    private static Label sectionHeader(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("Georgia", FontWeight.BOLD, 16));
        l.setStyle(Theme.labelTitleStyle());
        return l;
    }

    private static Label sliderValueLabel(String text) {
        Label l = new Label(text);
        l.setStyle(Theme.labelAccentStyle() + " -fx-font-size: 16px;");
        l.setMinWidth(VALUE_LABEL_WIDTH);
        l.setAlignment(Pos.CENTER_LEFT);
        return l;
    }

    private VBox buildMapSection() {
        Label mapLabel = sectionHeader("Map");

        HBox mapCards = buildMapCards();
        mapCards.setAlignment(Pos.CENTER_LEFT);

        ScrollPane mapScroll = new ScrollPane(mapCards);
        mapScroll.setFitToHeight(true);
        mapScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        mapScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        mapScroll.setPrefViewportWidth(900);
        mapScroll.setPannable(true);
        mapScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        VBox section = new VBox(ROW_GAP, mapLabel, mapScroll);
        section.setAlignment(Pos.CENTER);
        return section;
    }

    private VBox buildArmySection() {
        Label budgetLabel = sectionHeader("Points per army");

        Slider budgetSlider = new Slider(VersusFlow.MIN_BUDGET, VersusFlow.MAX_BUDGET, versus.budget());
        budgetSlider.setBlockIncrement(VersusFlow.BUDGET_STEP);
        budgetSlider.setMajorTickUnit(2000);
        budgetSlider.setMinorTickCount(3);
        budgetSlider.setSnapToTicks(true);
        budgetSlider.setShowTickMarks(true);
        budgetSlider.setShowTickLabels(true);
        budgetSlider.setPrefWidth(SLIDER_WIDTH);

        Label budgetValue = sliderValueLabel(String.valueOf(versus.budget()));

        budgetSlider.valueProperty().addListener((obs, o, n) -> {
            versus.setBudget(n.intValue());
            budgetValue.setText(String.valueOf(versus.budget()));
        });

        HBox budgetRow = new HBox(12, budgetSlider, budgetValue);
        budgetRow.setAlignment(Pos.CENTER);

        Label reserveLabel = sectionHeader("Reserve %");

        Slider reserveSlider = new Slider(VersusFlow.MIN_RESERVE, VersusFlow.MAX_RESERVE, versus.reservePercent());
        reserveSlider.setBlockIncrement(VersusFlow.RESERVE_STEP);
        reserveSlider.setMajorTickUnit(VersusFlow.RESERVE_STEP);
        reserveSlider.setMinorTickCount(0);
        reserveSlider.setSnapToTicks(true);
        reserveSlider.setShowTickMarks(true);
        reserveSlider.setShowTickLabels(true);
        reserveSlider.setPrefWidth(SLIDER_WIDTH);

        Label reserveValue = sliderValueLabel(versus.reservePercent() + "%");

        reserveSlider.valueProperty().addListener((obs, o, n) -> {
            versus.setReservePercent(n.intValue());
            reserveValue.setText(versus.reservePercent() + "%");
        });

        HBox reserveRow = new HBox(12, reserveSlider, reserveValue);
        reserveRow.setAlignment(Pos.CENTER);

        Label reserveHint = new Label("Held back as reserves; deploy mid-battle with R (RED) / B (BLUE).");
        reserveHint.setStyle(Theme.labelDimStyle());

        VBox section = new VBox(ROW_GAP, budgetLabel, budgetRow, reserveLabel, reserveRow, reserveHint);
        section.setAlignment(Pos.CENTER);
        return section;
    }

    private VBox buildWeatherSection() {
        Label weatherLabel = sectionHeader("Weather");

        ComboBox<Weather> weatherBox = new ComboBox<>();
        weatherBox.getItems().setAll(Weather.values());
        weatherBox.setValue(versus.selectedWeather());
        weatherBox.setPrefWidth(220);
        weatherBox.setCellFactory(list -> new javafx.scene.control.ListCell<Weather>() {
            @Override
            protected void updateItem(Weather item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.displayName());
            }
        });
        weatherBox.setButtonCell(new javafx.scene.control.ListCell<Weather>() {
            @Override
            protected void updateItem(Weather item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.displayName());
            }
        });

        Label weatherHint = new Label(versus.selectedWeather().description());
        weatherHint.setStyle(Theme.labelDimStyle());
        weatherHint.setWrapText(true);
        weatherHint.setMaxWidth(420);

        weatherBox.valueProperty().addListener((obs, o, n) -> {
            if (n != null) {
                versus.setSelectedWeather(n);
                weatherHint.setText(n.description());
            }
        });

        HBox weatherRow = new HBox(12, weatherBox, weatherHint);
        weatherRow.setAlignment(Pos.CENTER);

        VBox section = new VBox(ROW_GAP, weatherLabel, weatherRow);
        section.setAlignment(Pos.CENTER);
        return section;
    }

    private VBox buildDragonSection(boolean skirmish) {
        Label dragonLabel = sectionHeader("Allow Dragons?");

        Label hint = new Label(skirmish
                ? "If checked, you may deploy a Dragon. The bot may field one too."
                : "Both players must check the box for dragons to be enabled.");
        hint.setStyle(Theme.labelDimStyle());

        HBox boxes = new HBox(40);
        boxes.setAlignment(Pos.CENTER);

        if (skirmish) {
            CheckBox p1 = new CheckBox("Allow Dragons");
            p1.setStyle("-fx-text-fill: " + Theme.TEXT_ACCENT + "; -fx-font-family: 'Georgia','Serif'; -fx-font-size: 15px;");
            p1.selectedProperty().addListener((obs, o, n) -> versus.setP1OptIn(n));
            boxes.getChildren().add(p1);
        } else {
            CheckBox p1 = new CheckBox("Player 1 (RED)");
            p1.setStyle("-fx-text-fill: " + Theme.TEXT_ACCENT + "; -fx-font-family: 'Georgia','Serif'; -fx-font-size: 15px;");
            p1.selectedProperty().addListener((obs, o, n) -> versus.setP1OptIn(n));

            CheckBox p2 = new CheckBox("Player 2 (BLUE)");
            p2.setStyle("-fx-text-fill: #6688cc; -fx-font-family: 'Georgia','Serif'; -fx-font-size: 15px;");
            p2.selectedProperty().addListener((obs, o, n) -> versus.setP2OptIn(n));

            boxes.getChildren().addAll(p1, p2);
        }

        VBox section = new VBox(ROW_GAP, dragonLabel, hint, boxes);
        section.setAlignment(Pos.CENTER);
        return section;
    }

    private VBox buildHandicapSection(boolean skirmish) {
        Label header = sectionHeader("Handicap (HP multiplier)");

        Label hint = new Label(skirmish
                ? "Boost your army's HP if you want a head start. Bot is fixed at 0%."
                : "Boost a player's HP to balance skill differences. 0% = baseline, 200% = 3× HP.");
        hint.setStyle(Theme.labelDimStyle());

        VBox section = new VBox(ROW_GAP, header, hint);
        section.setAlignment(Pos.CENTER);

        section.getChildren().add(buildHandicapRow(
                skirmish ? "Your handicap" : "Player 1 (RED) handicap",
                Theme.TEXT_ACCENT,
                versus.p1HandicapPercent(),
                v -> versus.setP1HandicapPercent(v),
                () -> versus.p1HandicapPercent()));

        if (!skirmish) {
            section.getChildren().add(buildHandicapRow(
                    "Player 2 (BLUE) handicap",
                    "#6688cc",
                    versus.p2HandicapPercent(),
                    v -> versus.setP2HandicapPercent(v),
                    () -> versus.p2HandicapPercent()));
        }

        return section;
    }

    private VBox buildCapSection() {
        Label header = sectionHeader("Max per unit type");

        Label hint = new Label("Caps how many of any one unit type each side may field. "
                + "Unique units (General) are unaffected.");
        hint.setStyle(Theme.labelDimStyle());
        hint.setWrapText(true);
        hint.setMaxWidth(560);

        ComboBox<Integer> capBox = new ComboBox<>();
        for (int v : VersusFlow.CAP_OPTIONS) {
            capBox.getItems().add(v);
        }
        capBox.setValue(versus.perTypeCap());
        capBox.setPrefWidth(220);
        capBox.setCellFactory(list -> new javafx.scene.control.ListCell<Integer>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : capLabel(item));
            }
        });
        capBox.setButtonCell(new javafx.scene.control.ListCell<Integer>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : capLabel(item));
            }
        });
        capBox.valueProperty().addListener((obs, o, n) -> {
            if (n != null) versus.setPerTypeCap(n);
        });

        HBox row = new HBox(12, capBox);
        row.setAlignment(Pos.CENTER);

        VBox section = new VBox(ROW_GAP, header, hint, row);
        section.setAlignment(Pos.CENTER);
        return section;
    }

    private static String capLabel(int v) {
        return v == VersusFlow.CAP_UNLIMITED ? "Unlimited" : ("Max " + v);
    }

    private HBox buildHandicapRow(String labelText, String color, int initial,
                                  java.util.function.IntConsumer setter,
                                  java.util.function.IntSupplier getter) {
        Label nameLabel = new Label(labelText);
        nameLabel.setStyle("-fx-text-fill: " + color + "; -fx-font-family: 'Georgia','Serif'; -fx-font-size: 14px;");
        nameLabel.setMinWidth(220);

        Slider slider = new Slider(VersusFlow.MIN_HANDICAP, VersusFlow.MAX_HANDICAP, initial);
        slider.setBlockIncrement(VersusFlow.HANDICAP_STEP);
        slider.setMajorTickUnit(50);
        slider.setMinorTickCount(1);
        slider.setSnapToTicks(true);
        slider.setShowTickMarks(true);
        slider.setShowTickLabels(true);
        slider.setPrefWidth(SLIDER_WIDTH);

        Label value = sliderValueLabel(initial + "%");
        value.setStyle(Theme.labelAccentStyle() + " -fx-font-size: 14px;");

        slider.valueProperty().addListener((obs, o, n) -> {
            setter.accept(n.intValue());
            value.setText(getter.getAsInt() + "%");
        });

        HBox row = new HBox(12, nameLabel, slider, value);
        row.setAlignment(Pos.CENTER);
        return row;
    }

    private HBox buildMapCards() {
        HBox row = new HBox(12);
        MapPreviewRenderer previewRenderer = new MapPreviewRenderer();
        List<VBox> cards = new ArrayList<>();
        for (MapPreset preset : Maps.all()) {
            Canvas preview = new Canvas(160, 100);
            previewRenderer.render(preview.getGraphicsContext2D(), preview.getWidth(), preview.getHeight(), preset);

            Label name = new Label(preset.name());
            name.setStyle("-fx-text-fill: " + Theme.TEXT_PRIMARY + "; -fx-font-family: 'Georgia','Serif'; -fx-font-size: 12px; -fx-font-weight: bold;");

            Label desc = new Label(preset.description());
            desc.setWrapText(true);
            desc.setMaxWidth(160);
            desc.setStyle("-fx-text-fill: " + Theme.TEXT_DIM + "; -fx-font-family: 'Georgia','Serif'; -fx-font-size: 10px;");

            VBox card = new VBox(2, preview, name, desc);
            card.setAlignment(Pos.CENTER);
            card.setPadding(new Insets(4));
            card.setStyle(Theme.cardStyle(preset.equals(versus.selectedMap())));
            card.setOnMouseClicked(e -> {
                versus.setSelectedMap(preset);
                for (VBox other : cards) {
                    MapPreset otherPreset = (MapPreset) other.getProperties().get("preset");
                    other.setStyle(Theme.cardStyle(otherPreset.equals(versus.selectedMap())));
                }
            });
            card.getProperties().put("preset", preset);
            cards.add(card);
            row.getChildren().add(card);
        }
        return row;
    }

    private void beginVersusDeployP1() {
        root.setCenter(canvas);
        deploymentZone = new DeploymentZone(0, HEIGHT / 2.0, WIDTH, HEIGHT);
        startVersusDeployment(state.red, "PLAYER 1 — DEPLOYMENT (RED)", "End Turn");
    }

    private void beginVersusDeployP2() {
        deploymentZone = new DeploymentZone(0, 0, WIDTH, HEIGHT / 2.0);
        startVersusDeployment(state.blue, "PLAYER 2 — DEPLOYMENT (BLUE)", "Start Battle");
    }

    private void startVersusDeployment(Army army, String label, String startText) {
        Set<UnitType> allowed = allowedTypesForVersus();
        deploymentController = new DeploymentController(canvas, army, deploymentZone, TILE,
                () -> state.nextUnitId++, state.world);
        deploymentController.setStructures(state.structures);
        hud = new Hud(army, deploymentController, this::onStartBattle, allowed);
        hud.setTitleText("DEPLOYMENT");
        hud.setPlayerLabel(label);
        hud.setStartButtonText(startText);
        root.setLeft(hud);
        if (particles != null) {
            particles.particles().clear();
            particles.pools().clear();
        }
        if (deathTracker != null) {
            deathTracker.clear();
        }
        if (hitTracker != null) {
            hitTracker.clear();
        }
        if (state != null && state.corpses != null) {
            state.corpses.clear();
        }
        if (bloodyTiles != null) {
            bloodyTiles.clear();
        }
        if (crows != null) {
            crows.clear();
        }
        clearGoreOverlays();
        simTime = 0.0;
        driver.reset();
    }

    private Set<UnitType> allowedTypesForVersus() {
        EnumSet<UnitType> s = EnumSet.noneOf(UnitType.class);
        for (UnitType t : UnitType.values()) {
            if (t == UnitType.DRAGON) {
                if (versus != null && versus.dragonsAllowed()) {
                    s.add(t);
                }
            } else if (t.playerSelectable()) {
                s.add(t);
            }
        }
        return s;
    }

    private void progressVersus() {
        if (versus == null) return;
        switch (versus.phase()) {
            case HANDOVER_TO_P2:
                versus.handoverToP2Done();
                beginVersusDeployP2();
                break;
            case HANDOVER_TO_BATTLE:
                versus.handoverToBattleDone();
                beginBattle();
                break;
            case HANDOVER_TO_BATTLE_RESUME:
                versus.handoverToBattleResumeDone();
                resumeBattleAfterReserves();
                break;
            default:
                break;
        }
    }

    private void beginVersusReservesDeploy(Army army, String label) {
        DeploymentZone zone = army.faction() == Faction.RED
                ? new DeploymentZone(0, HEIGHT / 2.0, WIDTH, HEIGHT)
                : new DeploymentZone(0, 0, WIDTH, HEIGHT / 2.0);
        deploymentZone = zone;
        army.activateReserves();
        state.phase = GameState.Phase.DEPLOYMENT;
        Set<UnitType> allowed = allowedTypesForVersus();
        deploymentController = new DeploymentController(canvas, army, deploymentZone, TILE,
                () -> state.nextUnitId++, state.world);
        deploymentController.setStructures(state.structures);
        hud = new Hud(army, deploymentController, this::onReservesDone, allowed);
        hud.setTitleText("RESERVES");
        hud.setPlayerLabel(label);
        hud.setStartButtonText("Deploy");
        root.setLeft(hud);
    }

    private void onReservesDone() {
        if (deploymentController == null || !deploymentController.canStart()) {
            return;
        }
        if (versus == null) return;
        VersusFlow.VersusPhase vp = versus.phase();
        if (vp != VersusFlow.VersusPhase.DEPLOY_RESERVES_P1
                && vp != VersusFlow.VersusPhase.DEPLOY_RESERVES_P2) {
            return;
        }
        versus.endReservesTurn();
        tearDownDeployment();
    }

    private void resumeBattleAfterReserves() {
        state.phase = GameState.Phase.BATTLE;
        driver.reset();
    }

    private void cancelVersus() {
        if (versus != null) {
            versus.cancel();
        }
        bootMainMenu();
    }

    private List<Unit> collectUnitsForRender() {
        if (isVersusLikeMode() && versus != null) {
            VersusFlow.VersusPhase vp = versus.phase();
            if (vp == VersusFlow.VersusPhase.DEPLOY_P1) {
                return excludeGarrisoned(state.red.units());
            }
            if (vp == VersusFlow.VersusPhase.DEPLOY_P2) {
                return excludeGarrisoned(state.blue.units());
            }
        }
        List<Unit> all = new ArrayList<>(state.red.units().size() + state.blue.units().size());
        for (Unit u : state.red.units()) {
            if (u != null && !u.garrisoned) all.add(u);
        }
        for (Unit u : state.blue.units()) {
            if (u != null && !u.garrisoned) all.add(u);
        }
        return all;
    }

    private static List<Unit> excludeGarrisoned(List<Unit> src) {
        List<Unit> out = new ArrayList<>(src.size());
        for (Unit u : src) {
            if (u != null && !u.garrisoned) out.add(u);
        }
        return out;
    }

    private int redCasualties() {
        return Math.max(0, initialRedCount - aliveCount(state.red));
    }

    private int blueCasualties() {
        return Math.max(0, initialBlueCount - aliveCount(state.blue));
    }

    private static int aliveCount(Army army) {
        int n = 0;
        for (Unit u : army.units()) {
            if (u.isAlive()) n++;
        }
        return n;
    }

    private void clearGoreOverlays() {
        if (limbs != null) limbs.clear();
        if (stuckArrows != null) stuckArrows.clear();
        if (ragdolls != null) ragdolls.clear();
        if (scorchMarks != null) scorchMarks.clear();
        if (battleSmoke != null) battleSmoke.clear();
        if (vulture != null) vulture.clear();
        if (cameraShake != null) cameraShake.clear();
        if (killerByUnitId != null) killerByUnitId.clear();
        if (renderer != null && renderer.corpseRenderer() != null) {
            renderer.corpseRenderer().clear();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
