package com.ywh.annotation;

import com.ywh.enums.SystemPermission;
import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {
    SystemPermission[] value();
}
