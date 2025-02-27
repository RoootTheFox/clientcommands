package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import dev.xpple.clientarguments.arguments.CEntitySelector;
import net.earthcomputer.clientcommands.interfaces.IEntity;
import net.earthcomputer.clientcommands.render.RenderQueue;
import net.earthcomputer.clientcommands.task.SimpleTask;
import net.earthcomputer.clientcommands.task.TaskManager;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static dev.xpple.clientarguments.arguments.CBlockPosArgumentType.*;
import static dev.xpple.clientarguments.arguments.CColorArgumentType.*;
import static dev.xpple.clientarguments.arguments.CEntityArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandHelper.*;
import static net.earthcomputer.clientcommands.command.arguments.MultibaseIntegerArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v1.ClientCommandManager.*;

public class GlowCommand {
    private static final SimpleCommandExceptionType FAILED_EXCEPTION = new SimpleCommandExceptionType(new TranslatableText("commands.cglow.entity.failed"));

    private static final int FLAG_KEEP_SEARCHING = 1;

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        var cglow = dispatcher.register(literal("cglow"));
        dispatcher.register(literal("cglow")
                .then(literal("--keep-searching-entities")
                    .redirect(cglow, ctx -> withFlags(ctx.getSource(), FLAG_KEEP_SEARCHING, true)))
                .then(literal("entities")
                    .then(argument("targets", entities())
                        .executes(ctx -> glowEntities(ctx.getSource(), ctx.getArgument("targets", CEntitySelector.class), getFlag(ctx, FLAG_KEEP_SEARCHING) ? 0 : 30, 0xffffff))
                        .then(argument("seconds", integer(0))
                            .executes(ctx -> glowEntities(ctx.getSource(), ctx.getArgument("targets", CEntitySelector.class), getInteger(ctx, "seconds"), 0xffffff))
                            .then(literal("color")
                                .then(argument("color", color())
                                    .executes(ctx -> glowEntities(ctx.getSource(), ctx.getArgument("targets", CEntitySelector.class), getInteger(ctx, "seconds"), Optional.ofNullable(getCColor(ctx, "color").getColorValue()).orElse(0xffffff)))))
                            .then(literal("colorCode")
                                .then(argument("color", multibaseInteger(0, 0xffffff))
                                    .executes(ctx -> glowEntities(ctx.getSource(), ctx.getArgument("targets", CEntitySelector.class), getInteger(ctx, "seconds"), getMultibaseInteger(ctx, "color"))))))))
                .then(literal("area")
                    .then(argument("from", blockPos())
                        .then(argument("to", blockPos())
                            .executes(ctx -> glowBlock(ctx.getSource(), getCBlockPos(ctx, "from"), getCBlockPos(ctx, "to"), 1, 0xffffff))
                            .then(argument("seconds", integer(0))
                                .executes(ctx -> glowBlock(ctx.getSource(), getCBlockPos(ctx, "from"), getCBlockPos(ctx, "to"), getInteger(ctx, "seconds"), 0xffffff))
                                .then(literal("color")
                                    .then(argument("color", color())
                                        .executes(ctx -> glowBlock(ctx.getSource(), getCBlockPos(ctx, "from"), getCBlockPos(ctx, "to"), getInteger(ctx, "seconds"), Optional.ofNullable(getCColor(ctx, "color").getColorValue()).orElse(0xffffff)))))
                                .then(literal("colorCode")
                                    .then(argument("color", multibaseInteger(0, 0xffffff))
                                        .executes(ctx -> glowBlock(ctx.getSource(), getCBlockPos(ctx, "from"), getCBlockPos(ctx, "to"), getInteger(ctx, "seconds"), getMultibaseInteger(ctx, "color")))))))))
                .then(literal("block")
                    .then(argument("block", blockPos())
                        .executes(ctx -> glowBlock(ctx.getSource(), getCBlockPos(ctx, "block"), null, 1, 0xffffff))
                        .then(argument("seconds", integer(0))
                            .executes(ctx -> glowBlock(ctx.getSource(), getCBlockPos(ctx, "block"), null, getInteger(ctx, "seconds"), 0xffffff))
                            .then(literal("color")
                                .then(argument("color", color())
                                    .executes(ctx -> glowBlock(ctx.getSource(), getCBlockPos(ctx, "block"), null, getInteger(ctx, "seconds"), Optional.ofNullable(getCColor(ctx, "color").getColorValue()).orElse(0xffffff)))))
                            .then(literal("colorCode")
                                .then(argument("color", multibaseInteger(0, 0xffffff))
                                    .executes(ctx -> glowBlock(ctx.getSource(), getCBlockPos(ctx, "block"), null, getInteger(ctx, "seconds"), getMultibaseInteger(ctx, "color")))))))));
    }

    private static int glowEntities(FabricClientCommandSource source, CEntitySelector entitySelector, int seconds, int color) throws CommandSyntaxException {
        boolean keepSearching = getFlag(source, FLAG_KEEP_SEARCHING);
        if (keepSearching) {
            String taskName = TaskManager.addTask("cglow", new SimpleTask() {
                @Override
                public boolean condition() {
                    return MinecraftClient.getInstance().player != null;
                }

                @Override
                protected void onTick() {
                    ClientPlayerEntity player = MinecraftClient.getInstance().player;
                    assert player != null;

                    try {
                        for (Entity entity : entitySelector.getEntities(source)) {
                            ((IEntity) entity).addGlowingTicket(seconds * 20, color);
                        }
                    } catch (CommandSyntaxException e) {
                        e.printStackTrace();
                    }
                }
            });

            sendFeedback(new TranslatableText("commands.cglow.entity.keepSearching.success")
                    .append(" ")
                    .append(getCommandTextComponent("commands.client.cancel", "/ctask stop " + taskName)));

            return 0;
        } else {
            List<? extends Entity> entities = entitySelector.getEntities(source);
            if (entities.isEmpty()) {
                throw FAILED_EXCEPTION.create();
            }

            for (Entity entity : entities) {
                ((IEntity) entity).addGlowingTicket(seconds * 20, color);
            }

            sendFeedback("commands.cglow.entity.success", entities.size());

            return entities.size();
        }
    }

    private static int glowBlock(FabricClientCommandSource source, BlockPos pos1, BlockPos pos2, int seconds, int color) {
        List<Box> boundingBoxes = new ArrayList<>();

        if (pos2 == null) {
            boundingBoxes.addAll(MinecraftClient.getInstance().world.getBlockState(pos1).getOutlineShape(source.getWorld(), pos1).getBoundingBoxes());
            if (boundingBoxes.isEmpty()) {
                boundingBoxes.add(new Box(pos1));
            } else {
                boundingBoxes.replaceAll((box) -> box.offset(pos1));
            }
        } else {
            final int minX, maxX, minZ, maxZ, minY, maxY;
            minX = Math.min(pos1.getX(), pos2.getX());
            maxX = Math.max(pos1.getX(), pos2.getX());
            minZ = Math.min(pos1.getZ(), pos2.getZ());
            maxZ = Math.max(pos1.getZ(), pos2.getZ());
            minY = Math.min(pos1.getY(), pos2.getY());
            maxY = Math.max(pos1.getY(), pos2.getY());
            boundingBoxes.add(new Box(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1));
        }

        for (Box box : boundingBoxes) {
            RenderQueue.addCuboid(RenderQueue.Layer.ON_TOP, box, box, color, seconds * 20);
        }

        sendFeedback("commands.cglow.area.success", boundingBoxes.size());

        return boundingBoxes.size();
    }
}
