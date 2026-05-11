package com.github.rzo1.bloodfields.ui;

import com.github.rzo1.bloodfields.engine.CorpseField;
import com.github.rzo1.bloodfields.engine.GameState;
import com.github.rzo1.bloodfields.engine.StructureField;
import com.github.rzo1.bloodfields.model.Projectile;
import com.github.rzo1.bloodfields.model.Terrain.TileType;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitState;
import com.github.rzo1.bloodfields.model.UnitType;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;

import java.util.List;

public final class Renderer {

    private static final Color GRASS = AssetLoader.GRASS;
    private static final Color RIVER = AssetLoader.RIVER;
    private static final Color FOREST = AssetLoader.FOREST;

    private static final Color POOL_FILL = Color.web("#3d0606");
    private static final Color POOL_RING = Color.web("#5a1010");
    private static final double VIGNETTE_BASE_ALPHA = 0.6;
    private static final double VIGNETTE_MID_ALPHA = 0.15;
    private static final double DIM_PER_CASUALTY = 0.5 / 200.0;
    private static final double DIM_MAX_MULT = 1.6;

    private static final Color RED_SKY_L5 = Color.web("#a8400a");
    private static final double RED_SKY_L5_ALPHA = 0.10;
    private static final Color RED_SKY_L6 = Color.web("#7a2008");
    private static final double RED_SKY_L6_ALPHA = 0.15;

    private static final Color SHADOW = Color.rgb(0, 0, 0, 0.30);
    private static final Color PROJECTILE = Color.web("#1a1a1a");
    private static final Color ARROW_COLOR = Color.web("#3a2a18");
    private static final Color FIREBALL_COLOR = Color.web("#ff8c2a");
    private static final Color HAT_COLOR = Color.web("#f5f5f5");

    private static final Color HP_BG = Color.web("#3a0000");
    private static final Color HP_FULL = Color.web("#1faa3a");
    private static final Color HP_LOW = Color.web("#d83030");

    private static final double UNIT_SIZE = 16.0;
    private static final double UNIT_HALF = UNIT_SIZE / 2.0;
    private static final double UNIT_ARC = 5.0;

    private static final double TRIANGLE_W = 14.0;
    private static final double TRIANGLE_H = 18.0;

    private static final double CAVALRY_W = 24.0;
    private static final double CAVALRY_H = 12.0;
    private static final double CAVALRY_WEDGE = 6.0;

    private static final double MAGE_R = 8.0;
    private static final double HAT_W = 6.0;
    private static final double HAT_H = 8.0;

    private static final double NECRO_R = 7.0;
    private static final double NECRO_HOOD_W = 8.0;
    private static final double NECRO_HOOD_H = 6.0;
    private static final Color NECRO_BODY = Color.web("#2a1a35");

    private static final double SHADOW_W = 14.0;
    private static final double SHADOW_H = 4.0;
    private static final double SHADOW_DY = 6.0;

    private static final double PROJECTILE_SIZE = 4.0;
    private static final double PROJECTILE_HALF = PROJECTILE_SIZE / 2.0;
    private static final double ARROW_LEN = 12.0;
    private static final double ARROW_HEAD = 4.0;
    private static final double FIREBALL_R = 6.0;
    private static final double FIREBALL_HALO_R = 10.0;

    private static final double BOULDER_R = 8.0;
    private static final Color BOULDER_FILL = Color.web("#7a6850");
    private static final Color BOULDER_STROKE = Color.web("#2a1f15");
    private static final Color BOULDER_TRAIL = Color.web("#a89478");
    private static final Color BOULDER_SHADOW = Color.rgb(0, 0, 0, 0.45);

    private static final double HP_W = 18.0;
    private static final double HP_H = 3.0;
    private static final double HP_DY = 14.0;

    private static final Color VETERAN_TRIM = Color.web("#ffd76b");
    private static final double VETERAN_TRIM_RADIUS = 13.0;
    private static final double VETERAN_STAR_DY = 18.0;
    private static final double VETERAN_STAR_R = 2.0;

    private static final Color HEALER_BODY = Color.web("#3dff80");
    private static final Color HEALER_CROSS = Color.web("#ffffff");
    private static final Color CATAPULT_FRAME_FILL = Color.web("#5a3d1c");
    private static final Color CATAPULT_FRAME_STROKE = Color.web("#2a1d0c");
    private static final Color CATAPULT_WHEEL = Color.web("#1a1108");
    private static final Color CATAPULT_ARM = Color.web("#3a2510");
    private static final Color CATAPULT_LOAD_FILL = Color.web("#7a6a55");
    private static final Color CATAPULT_LOAD_STROKE = Color.web("#3a2520");
    private static final Color ASSASSIN_FILL = Color.web("#1a1a1a");
    private static final Color ASSASSIN_STROKE = Color.web("#0a0a0a");
    private static final Color GOLEM_FILL = Color.web("#3a3a3a");
    private static final Color GOLEM_STROKE = Color.web("#1a1a1a");
    private static final Color GOLEM_GEM_STROKE = Color.web("#0a0a0a");
    private static final Color PIKE_SHAFT = Color.web("#3a2a18");
    private static final Color PIKE_HEAD_FILL = Color.web("#a09080");
    private static final Color CROWN_STROKE = Color.web("#7a5a10");
    private static final Color DRAGON_EYE = Color.web("#1a0a00");

    private static final double UNIT_CULL_MARGIN = 36.0;
    private static final double POOL_CULL_MARGIN = 32.0;
    private static final int VIGNETTE_CASUALTY_BUCKET = 4;

    private final AssetLoader assets = AssetLoader.get();
    private final CorpseRenderer corpseRenderer = new CorpseRenderer();
    private final StructureRenderer structureRenderer = new StructureRenderer();
    private final FireRenderer fireRenderer = new FireRenderer();

