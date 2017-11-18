package com.lucene.starter.annotation;

import java.lang.annotation.*;

/**
 * lucene分词标记
 */
@Target(ElementType.FIELD)
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface Analysis {

}
