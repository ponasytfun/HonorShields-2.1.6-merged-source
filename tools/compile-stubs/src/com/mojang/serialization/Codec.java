package com.mojang.serialization;

import com.mojang.serialization.codecs.PrimitiveCodec;

public interface Codec<T> {
    PrimitiveCodec<Boolean> BOOL = null;
    PrimitiveCodec<Integer> INT = null;

    default MapCodec<T> optionalFieldOf(String name, T defaultValue) { return null; }
}