    private com.github.rzo1.bloodfields.engine.FireField fireField;
    private BloodTrails bloodTrails;
    private WallSplatter wallSplatter;

    private GameState auraState;
    private double auraSimTime;
    private StructureField structureField;

    private RadialGradient cachedVignette;
    private int cachedVignetteCasualtyBucket = -1;
    private double cachedVignetteWidth = -1.0;
    private double cachedVignetteHeight = -1.0;

    private double viewMinX, viewMinY, viewMaxX, viewMaxY;
    private final TerrainCache terrainCache = new TerrainCache();

    // Scratch arrays reused across the FX-thread render pass to avoid per-unit
    // double[] allocations for polygon vertices.
    private final double[] polyX3 = new double[3];
    private final double[] polyY3 = new double[3];
    private final double[] polyX4 = new double[4];
    private final double[] polyY4 = new double[4];

    // Dragon wing color is fill.deriveColor(0, 1.0, 0.7, 0.85) — a faction-derived
    // shade. Two-slot identity cache covers the usual two-faction case without
    // thrashing on alternating dragons.
    private Color dragonWingK0, dragonWingV0;
    private Color dragonWingK1, dragonWingV1;

    public void setAuraContext(GameState state, double simTime) {
        this.auraState = state;
        this.auraSimTime = simTime;
    }

    public void setStructureField(StructureField field) {
        this.structureField = field;
    }

    public void setGoreContext(com.github.rzo1.bloodfields.engine.FireField fireField,
                               BloodTrails bloodTrails, WallSplatter wallSplatter) {
        this.fireField = fireField;
        this.bloodTrails = bloodTrails;
        this.wallSplatter = wallSplatter;
    }

    public void updateFireFlicker(double dt) {
        fireRenderer.update(dt);
    }

    public FireRenderer fireRenderer() {
        return fireRenderer;
    }

    public StructureRenderer structureRenderer() {
        return structureRenderer;
    }

    public static UnitShape shapeFor(UnitType type) {
        if (type == null) {
            return UnitShape.SQUARE;
        }
        switch (type) {
            case ARCHER: return UnitShape.TRIANGLE;
            case CAVALRY: return UnitShape.HORIZONTAL_RECT;
            case MAGE: return UnitShape.CIRCLE_WITH_HAT;
            case DRAGON: return UnitShape.DRAGON;
            case NECROMANCER: return UnitShape.NECROMANCER;
            case HEALER: return UnitShape.HEALER;
            case CATAPULT: return UnitShape.CATAPULT;
            case ASSASSIN: return UnitShape.ASSASSIN;
            case GOLEM: return UnitShape.GOLEM;
            case PIKEMAN: return UnitShape.PIKEMAN;
            case GENERAL: return UnitShape.GENERAL;
            case INFANTRY:
            default: return UnitShape.SQUARE;
        }
    }

    public static ProjectileStyle projectileStyleFor(UnitType type) {
        if (type == null) {
            return ProjectileStyle.DEFAULT;
        }
        switch (type) {
            case ARCHER: return ProjectileStyle.ARROW;
            case DRAGON: return ProjectileStyle.FIREBALL;
            case MAGE: return ProjectileStyle.FIREBALL;
            case CATAPULT: return ProjectileStyle.BOULDER;
            default: return ProjectileStyle.DEFAULT;
        }
    }

    public void render(GraphicsContext gc, double width, double height,
                       TileType[][] terrainGrid, double tileSize,
                       List<Unit> units, List<Projectile> projectiles,
                       Camera camera) {
        render(gc, width, height, terrainGrid, tileSize, units, projectiles, null, camera);
    }

    public void render(GraphicsContext gc, double width, double height,
                       TileType[][] terrainGrid, double tileSize,
                       List<Unit> units, List<Projectile> projectiles,
                       List<ParticleSystem.BloodPool> pools,
                       Camera camera) {
        render(gc, width, height, terrainGrid, tileSize, units, projectiles, pools,
                null, null, null, camera, 0, false);
    }

    public void render(GraphicsContext gc, double width, double height,
                       TileType[][] terrainGrid, double tileSize,
                       List<Unit> units, List<Projectile> projectiles,
                       List<ParticleSystem.BloodPool> pools,
                       BloodyTiles bloodyTiles,
                       CorpseField corpses,
                       CrowFlock crows,
                       Camera camera,
                       int campaignLevel, boolean redSky) {
        render(gc, width, height, terrainGrid, tileSize, units, projectiles, pools,
                bloodyTiles, corpses, crows, camera, campaignLevel, redSky,
                null, null, null, null, null, null, null);
    }

