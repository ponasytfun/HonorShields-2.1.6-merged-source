package com.mojang.brigadier.suggestion;

import java.util.concurrent.CompletableFuture;

public class SuggestionsBuilder {
    public SuggestionsBuilder suggest(String text) { return this; }
    public CompletableFuture<Suggestions> buildFuture() { return null; }
}
