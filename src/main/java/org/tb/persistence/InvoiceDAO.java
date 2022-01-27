package org.tb.persistence;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tb.bdom.Employee;
import org.tb.bdom.Invoice;

import java.util.List;

@Component
public class InvoiceDAO extends AbstractDAO {

    @Autowired
    public InvoiceDAO(SessionFactory sessionFactory) {
        super(sessionFactory);
    }

    /**
     * Gets the Invoice for the given id.
     */
    public Invoice getInvoiceById(long id) {
        return (Invoice) getSession().createQuery("from Invoice i where i.id = ?").setLong(0, id).uniqueResult();
    }

    /**
     * Get a list of all Invoices.
     */
    @SuppressWarnings("unchecked")
    public List<Invoice> getInvoices() {
        return getSession().createQuery("from Invoice").list();
    }

    /**
     * Calls {@link InvoiceDAO#save(Invoice, Employee)} with {@link Employee} = null.
     */
    public void save(Invoice i) {
        save(i, null);
    }

    /**
     * Saves the given invoice and sets creation-/update-user and creation-/update-date.
     */
    public void save(Invoice invoice, Employee loginEmployee) {
        if (loginEmployee == null) {
            throw new RuntimeException("the login-user must be passed to the db");
        }
        Session session = getSession();
        java.util.Date creationDate = invoice.getCreated();
        if (creationDate == null) {
            invoice.setCreated(new java.util.Date());
            invoice.setCreatedby(loginEmployee.getSign());
        } else {
            invoice.setLastupdate(new java.util.Date());
            invoice.setLastupdatedby(loginEmployee.getSign());
            Integer updateCounter = invoice.getUpdatecounter();
            updateCounter = (updateCounter == null) ? 1 : updateCounter + 1;
            invoice.setUpdatecounter(updateCounter);
        }
        session.saveOrUpdate(invoice);
        session.flush();
    }

    /**
     * Deletes the given Invoice.
     */
    public boolean deleteInvoiceById(long inId) {
        List<Invoice> allInvoices = getInvoices();
        Invoice inToDelete = getInvoiceById(inId);
        boolean inDeleted = false;

        for (Invoice in : allInvoices) {
            if (in.getId() == inToDelete.getId()) {
                Session session = getSession();
                session.delete(inToDelete);
                session.flush();
                inDeleted = true;

                break;
            }
        }

        return inDeleted;
    }

}