    public void render(GraphicsContext gc, double width, double height,
                       TileType[][] terrainGrid, double tileSize,
                       List<Unit> units, List<Projectile> projectiles,
                       List<ParticleSystem.BloodPool> pools,
                       BloodyTiles bloodyTiles,
                       CorpseField corpses,
                       CrowFlock crows,
                       Camera camera,
                       int campaignLevel, boolean redSky,
                       LimbField limbs,
                       StuckArrows stuckArrows,
                       RagdollOverlay ragdolls,
                       ScorchMarks scorchMarks,
                       BattleSmoke battleSmoke,
                       Vulture vulture,
                       CameraShake shake) {

        double sx = shake != null ? shake.offsetX() : 0.0;
        double sy = shake != null ? shake.offsetY() : 0.0;
        boolean shaking = sx != 0.0 || sy != 0.0;
        if (shaking) {
            gc.save();
            gc.translate(sx, sy);
        }

        updateViewportBounds(camera, width, height, tileSize);

        gc.save();
        gc.setFill(GRASS);
        gc.fillRect(0, 0, width, height);
        gc.restore();

        gc.save();
        camera.apply(gc);
        drawTerrain(gc, terrainGrid, tileSize);
        gc.restore();

        if (bloodyTiles != null && tileSize > 0.0) {
            bloodyTiles.render(gc, tileSize, camera);
        }

        if (scorchMarks != null && tileSize > 0.0) {
            scorchMarks.render(gc, tileSize, camera);
        }

        if (fireField != null && tileSize > 0.0) {
            fireRenderer.render(gc, fireField, tileSize, camera);
        }

        if (bloodTrails != null) {
            bloodTrails.render(gc, camera);
        }

        if (structureField != null) {
            structureRenderer.render(gc, structureField, camera);
        }

        if (wallSplatter != null && structureField != null) {
            wallSplatter.render(gc, structureField, camera);
        }

        if (limbs != null) {
            limbs.render(gc, camera);
        }

        if (corpses != null) {
            corpseRenderer.render(gc, corpses, camera);
        }

        if (ragdolls != null) {
            ragdolls.render(gc, camera);
        }

        gc.save();
        camera.apply(gc);
        drawBloodPools(gc, pools);
        gc.restore();

        gc.save();
        camera.apply(gc);
        drawShadows(gc, units);
        gc.restore();

        if (auraState != null) {
            AuraRenderer.render(gc, units, auraState, auraSimTime, camera);
        }

        gc.save();
        camera.apply(gc);
        drawUnits(gc, units);
        gc.restore();

        fireRenderer.renderUnitFlames(gc, units, camera);

        if (stuckArrows != null) {
            stuckArrows.render(gc, units, camera);
        }

        gc.save();
        camera.apply(gc);
        drawProjectiles(gc, projectiles);
        gc.restore();

        gc.save();
        camera.apply(gc);
        drawHealthBars(gc, units);
        gc.restore();

        gc.save();
        camera.apply(gc);
        drawRoutedIndicators(gc, units);
        gc.restore();

        if (crows != null) {
            crows.render(gc, camera);
        }

        if (vulture != null) {
            vulture.render(gc, camera);
        }

        if (battleSmoke != null) {
            battleSmoke.render(gc, camera);
        }

        if (redSky) {
            paintRedSky(gc, width, height, campaignLevel);
        }

        int casualties = corpses != null ? corpses.size() : 0;
        paintVignette(gc, width, height, casualties);

        if (shaking) {
            gc.restore();
        }
    }

    public CorpseRenderer corpseRenderer() {
        return corpseRenderer;
    }

    private void updateViewportBounds(Camera camera, double width, double height, double tileSize) {
        double zoom = camera != null ? camera.zoom : 1.0;
        if (zoom <= 0.0) zoom = 1.0;
        double ox = camera != null ? camera.offsetX : 0.0;
        double oy = camera != null ? camera.offsetY : 0.0;
        double margin = tileSize > 0.0 ? tileSize : 32.0;
        viewMinX = (-ox) / zoom - margin;
        viewMinY = (-oy) / zoom - margin;
        viewMaxX = (width - ox) / zoom + margin;
        viewMaxY = (height - oy) / zoom + margin;
    }

    private boolean aabbInView(double minX, double minY, double maxX, double maxY) {
        return maxX >= viewMinX && minX <= viewMaxX
                && maxY >= viewMinY && minY <= viewMaxY;
    }

    private boolean unitInView(Unit u, double margin) {
        return aabbInView(u.x - margin, u.y - margin, u.x + margin, u.y + margin);
    }

    private void paintVignette(GraphicsContext gc, double width, double height, int casualties) {
        int bucket = Math.max(0, casualties) / VIGNETTE_CASUALTY_BUCKET;
        if (cachedVignette == null
                || bucket != cachedVignetteCasualtyBucket
                || width != cachedVignetteWidth
                || height != cachedVignetteHeight) {
            double mult = Math.min(DIM_MAX_MULT,
                    1.0 + DIM_PER_CASUALTY * (bucket * VIGNETTE_CASUALTY_BUCKET));
            double outerAlpha = Math.min(1.0, VIGNETTE_BASE_ALPHA * mult);
            double midAlpha = Math.min(1.0, VIGNETTE_MID_ALPHA * mult);
            cachedVignette = new RadialGradient(
                    0, 0, 0.5, 0.5, 0.75, true, CycleMethod.NO_CYCLE,
                    new Stop(0.0, Color.rgb(0, 0, 0, 0.0)),
                    new Stop(0.6, Color.rgb(0, 0, 0, midAlpha)),
                    new Stop(1.0, Color.rgb(0, 0, 0, outerAlpha)));
            cachedVignetteCasualtyBucket = bucket;
            cachedVignetteWidth = width;
            cachedVignetteHeight = height;
        }
        gc.save();
        gc.setFill(cachedVignette);
        gc.fillRect(0, 0, width, height);
        gc.restore();
    }

    private void paintRedSky(GraphicsContext gc, double width, double height, int campaignLevel) {
        Color tint;
        double alpha;
        if (campaignLevel >= 5) {
            tint = RED_SKY_L6;
            alpha = RED_SKY_L6_ALPHA;
        } else {
            tint = RED_SKY_L5;
            alpha = RED_SKY_L5_ALPHA;
        }
        gc.save();
        gc.setGlobalAlpha(alpha);
        gc.setFill(tint);
        gc.fillRect(0, 0, width, height);
        gc.setGlobalAlpha(1.0);
        gc.restore();
    }

