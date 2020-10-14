package org.tb.persistence;

import org.hibernate.SessionFactory;
import org.hibernate.classic.Session;

abstract public class AbstractDAO {

    private SessionFactory sessionFactory;

    public void setSessionFactory(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    protected Session getSession() {
        return this.sessionFactory.getCurrentSession();
    }
}
