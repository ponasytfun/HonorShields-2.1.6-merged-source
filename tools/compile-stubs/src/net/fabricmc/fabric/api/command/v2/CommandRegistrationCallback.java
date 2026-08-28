package net.fabricmc.fabric.api.command.v2;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
@FunctionalInterface public interface CommandRegistrationCallback {
    Event<CommandRegistrationCallback> EVENT = null;
    void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection environment);
}