    private void drawBloodPools(GraphicsContext gc, List<ParticleSystem.BloodPool> pools) {
        if (pools == null || pools.isEmpty()) {
            return;
        }
        for (ParticleSystem.BloodPool pool : pools) {
            if (pool == null || pool.maxAge <= 0.0) {
                continue;
            }
            double r = pool.radius;
            double extentX = r + POOL_CULL_MARGIN;
            double extentY = r * 0.55 + POOL_CULL_MARGIN;
            if (!aabbInView(pool.x - extentX, pool.y - extentY,
                    pool.x + extentX, pool.y + extentY)) {
                continue;
            }
            double t = Math.max(0.0, Math.min(1.0, pool.age / pool.maxAge));
            double alpha = Math.max(0.15, Math.min(0.6, 1.0 - t));
            gc.save();
            gc.setGlobalAlpha(alpha);
            gc.setFill(POOL_FILL);
            gc.fillOval(pool.x - r, pool.y - r * 0.55, r * 2.0, r * 1.1);
            gc.setGlobalAlpha(alpha * 0.7);
            gc.setStroke(POOL_RING);
            gc.setLineWidth(1.0);
            gc.strokeOval(pool.x - r, pool.y - r * 0.55, r * 2.0, r * 1.1);
            gc.restore();
        }
        gc.setGlobalAlpha(1.0);
    }

    private void drawTerrain(GraphicsContext gc, TileType[][] grid, double tileSize) {
        if (grid == null) {
            return;
        }
        // Static terrain (RIVER/FOREST tiles; GRASS is the cleared background)
        // is cached into a WritableImage by TerrainCache and only rebuilt on
        // world identity / tile-size change. The per-frame cost collapses to
        // one drawImage of the visible region instead of N fillRect calls.
        terrainCache.ensureFresh(grid, tileSize);
        terrainCache.draw(gc, viewMinX, viewMinY, viewMaxX - viewMinX, viewMaxY - viewMinY);
    }

    private void drawShadows(GraphicsContext gc, List<Unit> units) {
        if (units == null) {
            return;
        }
        gc.setFill(SHADOW);
        for (Unit u : units) {
            if (u == null || u.state == UnitState.DEAD) {
                continue;
            }
            if (!unitInView(u, UNIT_CULL_MARGIN)) {
                continue;
            }
            gc.fillOval(u.x - SHADOW_W / 2.0, u.y + SHADOW_DY - SHADOW_H / 2.0, SHADOW_W, SHADOW_H);
        }
    }

    private void drawUnits(GraphicsContext gc, List<Unit> units) {
        if (units == null) {
            return;
        }
        gc.setLineWidth(1.5);
        for (Unit u : units) {
            if (u == null || u.state == UnitState.DEAD) {
                continue;
            }
            if (!unitInView(u, UNIT_CULL_MARGIN)) {
                continue;
            }
            drawVeteranTrim(gc, u);
            Color fill = assets.factionFill(u.faction);
            Color stroke = assets.factionStroke(u.faction);
            UnitShape shape = shapeFor(u.type);
            switch (shape) {
                case TRIANGLE:
                    drawTriangleUnit(gc, u, fill, stroke);
                    break;
                case HORIZONTAL_RECT:
                    drawCavalryUnit(gc, u, fill, stroke);
                    break;
                case CIRCLE_WITH_HAT:
                    drawMageUnit(gc, u, fill, stroke);
                    break;
                case DRAGON:
                    drawDragonUnit(gc, u, fill, stroke);
                    break;
                case NECROMANCER:
                    drawNecromancerUnit(gc, u, fill, stroke);
                    break;
                case HEALER:
                    drawHealerUnit(gc, u, fill, stroke);
                    break;
                case CATAPULT:
                    drawCatapultUnit(gc, u, fill, stroke);
                    break;
                case ASSASSIN:
                    drawAssassinUnit(gc, u, fill, stroke);
                    break;
                case GOLEM:
                    drawGolemUnit(gc, u, fill, stroke);
                    break;
                case PIKEMAN:
                    drawPikemanUnit(gc, u, fill, stroke);
                    break;
                case GENERAL:
                    drawGeneralUnit(gc, u, fill, stroke);
                    break;
                case SQUARE:
                default:
                    drawSquareUnit(gc, u, fill, stroke);
                    break;
            }
            drawVeteranStar(gc, u);
        }
    }

    private void drawVeteranTrim(GraphicsContext gc, Unit u) {
        if (u.veteranRank <= 0) return;
        gc.setStroke(VETERAN_TRIM);
        gc.setLineWidth(u.veteranRank >= 2 ? 2.0 : 1.0);
        gc.strokeOval(u.x - VETERAN_TRIM_RADIUS, u.y - VETERAN_TRIM_RADIUS,
                VETERAN_TRIM_RADIUS * 2.0, VETERAN_TRIM_RADIUS * 2.0);
        gc.setLineWidth(1.5);
    }

    private void drawVeteranStar(GraphicsContext gc, Unit u) {
        if (u.veteranRank < 3) return;
        gc.setFill(VETERAN_TRIM);
        gc.fillOval(u.x - VETERAN_STAR_R, u.y - VETERAN_STAR_DY - VETERAN_STAR_R,
                VETERAN_STAR_R * 2.0, VETERAN_STAR_R * 2.0);
    }

    private void drawSquareUnit(GraphicsContext gc, Unit u, Color fill, Color stroke) {
        gc.setFill(fill);
        gc.setStroke(stroke);
        gc.fillRoundRect(u.x - UNIT_HALF, u.y - UNIT_HALF, UNIT_SIZE, UNIT_SIZE, UNIT_ARC, UNIT_ARC);
        gc.strokeRoundRect(u.x - UNIT_HALF, u.y - UNIT_HALF, UNIT_SIZE, UNIT_SIZE, UNIT_ARC, UNIT_ARC);
    }

