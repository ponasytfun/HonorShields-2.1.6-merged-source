package com.mojang.serialization;

import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.function.Function;

public abstract class MapCodec<T> {
    public <O> RecordCodecBuilder<O, T> forGetter(Function<O, T> getter) { return null; }
    public <S> MapCodec<S> xmap(Function<? super T, ? extends S> to, Function<? super S, ? extends T> from) { return null; }
    public Codec<T> codec() { return null; }
}
