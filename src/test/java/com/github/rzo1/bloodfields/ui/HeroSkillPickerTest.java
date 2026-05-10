package com.github.rzo1.bloodfields.ui;

import com.github.rzo1.bloodfields.model.HeroSkill;
import javafx.application.Platform;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class HeroSkillPickerTest {

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

    private interface JfxAction {
        void run();
    }

    private void runOnFx(JfxAction action) throws Exception {
        ensurePlatform();
        assumeTrue(platformAvailable, "JavaFX platform unavailable in this environment");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        boolean done = latch.await(10, TimeUnit.SECONDS);
        if (!done) {
            assumeTrue(false, "JavaFX runLater never executed");
        }
        assertNull(error.get(), () -> "picker threw: " + error.get());
    }

    @Test
    void initialDescriptionShowsHint() throws Exception {
        runOnFx(() -> {
            HeroSkillPicker picker = new HeroSkillPicker(skill -> {});
            String text = picker.descriptionLabel().getText();
            assertNotNull(text);
            assertEquals("Pick a hero skill to see what it does.", text);
        });
    }

    @Test
    void selectingSkillUpdatesDescriptionLabel() throws Exception {
        runOnFx(() -> {
            HeroSkillPicker picker = new HeroSkillPicker(skill -> {});
            picker.buttonFor(HeroSkill.BATTLE_LUST).fire();
            assertEquals(HeroSkill.BATTLE_LUST.description(),
                    picker.descriptionLabel().getText());

            picker.buttonFor(HeroSkill.SWIFT_FEET).fire();
            assertEquals(HeroSkill.SWIFT_FEET.description(),
                    picker.descriptionLabel().getText());
        });
    }

    @Test
    void deselectingShowsHintAgain() throws Exception {
        runOnFx(() -> {
            HeroSkillPicker picker = new HeroSkillPicker(skill -> {});
            picker.buttonFor(HeroSkill.IRON_DISCIPLINE).fire();
            assertNotEquals("Pick a hero skill to see what it does.",
                    picker.descriptionLabel().getText());
            picker.buttonFor(HeroSkill.IRON_DISCIPLINE).fire();
            assertEquals("Pick a hero skill to see what it does.",
                    picker.descriptionLabel().getText());
        });
    }

    @Test
    void setSelectedSkillUpdatesDescriptionLabel() throws Exception {
        runOnFx(() -> {
            HeroSkillPicker picker = new HeroSkillPicker(skill -> {});
            picker.setSelected(HeroSkill.VAMPIRIC_BANNER);
            assertEquals(HeroSkill.VAMPIRIC_BANNER.description(),
                    picker.descriptionLabel().getText());
            picker.setSelected(null);
            assertEquals("Pick a hero skill to see what it does.",
                    picker.descriptionLabel().getText());
        });
    }
}
