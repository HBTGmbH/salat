package org.tb.configuration;

import static org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.ui.ModelMap;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.WebRequestInterceptor;

@Slf4j
public class PerformanceLoggingFilter implements WebRequestInterceptor {

    @Override
    public void preHandle(WebRequest request) {
        long starttime = System.currentTimeMillis();
        request.setAttribute("PerformanceLoggingFilter.starttime", starttime, SCOPE_REQUEST);
    }

    @Override
    public void postHandle(WebRequest request, ModelMap model) {
    }

    @Override
    public void afterCompletion(WebRequest request, Exception ex) {
        long starttime = (long) request.getAttribute("PerformanceLoggingFilter.starttime", SCOPE_REQUEST);
        long duration = System.currentTimeMillis() - starttime;
        NativeWebRequest nativeRequest = (NativeWebRequest) request;
        log.info(nativeRequest.getNativeRequest(HttpServletRequest.class).getRequestURI() + " took " + duration + "ms.");
    }

}