    private void drawTriangleUnit(GraphicsContext gc, Unit u, Color fill, Color stroke) {
        double halfW = TRIANGLE_W / 2.0;
        double halfH = TRIANGLE_H / 2.0;
        polyX3[0] = u.x; polyX3[1] = u.x - halfW; polyX3[2] = u.x + halfW;
        polyY3[0] = u.y - halfH; polyY3[1] = u.y + halfH; polyY3[2] = u.y + halfH;
        gc.setFill(fill);
        gc.setStroke(stroke);
        gc.fillPolygon(polyX3, polyY3, 3);
        gc.strokePolygon(polyX3, polyY3, 3);
    }

    private void drawCavalryUnit(GraphicsContext gc, Unit u, Color fill, Color stroke) {
        double halfW = CAVALRY_W / 2.0;
        double halfH = CAVALRY_H / 2.0;
        gc.setFill(fill);
        gc.setStroke(stroke);
        gc.fillRoundRect(u.x - halfW, u.y - halfH, CAVALRY_W, CAVALRY_H, 4.0, 4.0);
        gc.strokeRoundRect(u.x - halfW, u.y - halfH, CAVALRY_W, CAVALRY_H, 4.0, 4.0);

        double dirX = u.vx;
        double dirY = u.vy;
        double mag = Math.sqrt(dirX * dirX + dirY * dirY);
        if (mag < 1e-3) {
            dirX = 1.0;
            dirY = 0.0;
        } else {
            dirX /= mag;
            dirY /= mag;
        }
        double tipX = u.x + dirX * (halfW + CAVALRY_WEDGE);
        double tipY = u.y + dirY * (halfW + CAVALRY_WEDGE);
        double perpX = -dirY;
        double perpY = dirX;
        double baseX = u.x + dirX * halfW;
        double baseY = u.y + dirY * halfW;
        polyX3[0] = tipX;
        polyX3[1] = baseX + perpX * halfH;
        polyX3[2] = baseX - perpX * halfH;
        polyY3[0] = tipY;
        polyY3[1] = baseY + perpY * halfH;
        polyY3[2] = baseY - perpY * halfH;
        gc.setFill(fill);
        gc.setStroke(stroke);
        gc.fillPolygon(polyX3, polyY3, 3);
        gc.strokePolygon(polyX3, polyY3, 3);
    }

    private void drawMageUnit(GraphicsContext gc, Unit u, Color fill, Color stroke) {
        double r = MAGE_R;
        gc.setFill(fill);
        gc.setStroke(stroke);
        gc.fillOval(u.x - r, u.y - r, r * 2.0, r * 2.0);
        gc.strokeOval(u.x - r, u.y - r, r * 2.0, r * 2.0);

        double hatBaseY = u.y - r;
        polyX3[0] = u.x; polyX3[1] = u.x - HAT_W / 2.0; polyX3[2] = u.x + HAT_W / 2.0;
        polyY3[0] = hatBaseY - HAT_H; polyY3[1] = hatBaseY; polyY3[2] = hatBaseY;
        gc.setFill(HAT_COLOR);
        gc.setStroke(stroke);
        gc.fillPolygon(polyX3, polyY3, 3);
        gc.strokePolygon(polyX3, polyY3, 3);
    }

    private void drawNecromancerUnit(GraphicsContext gc, Unit u, Color fill, Color stroke) {
        double r = NECRO_R;
        gc.setFill(NECRO_BODY);
        gc.setStroke(stroke);
        gc.fillOval(u.x - r, u.y - r, r * 2.0, r * 2.0);
        gc.strokeOval(u.x - r, u.y - r, r * 2.0, r * 2.0);

        double baseY = u.y - r;
        polyX3[0] = u.x; polyX3[1] = u.x - NECRO_HOOD_W / 2.0; polyX3[2] = u.x + NECRO_HOOD_W / 2.0;
        polyY3[0] = baseY - NECRO_HOOD_H; polyY3[1] = baseY; polyY3[2] = baseY;
        gc.setFill(fill);
        gc.setStroke(stroke);
        gc.fillPolygon(polyX3, polyY3, 3);
        gc.strokePolygon(polyX3, polyY3, 3);
    }

    private void drawHealerUnit(GraphicsContext gc, Unit u, Color fill, Color stroke) {
        double r = 9.0;
        gc.setFill(HEALER_BODY);
        gc.setStroke(stroke);
        gc.setLineWidth(1.5);
        gc.fillOval(u.x - r, u.y - r, r * 2.0, r * 2.0);
        gc.strokeOval(u.x - r, u.y - r, r * 2.0, r * 2.0);

        gc.setStroke(HEALER_CROSS);
        gc.setLineWidth(3.0);
        double half = 6.0;
        gc.strokeLine(u.x - half, u.y, u.x + half, u.y);
        gc.strokeLine(u.x, u.y - half, u.x, u.y + half);
        gc.setLineWidth(1.5);
    }

