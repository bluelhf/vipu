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
    /**
     * @return The libraries, specified as an array of Maven coordinates, like <code>org.example:artifact:3.2.1</code>.
     * */
    String[] value();
}
