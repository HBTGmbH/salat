package org.tb.persistence;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tb.bdom.Ticket;
import org.tb.bdom.Timereport;

import java.util.Date;
import java.util.List;

@Component
public class TicketDAO extends AbstractDAO {

    private final TimereportDAO timereportDAO;

    @Autowired
    public TicketDAO(SessionFactory sessionFactory, TimereportDAO timereportDAO) {
        super(sessionFactory);
        this.timereportDAO = timereportDAO;
    }

    /**
     * Gets the Ticket for the given id.
     */
    public Ticket getTicketById(long id) {
        return (Ticket) getSession().get(Ticket.class, id);
    }

    /**
     * Gets a list of Tickets by suborder id.
     */
    @SuppressWarnings("unchecked")
    public List<Ticket> getTicketsBySuborderID(long suborderID) {
        return getSession().createQuery("from Ticket t where t.suborder.id = ? order by jiraTicketKey").setLong(0, suborderID).list();
    }

    /**
     * Gets a list of Tickets by Jira Ticket Key.
     */
    @SuppressWarnings("unchecked")
    public List<Ticket> getTicketsByJiraTicketKey(String jiraTicketKey) {
        return getSession().createQuery("from Ticket t where t.jiraTicketKey = ? order by jiraTicketKey").setString(0, jiraTicketKey).list();
    }

    /**
     * Gets a ProjectID by Jira Project ID and customerorder ID.
     */
    @SuppressWarnings("unchecked")
    public List<Ticket> getTicketsByCustomerorderID(long customerorderId) {
        return getSession().createQuery("from Ticket t where t.suborder.customerorder.id = ? order by jiraTicketKey")
                .setLong(0, customerorderId).list();
    }

    public Ticket getTicketByJiraTicketKeyAndDate(String jiraTicketKey, Date date) {
        @SuppressWarnings("unchecked")
        List<Ticket> list = getSession().createQuery("from Ticket t " +
                "where t.jiraTicketKey = ? and t.fromDate <= ? and  (t.untilDate is null or t.untilDate >= ?) order by jiraTicketKey")
                .setString(0, jiraTicketKey).setDate(1, date).setDate(2, date).list();
        return !list.isEmpty() ? list.get(0) : null;
    }

    public void save(Ticket ticket) {
        Session session = getSession();

        if (session.contains(ticket)) {
            // existing and attached to session
            session.saveOrUpdate(ticket);
        } else {
            if (ticket.getId() != 0L) {
                // existing but detached from session
                session.merge(ticket);
            } else {
                // new object -> persist it!
                session.saveOrUpdate(ticket);
            }
        }
        session.flush();
    }

    public boolean deleteTicket(Ticket ticket) {
        //check if there are timereports related to this ticket - if so, cannot delete ticket
        List<Timereport> timereports = timereportDAO.getTimereportsByTicketID(ticket.getId());
        boolean deleted = false;
        if (timereports.isEmpty()) {
            getSession().delete(ticket);
            getSession().flush();
            deleted = true;
        }
        return deleted;
    }

}