    private void drawCatapultUnit(GraphicsContext gc, Unit u, Color fill, Color stroke) {
        double bw = 28.0;
        double bh = 12.0;
        gc.setFill(CATAPULT_FRAME_FILL);
        gc.setStroke(CATAPULT_FRAME_STROKE);
        gc.setLineWidth(2.0);
        gc.fillRoundRect(u.x - bw / 2.0, u.y - bh / 2.0, bw, bh, 3.0, 3.0);
        gc.strokeRoundRect(u.x - bw / 2.0, u.y - bh / 2.0, bw, bh, 3.0, 3.0);

        double wheelR = 3.5;
        gc.setFill(CATAPULT_WHEEL);
        gc.fillOval(u.x - bw / 2.0 + 2.0, u.y + bh / 2.0 - 1.0, wheelR * 2.0, wheelR * 2.0);
        gc.fillOval(u.x + bw / 2.0 - 2.0 - wheelR * 2.0, u.y + bh / 2.0 - 1.0, wheelR * 2.0, wheelR * 2.0);

        double armBaseX = u.x - bw / 2.0 + 4.0;
        double armBaseY = u.y - bh / 2.0;
        double armTipX = u.x + bw / 2.0 - 2.0;
        double armTipY = u.y - 18.0;
        gc.setStroke(CATAPULT_ARM);
        gc.setLineWidth(4.0);
        gc.strokeLine(armBaseX, armBaseY, armTipX, armTipY);

        double loadR = 3.0;
        gc.setFill(CATAPULT_LOAD_FILL);
        gc.setStroke(CATAPULT_LOAD_STROKE);
        gc.setLineWidth(1.0);
        gc.fillOval(armTipX - loadR, armTipY - loadR, loadR * 2.0, loadR * 2.0);
        gc.strokeOval(armTipX - loadR, armTipY - loadR, loadR * 2.0, loadR * 2.0);

        gc.setFill(fill);
        gc.fillRect(u.x - 2.0, u.y - bh / 2.0 - 1.0, 4.0, 2.0);

        gc.setLineWidth(1.5);
    }

    private void drawAssassinUnit(GraphicsContext gc, Unit u, Color fill, Color stroke) {
        double dirX = u.vx;
        double dirY = u.vy;
        double mag = Math.sqrt(dirX * dirX + dirY * dirY);
        if (mag < 1e-3) { dirX = 1.0; dirY = 0.0; }
        else { dirX /= mag; dirY /= mag; }

        gc.save();
        gc.setFill(fill);
        for (int i = 1; i <= 4; i++) {
            double t = i * 3.0;
            double tx = u.x - dirX * t;
            double ty = u.y - dirY * t;
            double alpha = 0.55 - i * 0.13;
            double sz = 3.0 - i * 0.5;
            if (alpha <= 0.0 || sz <= 0.5) continue;
            gc.setGlobalAlpha(alpha);
            gc.fillOval(tx - sz / 2.0, ty - sz / 2.0, sz, sz);
        }
        gc.setGlobalAlpha(1.0);
        gc.restore();

        double half = 5.0;
        polyX4[0] = u.x; polyX4[1] = u.x + half; polyX4[2] = u.x; polyX4[3] = u.x - half;
        polyY4[0] = u.y - half; polyY4[1] = u.y; polyY4[2] = u.y + half; polyY4[3] = u.y;
        gc.setFill(ASSASSIN_FILL);
        gc.setStroke(ASSASSIN_STROKE);
        gc.setLineWidth(1.0);
        gc.fillPolygon(polyX4, polyY4, 4);
        gc.strokePolygon(polyX4, polyY4, 4);

        gc.setStroke(fill);
        gc.setLineWidth(2.0);
        gc.strokeLine(u.x - half + 1.0, u.y, u.x + half - 1.0, u.y);
        gc.setLineWidth(1.5);
    }

    private void drawGolemUnit(GraphicsContext gc, Unit u, Color fill, Color stroke) {
        double size = 24.0;
        double half = size / 2.0;
        gc.setFill(GOLEM_FILL);
        gc.setStroke(GOLEM_STROKE);
        gc.setLineWidth(3.0);
        gc.fillRect(u.x - half, u.y - half, size, size);
        gc.strokeRect(u.x - half, u.y - half, size, size);

        double gemR = 2.5;
        gc.setFill(fill);
        gc.setStroke(GOLEM_GEM_STROKE);
        gc.setLineWidth(1.0);
        gc.fillOval(u.x - gemR, u.y - gemR, gemR * 2.0, gemR * 2.0);
        gc.strokeOval(u.x - gemR, u.y - gemR, gemR * 2.0, gemR * 2.0);

        gc.setStroke(GOLEM_STROKE);
        gc.setLineWidth(1.0);
        gc.strokeLine(u.x - half + 6.0, u.y - half, u.x - half + 6.0, u.y - half + 4.0);
        gc.strokeLine(u.x + half - 6.0, u.y + half, u.x + half - 6.0, u.y + half - 4.0);
        gc.setLineWidth(1.5);
    }

    private void drawPikemanUnit(GraphicsContext gc, Unit u, Color fill, Color stroke) {
        double bodyHalf = 7.0;
        gc.setFill(fill);
        gc.setStroke(stroke);
        gc.setLineWidth(1.5);
        gc.fillRect(u.x - bodyHalf, u.y - bodyHalf, bodyHalf * 2.0, bodyHalf * 2.0);
        gc.strokeRect(u.x - bodyHalf, u.y - bodyHalf, bodyHalf * 2.0, bodyHalf * 2.0);

        double dirX = u.vx;
        double dirY = u.vy;
        double mag = Math.sqrt(dirX * dirX + dirY * dirY);
        if (mag < 1e-3) { dirX = 1.0; dirY = 0.0; }
        else { dirX /= mag; dirY /= mag; }

        double pikeLen = 18.0;
        double startX = u.x + dirX * bodyHalf;
        double startY = u.y + dirY * bodyHalf;
        double tipX = u.x + dirX * (bodyHalf + pikeLen);
        double tipY = u.y + dirY * (bodyHalf + pikeLen);
        gc.setStroke(PIKE_SHAFT);
        gc.setLineWidth(4.0);
        gc.strokeLine(startX, startY, tipX, tipY);

        double headLen = 5.0;
        double perpX = -dirY;
        double perpY = dirX;
        double baseX = tipX - dirX * headLen;
        double baseY = tipY - dirY * headLen;
        polyX3[0] = tipX; polyX3[1] = baseX + perpX * 2.5; polyX3[2] = baseX - perpX * 2.5;
        polyY3[0] = tipY; polyY3[1] = baseY + perpY * 2.5; polyY3[2] = baseY - perpY * 2.5;
        gc.setFill(PIKE_HEAD_FILL);
        gc.setStroke(PIKE_SHAFT);
        gc.setLineWidth(1.0);
        gc.fillPolygon(polyX3, polyY3, 3);
        gc.strokePolygon(polyX3, polyY3, 3);

        gc.setLineWidth(1.5);
    }

