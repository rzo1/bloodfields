package com.github.rzo1.bloodfields.ui;

import com.github.rzo1.bloodfields.model.Faction;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public final class AssetLoader {

    private static final AssetLoader INSTANCE = new AssetLoader();

    public static final Color GRASS = Color.web("#2f4a26");
    public static final Color RIVER = Color.web("#1f3a55");
    public static final Color FOREST = Color.web("#1a2f17");

    private final Map<String, Image> imageCache = new HashMap<>();

    private AssetLoader() {}

    public static AssetLoader get() {
        return INSTANCE;
    }

    /** Faction fill — delegates to {@link Theme} so the colour-blind toggle applies live. */
    public Color factionFill(Faction faction) {
        return Theme.factionFill(faction);
    }

    /** Faction outline — delegates to {@link Theme} so the colour-blind toggle applies live. */
    public Color factionStroke(Faction faction) {
        return Theme.factionStroke(faction);
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
