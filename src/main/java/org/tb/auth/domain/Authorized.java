package org.tb.auth.domain;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Authorized {

  boolean requiresAuthentication() default true;
  boolean requiresManager() default false;
  boolean requiresAdmin() default false;
  boolean requiresBackoffice() default false;
  boolean requireUnrestricted() default false;
  boolean permitAll() default false;

}
