package blue.lhf.vipu;

import java.util.function.*;

public class Functional {
    private Functional() {

    }

    /**
     * Binds the first argument of a {@link BiFunction} to a value.
     * @return A function that takes the second argument of the original function.
     * @param original The original function.
     * */
    public static <A, B, R> Function<B, R> bind1(final BiFunction<A, B, R> original, final A value) {
        return b -> original.apply(value, b);
    }
}
