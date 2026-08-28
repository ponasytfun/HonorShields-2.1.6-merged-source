# HonorShields world-load recovery

The first data-pack error screen is generic: Minecraft shows it for any exception while loading level data or server resources. If **Safe Mode** then reports that the save data is invalid or corrupted, the vanilla-only retry also failed. The precise exception is recorded in `logs/latest.log`.

## Safe recovery order

1. Close Minecraft and copy the entire affected world folder somewhere safe.
2. Remove every older HonorShields JAR from `mods`. Install only `HonorShields-2.1.6-Minecraft-26.2.jar` and Fabric API 0.157.0+26.2 or newer for Minecraft 26.2.
3. Start Minecraft 26.2 with Fabric Loader 0.19.3 or newer and Java 25, then try opening the world normally.
4. If loading still fails, do not delete the world or repeatedly save it in Safe Mode. Share the new `logs/latest.log`; the useful section starts with `Failed to load level data or datapacks` and includes the first `Caused by` lines.

## If the log specifically says `level.dat` cannot be read

Keep the full-world backup from step 1. In a second working copy of the world, preserve the current `level.dat`, then copy `level.dat_old` to `level.dat`. Minecraft maintains `level.dat_old` as the previous metadata snapshot. Only use this fallback when the log names `level.dat` itself; it does not repair a broken recipe, registry entry, or mod mismatch.

HonorShields stores its own oath flag under the world's `data/honorable-smp/oath.dat`, not inside `level.dat`. A parse failure in that optional mod file is logged and recreated by Minecraft rather than being treated as corrupt world metadata.
