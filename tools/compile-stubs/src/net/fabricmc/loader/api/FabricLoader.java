package net.fabricmc.loader.api;
import java.nio.file.Path;
public interface FabricLoader {
    FabricLoader INSTANCE = () -> Path.of(System.getProperty("java.io.tmpdir"), "honorshields-smoke");

    public static FabricLoader getInstance() { return INSTANCE; }
    Path getConfigDir();
    default boolean isModLoaded(String modId) { return false; }
}
