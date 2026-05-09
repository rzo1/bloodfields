package com.example.armyclash.tools;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class SoundGen {

    private static final float SAMPLE_RATE = 22050f;
    private static final AudioFormat FORMAT =
            new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, SAMPLE_RATE, 16, 1, 2, SAMPLE_RATE, false);

    public static void main(String[] args) throws Exception {
        String outDir = args.length > 0 ? args[0] : "src/main/resources/sfx/";
        Path dir = Paths.get(outDir);
        Files.createDirectories(dir);

        writeWav(dir.resolve("melee.wav").toFile(), melee());
        writeWav(dir.resolve("arrow.wav").toFile(), arrow());
        writeWav(dir.resolve("victory.wav").toFile(), victory());

        System.out.println("Wrote sfx to " + dir.toAbsolutePath());
    }

    private static byte[] melee() {
        int durMs = 80;
        int n = (int) (SAMPLE_RATE * durMs / 1000.0);
        byte[] out = new byte[n * 2];
        double freq = 220.0;
        for (int i = 0; i < n; i++) {
            double t = i / SAMPLE_RATE;
            double phase = (t * freq) % 1.0;
            double sq = phase < 0.5 ? 1.0 : -1.0;
            double env = Math.exp(-25.0 * t);
            short s = (short) (sq * env * 0.55 * Short.MAX_VALUE);
            writeShortLE(out, i * 2, s);
        }
        return out;
    }

    private static byte[] arrow() {
        int durMs = 60;
        int n = (int) (SAMPLE_RATE * durMs / 1000.0);
        byte[] out = new byte[n * 2];
        double phase = 0.0;
        for (int i = 0; i < n; i++) {
            double t = i / SAMPLE_RATE;
            double frac = t / (durMs / 1000.0);
            double freq = 800.0 + (200.0 - 800.0) * frac;
            phase += freq / SAMPLE_RATE;
            double sine = Math.sin(2.0 * Math.PI * phase);
            double env = Math.exp(-15.0 * t);
            short s = (short) (sine * env * 0.45 * Short.MAX_VALUE);
            writeShortLE(out, i * 2, s);
        }
        return out;
    }

    private static byte[] victory() {
        int durMs = 600;
        int n = (int) (SAMPLE_RATE * durMs / 1000.0);
        byte[] out = new byte[n * 2];
        double[] freqs = {261.63, 329.63, 392.00};
        int segLen = n / 3;
        for (int i = 0; i < n; i++) {
            double t = i / SAMPLE_RATE;
            int seg = Math.min(2, i / segLen);
            double localT = (i - seg * segLen) / (double) segLen;
            double f = freqs[seg];
            double sine = Math.sin(2.0 * Math.PI * f * t);
            double env = Math.sin(Math.PI * localT) * 0.5;
            short s = (short) (sine * env * Short.MAX_VALUE);
            writeShortLE(out, i * 2, s);
        }
        return out;
    }

    private static void writeShortLE(byte[] buf, int idx, short v) {
        buf[idx] = (byte) (v & 0xff);
        buf[idx + 1] = (byte) ((v >> 8) & 0xff);
    }

    private static void writeWav(File file, byte[] pcm) throws Exception {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(pcm);
             AudioInputStream ais = new AudioInputStream(bais, FORMAT, pcm.length / FORMAT.getFrameSize())) {
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, file);
        }
    }
}
