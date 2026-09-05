package com.mmm.smoke;

import com.mmm.storage.SessionHistory;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

public final class ClientSmoke implements FabricClientGameTest
{
    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            context.runOnClient(RuntimeChecks::run);
            context.waitTicks(40);
            context.runOnClient(client -> {
                if (SessionHistory.getLifetimeSummary() == null)
                    throw new AssertionError("Background history did not finish loading");
                client.setScreen(null);
                Files.writeString(Path.of("mmm-smoke-result.txt"), "PASS: live scoreboard, snapshot invalidation, async history, mixin audit, chat typing, advancements");
                System.out.println("MMM_SMOKE_PASS");
            });
        }
        catch (Exception error)
        {
            throw new AssertionError("MMM runtime regression failed", error);
        }
    }
}
