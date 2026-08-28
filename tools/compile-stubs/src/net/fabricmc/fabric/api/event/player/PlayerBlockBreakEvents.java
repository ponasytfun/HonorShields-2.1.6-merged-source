package net.fabricmc.fabric.api.event.player;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
public final class PlayerBlockBreakEvents {
    public static final Event<After> AFTER = null;
    @FunctionalInterface public interface After { void afterBlockBreak(Level level, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity); }
}
