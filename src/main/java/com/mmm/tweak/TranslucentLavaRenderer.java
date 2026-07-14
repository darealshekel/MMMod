package com.mmm.tweak;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import javax.imageio.ImageIO;

import com.mmm.MMM;
import com.mmm.config.Configs;

import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.fluid.Fluids;
import net.minecraft.resource.ResourcePackManager;

public final class TranslucentLavaRenderer
{
    private static final int RELOAD_DEBOUNCE_TICKS = 4;
    private static final int STARTUP_DELAY_TICKS = 20;
    private static final int RECONCILE_INTERVAL_TICKS = 100;
    private static final int RESOURCE_PACK_FORMAT = 34;
    private static final String PACK_DIRECTORY_NAME = "MMM-Translucent-Lava";
    private static final String PACK_ID = "file/" + PACK_DIRECTORY_NAME;
    private static final String RESOURCE_ROOT = "/assets/mmm/translucent_lava/";
    private static final String GENERATED_VERSION = "1";

    private static int pendingReloadTicks = -1;
    private static int reconcileTicks;
    private static boolean reloadInFlight;
    private static int lastAppliedOpacity = -1;

    private TranslucentLavaRenderer()
    {
    }

    public static void initialize()
    {
        BlockRenderLayerMap.INSTANCE.putFluids(RenderLayer.getTranslucent(), Fluids.LAVA, Fluids.FLOWING_LAVA);
        pendingReloadTicks = STARTUP_DELAY_TICKS;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (reloadInFlight)
            {
                return;
            }

            if (pendingReloadTicks >= 0)
            {
                if (pendingReloadTicks-- > 0)
                {
                    return;
                }

                pendingReloadTicks = -1;
                applyResourcePack(client);
                return;
            }

            if (++reconcileTicks >= RECONCILE_INTERVAL_TICKS)
            {
                reconcileTicks = 0;
                ResourcePackManager manager = client.getResourcePackManager();
                boolean packEnabled = manager.getEnabledIds().contains(PACK_ID);
                int configuredOpacity = Math.clamp(Configs.Generic.LAVA_OPACITY.getIntegerValue(), 10, 100);
                if (packEnabled != isEnabled() || (packEnabled && configuredOpacity != lastAppliedOpacity))
                {
                    pendingReloadTicks = 0;
                }
            }
        });
    }

    public static void requestReload()
    {
        pendingReloadTicks = RELOAD_DEBOUNCE_TICKS;
    }

    public static boolean isEnabled()
    {
        return Configs.Generic.TRANSLUCENT_LAVA.getBooleanValue();
    }

    static int alphaFromPercent(int percent)
    {
        int clampedPercent = Math.clamp(percent, 10, 100);
        return Math.round(clampedPercent * 255.0F / 100.0F);
    }

    private static void applyResourcePack(MinecraftClient client)
    {
        try
        {
            boolean shouldEnable = isEnabled();
            int opacity = Math.clamp(Configs.Generic.LAVA_OPACITY.getIntegerValue(), 10, 100);
            Path packDirectory = client.getResourcePackDir().resolve(PACK_DIRECTORY_NAME);
            boolean contentChanged = shouldEnable && ensureGeneratedPack(packDirectory, opacity);

            ResourcePackManager manager = client.getResourcePackManager();
            if (contentChanged || (shouldEnable && manager.hasProfile(PACK_ID) == false))
            {
                manager.scanPacks();
            }

            boolean stateChanged;
            if (shouldEnable)
            {
                if (manager.hasProfile(PACK_ID) == false)
                {
                    MMM.LOGGER.warn("[MMM] Generated translucent lava pack was not discovered at {}", packDirectory);
                    return;
                }
                stateChanged = manager.getEnabledIds().contains(PACK_ID) == false && manager.enable(PACK_ID);
            }
            else
            {
                stateChanged = manager.getEnabledIds().contains(PACK_ID) && manager.disable(PACK_ID);
            }

            if (stateChanged)
            {
                client.options.refreshResourcePacks(manager);
                lastAppliedOpacity = shouldEnable ? opacity : -1;
                return;
            }

            if (contentChanged == false)
            {
                lastAppliedOpacity = shouldEnable ? opacity : -1;
                return;
            }

            reloadInFlight = true;
            client.reloadResources().whenComplete((unused, throwable) -> client.execute(() -> {
                reloadInFlight = false;
                if (throwable != null)
                {
                    MMM.LOGGER.warn("[MMM] Failed to reload translucent lava resources: {}", throwable.getMessage());
                    pendingReloadTicks = RELOAD_DEBOUNCE_TICKS;
                    return;
                }
                lastAppliedOpacity = opacity;
            }));
        }
        catch (Exception exception)
        {
            reloadInFlight = false;
            MMM.LOGGER.warn("[MMM] Failed to apply translucent lava resources: {}", exception.getMessage());
        }
    }

    static boolean ensureGeneratedPack(Path packDirectory, int opacityPercent) throws IOException
    {
        Path textureDirectory = packDirectory.resolve("assets/minecraft/textures/block");
        Path marker = packDirectory.resolve(".mmm-generated");
        String expectedMarker = GENERATED_VERSION + ":" + opacityPercent;
        Path stillTexture = textureDirectory.resolve("lava_still.png");
        Path flowTexture = textureDirectory.resolve("lava_flow.png");

        if (Files.isRegularFile(marker)
                && expectedMarker.equals(Files.readString(marker, StandardCharsets.UTF_8).trim())
                && Files.isRegularFile(stillTexture)
                && Files.isRegularFile(flowTexture))
        {
            return false;
        }

        Files.createDirectories(textureDirectory);
        Files.writeString(
                packDirectory.resolve("pack.mcmeta"),
                "{\n  \"pack\": {\n    \"pack_format\": " + RESOURCE_PACK_FORMAT
                        + ",\n    \"description\": \"MMM Translucent Lava (managed)\"\n  }\n}\n",
                StandardCharsets.UTF_8
        );
        writeAdjustedTexture("lava_still.png", stillTexture, opacityPercent);
        writeAdjustedTexture("lava_flow.png", flowTexture, opacityPercent);
        copyBundledResource("lava_still.png.mcmeta", textureDirectory.resolve("lava_still.png.mcmeta"));
        copyBundledResource("lava_flow.png.mcmeta", textureDirectory.resolve("lava_flow.png.mcmeta"));
        Files.writeString(marker, expectedMarker, StandardCharsets.UTF_8);
        return true;
    }

    private static void writeAdjustedTexture(String resourceName, Path target, int opacityPercent) throws IOException
    {
        try (InputStream input = requireBundledResource(resourceName))
        {
            BufferedImage source = ImageIO.read(input);
            if (source == null)
            {
                throw new IOException("Unsupported image resource " + resourceName);
            }

            int targetAlpha = alphaFromPercent(opacityPercent);
            BufferedImage image = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < source.getHeight(); y++)
            {
                for (int x = 0; x < source.getWidth(); x++)
                {
                    int color = source.getRGB(x, y);
                    if ((color >>> 24) != 0)
                    {
                        color = (targetAlpha << 24) | (color & 0x00FFFFFF);
                    }
                    image.setRGB(x, y, color);
                }
            }

            Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
            if (ImageIO.write(image, "PNG", temporary.toFile()) == false)
            {
                throw new IOException("PNG writer unavailable for " + resourceName);
            }
            replaceAtomically(temporary, target);
        }
    }

    private static void copyBundledResource(String resourceName, Path target) throws IOException
    {
        try (InputStream input = requireBundledResource(resourceName))
        {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static InputStream requireBundledResource(String resourceName) throws IOException
    {
        InputStream input = TranslucentLavaRenderer.class.getResourceAsStream(RESOURCE_ROOT + resourceName);
        if (input == null)
        {
            throw new IOException("Missing bundled lava resource " + resourceName);
        }
        return input;
    }

    private static void replaceAtomically(Path source, Path target) throws IOException
    {
        try
        {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (AtomicMoveNotSupportedException exception)
        {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
