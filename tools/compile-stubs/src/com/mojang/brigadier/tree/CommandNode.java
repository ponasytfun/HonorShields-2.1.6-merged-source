package com.mojang.brigadier.tree;

import java.util.Collection;

public abstract class CommandNode<S> {
    public abstract Collection<CommandNode<S>> getChildren();
    public abstract String getName();
}
