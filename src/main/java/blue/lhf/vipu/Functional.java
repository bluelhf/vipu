package blue.lhf.vipu;

import java.util.function.*;
import java.util.stream.Stream;

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

    /**
     * A functional interface that can throw an exception.
     * */
    @FunctionalInterface
    public interface Throwing<T, R> {
        R apply(T t) throws Exception;
    }

    /**
     * Maps a {@link Stream} using a {@link Throwing} function.
     * @param stream The stream to map.
     * @param function The function to map with.
     * @param onException The action to take when an exception is thrown.
     * */
    public static <T, R> Stream<R> map(final Stream<T> stream, final Throwing<T, R> function, final BiConsumer<T, Exception> onException) {
        return stream.mapMulti((t, action) -> {
            try {
                action.accept(function.apply(t));
            } catch (Exception e) {
                onException.accept(t, e);
            }
        });
    }
}
