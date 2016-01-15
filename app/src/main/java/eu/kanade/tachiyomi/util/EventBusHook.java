package eu.kanade.tachiyomi.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
public @interface EventBusHook {}