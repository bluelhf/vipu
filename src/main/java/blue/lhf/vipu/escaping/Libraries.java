package blue.lhf.vipu.escaping;

import java.lang.annotation.*;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Annotation to specify the libraries that a {@link VipuPlugin} depends on.
 * <p>
 *     The libraries are specified as a list of Maven coordinates.
 * </p>
 * */
@Retention(RUNTIME)
@Target(TYPE)
public @interface Libraries {
    String[] value();
}
