package io.github.thebusybiscuit.slimefun4.epochrebirth.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.Test;

class RebirthConfigTest {

    private final RebirthConfig config = new RebirthConfig();

    @Test
    void usesPlanningDocumentDeathPenalties() {
        var none = config.penaltyFor("none");
        assertEquals(10.0, none.getMaxHealthReduction());
        assertEquals(3200.0, none.getMoney());
        assertEffect(none, PotionEffectType.HUNGER, 60, 3);
        assertEffect(none, PotionEffectType.BLINDNESS, 5, 1);
        assertEffect(none, PotionEffectType.MINING_FATIGUE, 30, 1);
        assertEffect(none, PotionEffectType.SLOWNESS, 120, 1);
        assertEffect(none, PotionEffectType.WEAKNESS, 120, 1);

        var basic = config.penaltyFor("basic");
        assertEquals(8.0, basic.getMaxHealthReduction());
        assertEquals(1600.0, basic.getMoney());
        assertEffect(basic, PotionEffectType.BLINDNESS, 3, 1);

        var advanced = config.penaltyFor("advanced");
        assertEquals(6.0, advanced.getMaxHealthReduction());
        assertEquals(800.0, advanced.getMoney());

        var ultimate = config.penaltyFor("ultimate");
        assertEquals(2.0, ultimate.getMaxHealthReduction());
        assertEquals(200.0, ultimate.getMoney());
        assertEffect(ultimate, PotionEffectType.ABSORPTION, 180, 2);
        assertEffect(ultimate, PotionEffectType.REGENERATION, 30, 2);
    }

    private void assertEffect(RebirthConfig.Penalty penalty, PotionEffectType type, int durationSeconds, int level) {
        PotionEffect effect = penalty.getEffects().stream()
                .filter(candidate -> candidate.getType().equals(type))
                .findFirst()
                .orElseThrow();
        assertEquals(durationSeconds * 20, effect.getDuration());
        assertEquals(level - 1, effect.getAmplifier());
    }
}
