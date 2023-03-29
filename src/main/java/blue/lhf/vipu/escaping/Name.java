package blue.lhf.vipu.escaping;

import java.lang.annotation.*;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
/**
 * Annotation to specify the name of a {@link VipuPlugin}.
 * */
@Retention(RUNTIME)
@Target(TYPE)
public @interface Name {
    String value();
}
