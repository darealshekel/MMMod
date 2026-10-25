package com.mmm.legacy.social;

import com.mmm.config.Configs;
import com.mmm.legacy.social.mixin.SocialPlayerTabMixin;
import java.lang.reflect.Method;
import net.minecraft.text.Formatting;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TabScoreFormattingTest {
    private boolean visible, commas;
    private Method formatter;
    private final SocialPlayerTabMixin mixin = new SocialPlayerTabMixin() {};

    @BeforeEach void setup() throws Exception {
        visible = Configs.Generic.SCOREBOARD_SCORES_VISIBLE.getBooleanValue();
        commas = Configs.Generic.SCOREBOARD_TAB_LIST_COMMAS.getBooleanValue();
        formatter = SocialPlayerTabMixin.class.getDeclaredMethod("mmm$formatTabScore", StringBuilder.class, int.class);
        formatter.setAccessible(true);
    }

    @AfterEach void restore() {
        Configs.Generic.SCOREBOARD_SCORES_VISIBLE.setBooleanValue(visible);
        Configs.Generic.SCOREBOARD_TAB_LIST_COMMAS.setBooleanValue(commas);
    }

    @Test void widthAndDrawingUseIdenticalFormattedLiveValues() throws Exception {
        Configs.Generic.SCOREBOARD_SCORES_VISIBLE.setBooleanValue(true);
        Configs.Generic.SCOREBOARD_TAB_LIST_COMMAS.setBooleanValue(true);
        for (int value : new int[]{0, 999, 1000, 1001, -1234567, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            String expected = String.format(java.util.Locale.US, "%,d", value);
            assertEquals(" " + expected, format(" ", value));
            assertEquals(Formatting.YELLOW + expected, format(Formatting.YELLOW.toString(), value));
        }
    }

    @Test void disablingCommasRestoresVanillaNumericStrings() throws Exception {
        Configs.Generic.SCOREBOARD_SCORES_VISIBLE.setBooleanValue(true);
        Configs.Generic.SCOREBOARD_TAB_LIST_COMMAS.setBooleanValue(false);
        assertEquals(" 1234567", format(" ", 1234567));
        assertEquals(Formatting.YELLOW + "1234567", format(Formatting.YELLOW.toString(), 1234567));
    }

    @Test void hiddenScoresLeaveNeitherPaddingNorFormattingCodesInEitherPath() throws Exception {
        Configs.Generic.SCOREBOARD_SCORES_VISIBLE.setBooleanValue(false);
        for (boolean useCommas : new boolean[]{true, false}) {
            Configs.Generic.SCOREBOARD_TAB_LIST_COMMAS.setBooleanValue(useCommas);
            assertEquals("", format(" ", 1234567));
            assertEquals("", format(Formatting.YELLOW.toString(), 1234567));
        }
    }

    private String format(String prefix, int score) throws Exception {
        StringBuilder builder = new StringBuilder(prefix);
        assertSame(builder, formatter.invoke(mixin, builder, score));
        return builder.toString();
    }
}
