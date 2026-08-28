package com.mojang.serialization.codecs;

import com.mojang.serialization.Codec;
import com.mojang.datafixers.Products;
import com.mojang.datafixers.kinds.App;
import com.mojang.datafixers.kinds.Applicative;
import com.mojang.datafixers.kinds.K1;
import java.util.function.Function;

public final class RecordCodecBuilder<O, F> implements App<RecordCodecBuilder.Mu<O>, F> {
	public static final class Mu<O> implements K1 {}

	public static final class Instance<O> implements Applicative<RecordCodecBuilder.Mu<O>, Instance.Mu<O>> {
		public static final class Mu<O> implements K1 {}

		public <A, B> Products.P2<RecordCodecBuilder.Mu<O>, A, B> group(
			App<RecordCodecBuilder.Mu<O>, A> first, App<RecordCodecBuilder.Mu<O>, B> second
		) { return null; }
	}

	public static <O> Codec<O> create(Function<Instance<O>, ? extends App<Mu<O>, O>> builder) { return null; }
}
