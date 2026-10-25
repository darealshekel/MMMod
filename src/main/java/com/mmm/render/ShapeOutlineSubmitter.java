package com.mmm.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;

public final class ShapeOutlineSubmitter
{
    private ShapeOutlineSubmitter()
    {
    }

    public static void submit(SubmitNodeCollector collector,
                              PoseStack poseStack,
                              VoxelShape shape,
                              int color)
    {
        collector.submitCustomGeometry(poseStack, RenderTypes.lines(),
                (pose, consumer) -> shape.forAllEdges((x1, y1, z1, x2, y2, z2) -> {
                    Vector3f normal = new Vector3f(
                            (float) (x2 - x1),
                            (float) (y2 - y1),
                            (float) (z2 - z1)
                    ).normalize();
                    consumer.addVertex(pose, (float) x1, (float) y1, (float) z1)
                            .setColor(color)
                            .setNormal(pose, normal)
                            .setLineWidth(1.0F);
                    consumer.addVertex(pose, (float) x2, (float) y2, (float) z2)
                            .setColor(color)
                            .setNormal(pose, normal)
                            .setLineWidth(1.0F);
                }));
    }
}
