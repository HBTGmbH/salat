package org.tb.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public aspect HibernateLogger {

    private static final Logger LOG = LoggerFactory.getLogger(HibernateLogger.class);

    pointcut callHibernateCreateQuery(): execution(* org.tb.persistence.*DAO.*(..)) && !within(org.tb.persistence.HibernateLogger);

    before(): callHibernateCreateQuery() {
        if (LOG.isTraceEnabled()) {
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            StackTraceElement element = stackTrace[2];
            LOG.trace("Entering {}", element.toString());
        }
    }

    after(): callHibernateCreateQuery() {
        if (LOG.isTraceEnabled()) {
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            StackTraceElement element = stackTrace[2];
            LOG.trace("Leaving  {}", element.toString());
        }
    }
}
