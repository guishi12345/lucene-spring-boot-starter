package com.lucene.starter.annotation;

import java.lang.annotation.*;

/**
 * 标识字段的值唯一
 */
@Target(ElementType.FIELD)
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface Unique {
}
