package com.mojang.datafixers;

import com.mojang.datafixers.kinds.App;
import com.mojang.datafixers.kinds.Applicative;
import com.mojang.datafixers.kinds.K1;
import java.util.function.BiFunction;

public final class Products {
	public static final class P2<F extends K1, T1, T2> {
		public <R> App<F, R> apply(Applicative<F, ?> instance, BiFunction<T1, T2, R> constructor) { return null; }
	}

	private Products() {}
}
