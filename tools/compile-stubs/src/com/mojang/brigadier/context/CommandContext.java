package com.mojang.brigadier.context;

public class CommandContext<S> {
    public S getSource() { return null; }
    public Object getArgument(String name, Class<?> type) { return null; }
}
