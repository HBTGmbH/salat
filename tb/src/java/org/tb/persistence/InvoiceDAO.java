package org.tb.persistence;

import java.util.Iterator;
import java.util.List;

import org.hibernate.Session;
import org.springframework.orm.hibernate3.support.HibernateDaoSupport;
import org.tb.bdom.Invoice;

/**
 * DAO class for 'Invoice'
 * 
 * @author oda
 *
 */
public class InvoiceDAO extends HibernateDaoSupport {

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
	public List<Invoice> getInvoices() {
		return getSession().createQuery("from Invoice").list();
	}	
	
	/**
	 * Saves the given invoice.
	 * 
	 * @param Invoice i
	 */
	public void save(Invoice i) {
		Session session = getSession();
		session.saveOrUpdate(i);
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
		
		for (Iterator iter = allInvoices.iterator(); iter.hasNext();) {
			Invoice in = (Invoice) iter.next();
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
