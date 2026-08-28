package com.honorablesmp.honorshields.mixin;

import com.mojang.brigadier.tree.CommandNode;
import java.util.Collection;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Commands.class)
public abstract class CommandsMixin {
	@Redirect(
		method = "fillUsableCommands",
		at = @At(value = "INVOKE", target = "Lcom/mojang/brigadier/tree/CommandNode;getChildren()Ljava/util/Collection;")
	)
	private static <S> Collection<CommandNode<S>> honorshields$hideOath(CommandNode<S> node) {
		return node.getChildren().stream().filter(child -> !child.getName().equals("oath")).toList();
	}
}
