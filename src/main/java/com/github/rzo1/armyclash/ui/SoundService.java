package com.github.rzo1.armyclash.ui;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

public final class SoundService {

    private final byte[] meleeBytes;
    private final byte[] arrowBytes;
    private final byte[] victoryBytes;
    private final boolean enabled;

    public SoundService() {
        this.meleeBytes = loadResource("/sfx/melee.wav");
        this.arrowBytes = loadResource("/sfx/arrow.wav");
        this.victoryBytes = loadResource("/sfx/victory.wav");
        this.enabled = probeAudio();
    }

    public void playMeleeHit() {
        play(meleeBytes);
    }

    public void playArrowFire() {
        play(arrowBytes);
    }

    public void playVictory() {
        play(victoryBytes);
    }

    private void play(byte[] data) {
        if (!enabled || data == null) return;
        try (AudioInputStream in = AudioSystem.getAudioInputStream(new ByteArrayInputStream(data))) {
            Clip clip = AudioSystem.getClip();
            clip.open(in);
            clip.addLineListener(ev -> {
                if (ev.getType().toString().equals("Stop")) {
                    try { clip.close(); } catch (Exception ignored) {}
                }
            });
            clip.start();
        } catch (Exception ignored) {
        }
    }

    private static byte[] loadResource(String path) {
        try (InputStream raw = SoundService.class.getResourceAsStream(path)) {
            if (raw == null) return null;
            try (BufferedInputStream in = new BufferedInputStream(raw)) {
                return in.readAllBytes();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean probeAudio() {
        try {
            AudioSystem.getMixerInfo();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
