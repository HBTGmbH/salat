package org.tb.persistence;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tb.bdom.ProjectID;

import java.util.List;

/**
 * DAO-class for ProjectID
 *
 * @author sql
 */
@Component
public class ProjectIDDAO extends AbstractDAO {

    @Autowired
    public ProjectIDDAO(SessionFactory sessionFactory) {
        super(sessionFactory);
    }

    /**
     * Gets the ProjectID for the given id.
     */
    public ProjectID getProjectIDById(long id) {
        return (ProjectID) getSession().get(ProjectID.class, id);
    }

    /**
     * Gets a list of ProjectIDs by customerorder id.
     * <p>
     * At the moment, this list should only contain one or zero entries!!!
     */
    @SuppressWarnings("unchecked")
    public List<ProjectID> getProjectIDsByCustomerorderID(long customerorderId) {
        return getSession().createQuery("from ProjectID p where p.customerorder.id = ? order by jiraProjectID").setLong(0, customerorderId).list();
    }

    /**
     * Gets a list of ProjectIDs by Jira Project ID.
     * <p>
     * At the moment, this list should only contain one or zero entries!!!
     */
    @SuppressWarnings("unchecked")
    public List<ProjectID> getProjectIDsByJiraProjectID(String jiraProjectID) {
        return getSession().createQuery("from ProjectID p where p.jiraProjectID = ? order by jiraProjectID").setString(0, jiraProjectID).list();
    }

    /**
     * Gets a ProjectID by Jira Project ID and customerorder ID.
     */
    public ProjectID getProjectIDByJiraAndCustomerorderID(String jiraProjectID, long customerorderId) {
        return (ProjectID) getSession().createQuery("from ProjectID p where p.jiraProjectID = ? and p.customerorder.id = ?")
                .setString(0, jiraProjectID).setLong(1, customerorderId)
                .uniqueResult();
    }

    public void save(ProjectID projectID) {
        Session session = getSession();
        session.saveOrUpdate(projectID);
        session.flush();
    }

    public void deleteProjectID(ProjectID projectID) {
        getSession().delete(projectID);
        getSession().flush();
    }

}
