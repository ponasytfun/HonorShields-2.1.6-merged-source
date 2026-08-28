package com.mojang.brigadier.arguments;

import com.mojang.brigadier.context.CommandContext;

public final class StringArgumentType implements ArgumentType<String> {
    public static StringArgumentType word() { return new StringArgumentType(); }
    public static String getString(CommandContext<?> context, String name) { return null; }
}
