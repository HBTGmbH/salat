package org.tb.auth.service;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.tb.auth.domain.Authorized;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.ErrorCode;

@Aspect
@Component
@RequiredArgsConstructor
public class AuthorizationAspect {

  private final AuthorizedUser authorizedUser;

  @Pointcut("@within(org.tb.auth.domain.Authorized) || @annotation(org.tb.auth.domain.Authorized)")
  public void authorizedMethods() {
  }

  @Before("authorizedMethods()")
  public void authenticate(JoinPoint joinPoint) throws Throwable {
    var effectiveAnnotation = getAnnotation(joinPoint);

    if(effectiveAnnotation.requiresAuthentication() && !authorizedUser.isAuthenticated()) {
      throw new AuthorizationException(ErrorCode.AA_REQUIRED);
    }
    if(effectiveAnnotation.requireUnrestricted() && authorizedUser.isRestricted()) {
      throw new AuthorizationException(ErrorCode.AA_NEEDS_UNRESTRICTED);
    }
    if(effectiveAnnotation.requiresBackoffice() && !authorizedUser.isBackoffice()) {
      throw new AuthorizationException(ErrorCode.AA_NEEDS_BACKOFFICE);
    }
    if(effectiveAnnotation.requiresManager() && !authorizedUser.isManager()) {
      throw new AuthorizationException(ErrorCode.AA_NEEDS_MANAGER);
    }
    if(effectiveAnnotation.requiresAdmin() && !authorizedUser.isAdmin()) {
      throw new AuthorizationException(ErrorCode.AA_NEEDS_ADMIN);
    }
  }

  private Authorized getAnnotation(JoinPoint joinPoint) {
    MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
    var method = methodSignature.getMethod();
    Authorized classAnnotation = method.getDeclaringClass().getAnnotation(Authorized.class);
    Authorized methodAnnotation = method.getAnnotation(Authorized.class);
    var effectiveAnnotation = methodAnnotation != null ? methodAnnotation : classAnnotation;
    return effectiveAnnotation;
  }

}