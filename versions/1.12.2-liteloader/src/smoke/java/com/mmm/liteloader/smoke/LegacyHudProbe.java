package com.mmm.liteloader.smoke;

import com.mmm.config.Configs;
import com.mmm.config.FeatureToggle;
import java.io.File;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Checks actual framebuffer output, not just successful initialization. */
final class LegacyHudProbe {
    private int ticks;

    boolean tick(Minecraft client) throws Exception {
        ticks++;
        if (ticks == 1) {
            client.openScreen(null);
            client.options.hideGui = false;
            FeatureToggle.MMM_MINING_TRACKER.setBooleanValue(true);
            FeatureToggle.MMM_HUD.setBooleanValue(true);
            Configs.Generic.HUD_ALIGNMENT.setOptionListValue(Configs.HudAlignment.TOP_LEFT);
            Configs.Generic.HUD_X.setIntegerValue(4);
            Configs.Generic.HUD_Y.setIntegerValue(4);
            Configs.Generic.HUD_SCALE.setDoubleValue(1);
            Configs.Generic.HUD_TITLE_VISIBLE.setBooleanValue(true);
            Configs.Generic.HUD_TITLE_HEX_COLOR.setValueFromString("#FFFF00FF");
        }
        if (ticks == 80) {
            check(client, "enabled", true);
            FeatureToggle.MMM_HUD.setBooleanValue(false);
        } else if (ticks == 100) {
            check(client, "disabled", false);
            FeatureToggle.MMM_HUD.setBooleanValue(true);
            FeatureToggle.MMM_MINING_TRACKER.setBooleanValue(false);
        } else if (ticks == 120) {
            check(client, "tracker-disabled", false);
            FeatureToggle.MMM_MINING_TRACKER.setBooleanValue(true);
            client.options.hideGui = true;
        } else if (ticks == 140) {
            check(client, "f1-hidden", false);
            client.options.hideGui = false;
        } else if (ticks == 160) {
            check(client, "restored", true);
            System.out.println("MMM_HUD_PROBE_OK");
            return true;
        }
        return false;
    }

    private void check(Minecraft client, String state, boolean visible) throws Exception {
        String filename = "hud-hook-" + state + ".png";
        File file = new File(new File(client.gameDir, "screenshots"), filename);
        java.nio.file.Files.deleteIfExists(file.toPath());
        Screenshot.take(client.gameDir, filename, client.width, client.height, client.getRenderTarget());
        var image = ImageIO.read(file);
        int pixels = 0;
        for (int y = 0; y < Math.min(70, image.getHeight()); y++) {
            for (int x = 0; x < Math.min(220, image.getWidth()); x++) {
                int color = image.getRGB(x, y);
                if (((color >> 16) & 255) > 230 && ((color >> 8) & 255) < 30 && (color & 255) > 230) pixels++;
            }
        }
        if (visible ? pixels < 30 : pixels != 0) throw new AssertionError("HUD " + state + ": " + pixels + " title pixels");
        System.out.println("MMM_HUD_PIXELS " + state + "=" + pixels);
    }
}
