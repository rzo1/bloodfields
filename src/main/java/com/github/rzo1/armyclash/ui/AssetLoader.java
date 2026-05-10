package com.github.rzo1.armyclash.ui;

import com.github.rzo1.armyclash.model.Faction;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public final class AssetLoader {

    private static final AssetLoader INSTANCE = new AssetLoader();

    private static final Color RED_FILL = Color.web("#8a1a14");
    private static final Color BLUE_FILL = Color.web("#1a3d7a");
    private static final Color RED_STROKE = Color.web("#3d0907");
    private static final Color BLUE_STROKE = Color.web("#0a1f44");

    public static final Color GRASS = Color.web("#2f4a26");
    public static final Color RIVER = Color.web("#1f3a55");
    public static final Color FOREST = Color.web("#1a2f17");

    private final Map<String, Image> imageCache = new HashMap<>();

    private AssetLoader() {}

    public static AssetLoader get() {
        return INSTANCE;
    }

    public Color factionFill(Faction faction) {
        return faction == Faction.RED ? RED_FILL : BLUE_FILL;
    }

    public Color factionStroke(Faction faction) {
        return faction == Faction.RED ? RED_STROKE : BLUE_STROKE;
    }

    public Image image(String key) {
        if (imageCache.containsKey(key)) {
            return imageCache.get(key);
        }
        Image img = null;
        try (InputStream in = getClass().getResourceAsStream("/" + key)) {
            if (in != null) {
                img = new Image(in);
            } else {
                System.err.println("AssetLoader: missing asset '" + key + "'");
            }
        } catch (Exception e) {
            System.err.println("AssetLoader: failed to load '" + key + "': " + e.getMessage());
        }
        imageCache.put(key, img);
        return img;
    }
}
