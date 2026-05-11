package com.github.rzo1.bloodfields.ui;

import com.github.rzo1.bloodfields.model.Terrain.TileType;
import javafx.application.Platform;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class TerrainCacheTest {

    private static volatile boolean platformReady = false;
    private static volatile boolean platformAvailable = true;

    private static synchronized void ensurePlatform() {
        if (platformReady || !platformAvailable) {
            return;
        }
        try {
            Platform.startup(() -> {});
            platformReady = true;
        } catch (IllegalStateException alreadyStarted) {
            platformReady = true;
        } catch (UnsupportedOperationException | Error e) {
            platformAvailable = false;
        } catch (RuntimeException e) {
            platformAvailable = false;
        }
    }

    private static TileType[][] grid(int cols, int rows, TileType fill) {
        TileType[][] g = new TileType[cols][rows];
        for (int c = 0; c < cols; c++) {
            for (int r = 0; r < rows; r++) {
                g[c][r] = fill;
            }
        }
        return g;
    }

    private static <T> T onFx(java.util.function.Supplier<T> work) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                result.set(work.get());
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS), "FX work timed out");
        if (error.get() != null) {
            if (error.get() instanceof Exception) throw (Exception) error.get();
            throw new RuntimeException(error.get());
        }
        return result.get();
    }

    @Test
    void rebuildsOnFirstCallAndOnGridIdentityChange() throws Exception {
        ensurePlatform();
        assumeTrue(platformAvailable, "JavaFX platform unavailable in this environment");

        TileType[][] gridA = grid(8, 6, TileType.GRASS);
        gridA[2][3] = TileType.RIVER;
        gridA[5][1] = TileType.FOREST;
        TileType[][] gridB = grid(8, 6, TileType.GRASS);
        gridB[1][1] = TileType.FOREST;
        double tileSize = 16.0;

        Boolean ok = onFx(() -> {
            TerrainCache cache = new TerrainCache();
            assertFalse(cache.hasImage(), "fresh cache has no image");

            cache.ensureFresh(gridA, tileSize);
            assertTrue(cache.hasImage(), "image built after first ensureFresh");
            assertSame(gridA, cache.cachedGrid(), "tracks gridA identity");

            // Same grid identity, same tile size: no rebuild (cachedGrid stays same).
            cache.ensureFresh(gridA, tileSize);
            assertSame(gridA, cache.cachedGrid(), "still gridA after no-op ensureFresh");

            // Different grid identity: rebuild.
            cache.ensureFresh(gridB, tileSize);
            assertSame(gridB, cache.cachedGrid(), "rebuilt to track gridB identity");

            // Different tile size also triggers rebuild.
            cache.ensureFresh(gridB, tileSize * 2.0);
            assertSame(gridB, cache.cachedGrid(), "still gridB after tile-size change rebuild");

            cache.invalidate();
            assertFalse(cache.hasImage(), "invalidate drops image");
            return Boolean.TRUE;
        });
        assertNotNull(ok);
    }

    @Test
    void ensureFreshIsNullSafe() throws Exception {
        ensurePlatform();
        assumeTrue(platformAvailable, "JavaFX platform unavailable in this environment");

        Boolean ok = onFx(() -> {
            TerrainCache cache = new TerrainCache();
            cache.ensureFresh(null, 16.0);
            assertFalse(cache.hasImage(), "null grid leaves cache empty");
            cache.ensureFresh(grid(4, 4, TileType.GRASS), 0.0);
            assertFalse(cache.hasImage(), "zero tile size leaves cache empty");
            return Boolean.TRUE;
        });
        assertNotNull(ok);
    }
}