    private void drawGeneralUnit(GraphicsContext gc, Unit u, Color fill, Color stroke) {
        double size = 22.0;
        double half = size / 2.0;
        gc.setFill(fill);
        gc.setStroke(VETERAN_TRIM);
        gc.setLineWidth(2.0);
        gc.fillRoundRect(u.x - half, u.y - half, size, size, 4.0, 4.0);
        gc.strokeRoundRect(u.x - half, u.y - half, size, size, 4.0, 4.0);

        Color crownFill = VETERAN_TRIM;
        Color crownStroke = CROWN_STROKE;
        double cy = u.y - half - 1.0;
        double cw = 4.0;
        double ch = 6.0;
        gc.setFill(crownFill);
        gc.setStroke(crownStroke);
        gc.setLineWidth(1.0);
        for (int i = -1; i <= 1; i++) {
            double cx = u.x + i * (cw + 1.0);
            polyX3[0] = cx; polyX3[1] = cx - cw / 2.0; polyX3[2] = cx + cw / 2.0;
            polyY3[0] = cy - ch; polyY3[1] = cy; polyY3[2] = cy;
            gc.fillPolygon(polyX3, polyY3, 3);
            gc.strokePolygon(polyX3, polyY3, 3);
        }
        gc.setLineWidth(1.5);
    }

    private void drawDragonUnit(GraphicsContext gc, Unit u, Color fill, Color stroke) {
        double r = 18.0;
        double dirX = u.vx;
        double dirY = u.vy;
        double mag = Math.sqrt(dirX * dirX + dirY * dirY);
        if (mag < 1e-3) { dirX = 1.0; dirY = 0.0; }
        else { dirX /= mag; dirY /= mag; }
        double perpX = -dirY;
        double perpY = dirX;

        double bodyTipX = u.x + dirX * r;
        double bodyTipY = u.y + dirY * r;
        double bodyTailX = u.x - dirX * r;
        double bodyTailY = u.y - dirY * r;
        double bodyLeftX = u.x + perpX * (r * 0.7);
        double bodyLeftY = u.y + perpY * (r * 0.7);
        double bodyRightX = u.x - perpX * (r * 0.7);
        double bodyRightY = u.y - perpY * (r * 0.7);

        polyX4[0] = bodyTipX; polyX4[1] = bodyLeftX; polyX4[2] = bodyTailX; polyX4[3] = bodyRightX;
        polyY4[0] = bodyTipY; polyY4[1] = bodyLeftY; polyY4[2] = bodyTailY; polyY4[3] = bodyRightY;
        gc.setFill(fill);
        gc.setStroke(stroke);
        gc.setLineWidth(2.0);
        gc.fillPolygon(polyX4, polyY4, 4);
        gc.strokePolygon(polyX4, polyY4, 4);

        double wingSpan = r * 1.6;
        double wingBackX = u.x - dirX * (r * 0.2);
        double wingBackY = u.y - dirY * (r * 0.2);
        double wingFrontX = u.x + dirX * (r * 0.4);
        double wingFrontY = u.y + dirY * (r * 0.4);

        Color wingFill = dragonWingFor(fill);
        gc.setFill(wingFill);
        gc.setStroke(stroke);
        polyX3[0] = wingFrontX; polyX3[1] = wingBackX; polyX3[2] = u.x + perpX * wingSpan;
        polyY3[0] = wingFrontY; polyY3[1] = wingBackY; polyY3[2] = u.y + perpY * wingSpan;
        gc.fillPolygon(polyX3, polyY3, 3);
        gc.strokePolygon(polyX3, polyY3, 3);
        polyX3[2] = u.x - perpX * wingSpan;
        polyY3[2] = u.y - perpY * wingSpan;
        gc.fillPolygon(polyX3, polyY3, 3);
        gc.strokePolygon(polyX3, polyY3, 3);

        gc.setFill(DRAGON_EYE);
        gc.fillOval(bodyTipX - 2.0, bodyTipY - 2.0, 4.0, 4.0);
        gc.setLineWidth(1.5);
    }

    private Color dragonWingFor(Color fill) {
        if (fill == dragonWingK0) return dragonWingV0;
        if (fill == dragonWingK1) return dragonWingV1;
        Color derived = fill.deriveColor(0, 1.0, 0.7, 0.85);
        dragonWingK1 = dragonWingK0;
        dragonWingV1 = dragonWingV0;
        dragonWingK0 = fill;
        dragonWingV0 = derived;
        return derived;
    }

    private void drawProjectiles(GraphicsContext gc, List<Projectile> projectiles) {
        if (projectiles == null) {
            return;
        }
        for (Projectile p : projectiles) {
            if (p == null || !p.alive) {
                continue;
            }
            ProjectileStyle style = projectileStyleFor(p.attackerType);
            switch (style) {
                case ARROW:
                    drawArrow(gc, p);
                    break;
                case FIREBALL:
                    drawFireball(gc, p);
                    break;
                case BOULDER:
                    drawBoulder(gc, p);
                    break;
                case DEFAULT:
                default:
                    gc.setFill(PROJECTILE);
                    gc.fillRect(p.x - PROJECTILE_HALF, p.y - PROJECTILE_HALF, PROJECTILE_SIZE, PROJECTILE_SIZE);
                    break;
            }
        }
    }

