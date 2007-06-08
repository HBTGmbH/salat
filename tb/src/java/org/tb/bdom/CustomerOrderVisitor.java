package org.tb.bdom;

/**
 * TODO comment
 * 
 * @author th
 */
public interface CustomerOrderVisitor {
	
	/**
	 * TODO comment
	 * 
	 * @param customerorder
	 */
	void visitCustomerOrder(Customerorder customerorder);
	
	/**
	 * TODO comment
	 *  
	 * @param suborder
	 */
	void visitSuborder(Suborder suborder);

}
