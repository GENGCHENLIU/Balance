package core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field modifiable from command line arguments.
 * <p>
 * Such fields are often public even though reflection may access private fields.
 * Referencing such fields on the command line depends no the field names, creating
 * coupling which defeats the point of the private modifier.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Mutable {}
