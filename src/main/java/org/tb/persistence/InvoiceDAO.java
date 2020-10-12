package org.tb.persistence;

import java.util.List;

import org.hibernate.Session;
import org.tb.bdom.Employee;
import org.tb.bdom.Invoice;

/**
 * DAO class for 'Invoice'
 * 
 * @author oda
 *
 */
public class InvoiceDAO extends AbstractDAO {

	/**
	 * Gets the Invoice for the given id.
	 * 
	 * @param long id
	 * 
	 * @return Invoice
	 */
	public Invoice getInvoiceById(long id) {
		return (Invoice) getSession().createQuery("from Invoice in where in.id = ?").setLong(0, id).uniqueResult();
	}
	
	/**
	 * Get a list of all Invoices.
	 * 
	 * @return List<Invoice> 
	 */
	@SuppressWarnings("unchecked")
	public List<Invoice> getInvoices() {
		return getSession().createQuery("from Invoice").list();
	}	
	
	/**
	 * Calls {@link InvoiceDAO#save(Invoice, Employee)} with {@link Employee} = null.
	 * @param i
	 */
	public void save(Invoice i) {
		save(i, null);
	}
	
	/**
	 * Saves the given invoice and sets creation-/update-user and creation-/update-date.
	 * 
	 * @param Invoice invoice
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
			updateCounter = (updateCounter == null) ? 1 : updateCounter +1;
			invoice.setUpdatecounter(updateCounter);
		}
		session.saveOrUpdate(invoice);
		session.flush();
	}
	
	/**
	 * Deletes the given Invoice.
	 * 
	 * @param long inId
	 * 
	 * @return boolean
	 */
	public boolean deleteInvoiceById(long inId) {
		List<Invoice> allInvoices = getInvoices();
		Invoice inToDelete = getInvoiceById(inId);
		boolean inDeleted = false;
		
		for (Invoice in : allInvoices) {
			if(in.getId() == inToDelete.getId()) {				
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
