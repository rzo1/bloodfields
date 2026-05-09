package com.example.armyclash.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeroSkillTest {

    @Test
    void hasFiveEntries() {
        assertEquals(5, HeroSkill.values().length);
    }

    @Test
    void everySkillHasNonBlankDisplayNameAndDescription() {
        for (HeroSkill s : HeroSkill.values()) {
            assertNotNull(s.displayName(), s.name() + " displayName must not be null");
            assertTrue(!s.displayName().isBlank(), s.name() + " displayName must not be blank");
            assertNotNull(s.description(), s.name() + " description must not be null");
            assertTrue(!s.description().isBlank(), s.name() + " description must not be blank");
        }
    }

    @Test
    void allFiveEnumNamesPresent() {
        assertNotNull(HeroSkill.BATTLE_LUST);
        assertNotNull(HeroSkill.IRON_DISCIPLINE);
        assertNotNull(HeroSkill.SWIFT_STRIKE);
        assertNotNull(HeroSkill.VAMPIRIC_BANNER);
        assertNotNull(HeroSkill.SWIFT_FEET);
    }
}