    private void drawBoulder(GraphicsContext gc, Projectile p) {
        double mag = Math.sqrt(p.vx * p.vx + p.vy * p.vy);
        double dirX = mag > 1.0e-6 ? p.vx / mag : 1.0;
        double dirY = mag > 1.0e-6 ? p.vy / mag : 0.0;

        // Ground shadow underneath the boulder so the arc reads visually.
        gc.save();
        gc.setFill(BOULDER_SHADOW);
        gc.fillOval(p.x - BOULDER_R, p.y + BOULDER_R + 2.0,
                BOULDER_R * 2.0, BOULDER_R);
        gc.restore();

        gc.save();
        gc.setFill(BOULDER_TRAIL);
        for (int i = 1; i <= 5; i++) {
            double t = i * 6.0;
            double tx = p.x - dirX * t;
            double ty = p.y - dirY * t;
            double alpha = 0.55 - i * 0.10;
            double sz = 6.0 - i * 0.8;
            if (alpha <= 0.0 || sz <= 0.5) continue;
            gc.setGlobalAlpha(alpha);
            gc.fillOval(tx - sz / 2.0, ty - sz / 2.0, sz, sz);
        }
        gc.setGlobalAlpha(1.0);
        gc.restore();

        double rot = auraSimTime * 6.0;
        gc.save();
        gc.translate(p.x, p.y);
        gc.rotate(Math.toDegrees(rot));
        gc.setFill(BOULDER_FILL);
        gc.setStroke(BOULDER_STROKE);
        gc.setLineWidth(1.5);
        gc.fillOval(-BOULDER_R, -BOULDER_R, BOULDER_R * 2.0, BOULDER_R * 2.0);
        gc.strokeOval(-BOULDER_R, -BOULDER_R, BOULDER_R * 2.0, BOULDER_R * 2.0);
        gc.setLineWidth(1.0);
        gc.strokeLine(-BOULDER_R * 0.6, -1.0, BOULDER_R * 0.4, 2.0);
        gc.strokeLine(-BOULDER_R * 0.2, BOULDER_R * 0.5, BOULDER_R * 0.5, -BOULDER_R * 0.3);
        gc.restore();
    }

    private void drawArrow(GraphicsContext gc, Projectile p) {
        double mag = Math.sqrt(p.vx * p.vx + p.vy * p.vy);
        if (mag < 1.0) {
            return;
        }
        double dirX = p.vx / mag;
        double dirY = p.vy / mag;
        double tailX = p.x - dirX * ARROW_LEN;
        double tailY = p.y - dirY * ARROW_LEN;
        gc.setStroke(ARROW_COLOR);
        gc.setLineWidth(2.0);
        gc.strokeLine(tailX, tailY, p.x, p.y);

        double perpX = -dirY;
        double perpY = dirX;
        double baseX = p.x - dirX * ARROW_HEAD;
        double baseY = p.y - dirY * ARROW_HEAD;
        double half = ARROW_HEAD / 2.0;
        polyX3[0] = p.x;
        polyX3[1] = baseX + perpX * half;
        polyX3[2] = baseX - perpX * half;
        polyY3[0] = p.y;
        polyY3[1] = baseY + perpY * half;
        polyY3[2] = baseY - perpY * half;
        gc.setFill(ARROW_COLOR);
        gc.fillPolygon(polyX3, polyY3, 3);
    }

    private void drawFireball(GraphicsContext gc, Projectile p) {
        gc.save();
        gc.setGlobalAlpha(0.3);
        gc.setFill(FIREBALL_COLOR);
        gc.fillOval(p.x - FIREBALL_HALO_R, p.y - FIREBALL_HALO_R,
                FIREBALL_HALO_R * 2.0, FIREBALL_HALO_R * 2.0);
        gc.restore();
        gc.setFill(FIREBALL_COLOR);
        gc.fillOval(p.x - FIREBALL_R, p.y - FIREBALL_R,
                FIREBALL_R * 2.0, FIREBALL_R * 2.0);
    }

    private void drawHealthBars(GraphicsContext gc, List<Unit> units) {
        if (units == null) {
            return;
        }
        for (Unit u : units) {
            if (u == null || u.state == UnitState.DEAD) {
                continue;
            }
            if (!unitInView(u, UNIT_CULL_MARGIN)) {
                continue;
            }
            double max = u.maxHp > 0.0 ? u.maxHp : u.type.maxHp();
            if (max <= 0.0) {
                continue;
            }
            double frac = Math.max(0.0, Math.min(1.0, u.hp / max));
            double bx = u.x - HP_W / 2.0;
            double by = u.y - HP_DY;
            gc.setFill(HP_BG);
            gc.fillRect(bx, by, HP_W, HP_H);
            gc.setFill(HP_LOW.interpolate(HP_FULL, frac));
            gc.fillRect(bx, by, HP_W * frac, HP_H);
        }
    }

    private static final javafx.scene.text.Font ROUTED_FONT =
            javafx.scene.text.Font.font("System", javafx.scene.text.FontWeight.BOLD, 12.0);

    private void drawRoutedIndicators(GraphicsContext gc, List<Unit> units) {
        if (units == null) {
            return;
        }
        gc.setFill(Color.WHITE);
        gc.setFont(ROUTED_FONT);
        for (Unit u : units) {
            if (u == null || u.state == UnitState.DEAD) continue;
            if (!u.routed) continue;
            if (!unitInView(u, UNIT_CULL_MARGIN)) continue;
            gc.fillText("!", u.x - 2.0, u.y - HP_DY - 2.0);
        }
    }
}
