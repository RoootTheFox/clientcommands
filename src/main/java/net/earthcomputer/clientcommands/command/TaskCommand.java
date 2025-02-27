package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.earthcomputer.clientcommands.task.TaskManager;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandHelper.*;
import static net.fabricmc.fabric.api.client.command.v1.ClientCommandManager.*;

public class TaskCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("ctask")
            .then(literal("list")
                .executes(ctx -> listTasks(ctx.getSource())))
            .then(literal("stop-all")
                .executes(ctx -> stopTasks(ctx.getSource(), "")))
            .then(literal("stop")
                .then(argument("pattern", string())
                    .executes(ctx -> stopTasks(ctx.getSource(), getString(ctx, "pattern"))))));
    }

    private static int listTasks(FabricClientCommandSource source) {
        Iterable<String> tasks = TaskManager.getTaskNames();
        int taskCount = TaskManager.getTaskCount();

        if (taskCount == 0) {
            sendError(new TranslatableText("commands.ctask.list.noTasks"));
        } else {
            sendFeedback(new TranslatableText("commands.ctask.list.success", taskCount).formatted(Formatting.BOLD));
            for (String task : tasks) {
                sendFeedback(new LiteralText("- " + task));
            }
        }

        return taskCount;
    }

    private static int stopTasks(FabricClientCommandSource source, String pattern) {
        List<String> tasksToStop = new ArrayList<>();
        for (String task : TaskManager.getTaskNames()) {
            if (task.contains(pattern))
                tasksToStop.add(task);
        }
        for (String task : tasksToStop)
            TaskManager.removeTask(task);

        if (tasksToStop.isEmpty())
            if (pattern.isEmpty())
                sendError(new TranslatableText("commands.ctask.list.noTasks"));
            else
                sendError(new TranslatableText("commands.ctask.stop.noMatch"));
        else
            sendFeedback(new TranslatableText("commands.ctask.stop.success", tasksToStop.size()));
        return tasksToStop.size();
    }

}
