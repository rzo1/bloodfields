package com.example.armyclash.model;

public enum HeroSkill {
    BATTLE_LUST("Battle Lust", "Allies nearby deal +25% damage."),
    IRON_DISCIPLINE("Iron Discipline", "Allies nearby take 25% less damage."),
    SWIFT_STRIKE("Swift Strike", "Allies nearby attack 30% faster."),
    VAMPIRIC_BANNER("Vampiric Banner", "Allies nearby heal 20% of damage dealt."),
    SWIFT_FEET("Swift Feet", "Allies nearby move 30% faster.");

    private final String displayName;
    private final String description;

    HeroSkill(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String displayName() { return displayName; }
    public String description() { return description; }
}
