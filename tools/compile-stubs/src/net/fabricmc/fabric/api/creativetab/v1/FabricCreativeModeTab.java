package net.fabricmc.fabric.api.creativetab.v1;
import net.minecraft.world.item.CreativeModeTab;
public final class FabricCreativeModeTab {
    public static CreativeModeTab.Builder builder() { return new CreativeModeTab.Builder(CreativeModeTab.Row.TOP, 0); }
}
