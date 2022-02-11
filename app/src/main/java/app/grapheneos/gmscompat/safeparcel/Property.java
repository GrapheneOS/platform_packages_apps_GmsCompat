package app.grapheneos.gmscompat.safeparcel;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

// used by Spoon during codegen, discarded afterwards
@Retention(RetentionPolicy.SOURCE)
public @interface Property {
   int value();
}
