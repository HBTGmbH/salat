package org.tb.web.action.admin;

import java.io.IOException;
import java.sql.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.util.MessageResources;
import org.tb.GlobalConstants;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Suborder;
import org.tb.bdom.Ticket;
import org.tb.bdom.TicketViewDecorator;
import org.tb.bdom.Timereport;
import org.tb.bdom.Worklog;
import org.tb.helper.AtlassianOAuthClient;
import org.tb.helper.JiraConnectionOAuthHelper;
import org.tb.helper.JiraSalatHelper;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.persistence.TicketDAO;
import org.tb.persistence.TimereportDAO;
import org.tb.persistence.WorklogDAO;
import org.tb.persistence.WorklogMemoryDAO;
import org.tb.web.action.LoginRequiredAction;
import org.tb.web.form.ShowTicketsForm;

/**
 * action class for showing all Jira-Tickets
 * 
 * @author mgo
 * 
 */
public class ShowTicketsAction extends LoginRequiredAction {
    
	private TicketDAO ticketDAO;
	private WorklogDAO worklogDAO;
    private SuborderDAO suborderDAO;
    private TimereportDAO timereportDAO;
    private CustomerorderDAO customerorderDAO;
    private EmployeeorderDAO employeeorderDAO;
    private WorklogMemoryDAO worklogMemoryDAO;
    
    
    public void setTicketDAO(TicketDAO ticketDAO) {
    	this.ticketDAO = ticketDAO;
    }
    public void setWorklogDAO(WorklogDAO worklogDAO) {
    	this.worklogDAO = worklogDAO;
    }
    public void setSuborderDAO(SuborderDAO suborderDAO) {
    	this.suborderDAO = suborderDAO;
    }
    public void setTimereportDAO(TimereportDAO timereportDAO) {
    	this.timereportDAO = timereportDAO;
    }
    public void setCustomerorderDAO(CustomerorderDAO customerorderDAO) {
        this.customerorderDAO = customerorderDAO;
    }
    public void setEmployeeorderDAO(EmployeeorderDAO employeeorderDAO) {
        this.employeeorderDAO = employeeorderDAO;
    }
    public void setWorklogMemoryDAO(WorklogMemoryDAO worklogMemoryDAO) {
        this.worklogMemoryDAO = worklogMemoryDAO;
    }
    
    
    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping,
            ActionForm form, HttpServletRequest request,
            HttpServletResponse response) throws IOException {
		
    	Employee loginEmployee = (Employee)request.getSession().getAttribute("loginEmployee");
    	
    	if (loginEmployee.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_BL) || loginEmployee.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_PV) 
        		|| loginEmployee.getStatus().equals( GlobalConstants.EMPLOYEE_STATUS_ADM)) {
    		request.getSession().setAttribute("employeeAuthorized", true);
    	} else {
    		request.getSession().setAttribute("employeeAuthorized", false);
    	}
    	
    	
    	if (request.getSession().getAttribute("customerOrders") == null) {
    		
	    	// Filter customerorder with tickets
	    	List<Customerorder> all_orders = customerorderDAO.getCustomerorders();
	    	List<Customerorder> orders = new ArrayList<Customerorder>();
	    	
	    	for (Customerorder order: all_orders) {
	    		if (order.getProjectIDs().size() > 0) {
	    			List<Ticket> tickets = ticketDAO.getTicketsByCustomerorderID(order.getId());
	    			if (tickets.size() > 0) {
						orders.add(order);
					}
	    		} 
			}
			request.getSession().setAttribute("customerOrders", orders);
    	}
    	
    	
    	ShowTicketsForm ticketsForm = (ShowTicketsForm)form;
		Long currentOrderId = 0L;
		Long currentSuborderId = 0L;
		
		if (ticketsForm != null) {
			currentOrderId = ticketsForm.getOrderId();
		}
		if (currentOrderId == null || currentOrderId == 0) {
			if (request.getSession().getAttribute("currentOrderId") != null) {
				currentOrderId = (Long) request.getSession().getAttribute("currentOrderId");
			}
		}
		if (currentOrderId == null || currentOrderId == 0) {
			currentOrderId = -1l;
		}
		if (ticketsForm != null) {
			ticketsForm.setOrderId(currentOrderId);
			request.getSession().setAttribute("currentOrderId", currentOrderId);
		}
		
		String ticketId;
		MessageResources messageResources = getResources(request);
		
		if ((ticketId = request.getParameter("setSuborder")) != null) {
			
			//get the newly picked Suborder from the Dropdown list
			long newSubOrderId = ticketsForm.getNewSuborderId();			
			//get the Ticket Id
			Long ticketToSetL = Long.parseLong(ticketId);				
			//get the right Ticket from the Decorators
			TicketViewDecorator ticketVD = ticketsForm.getTicketDecoratorWithId(ticketToSetL);
			
			ticketVD.disableError();
			
			//save the picked Suborder to the Decorator
			if (ticketVD != null) {				
				ticketVD.setPickedSuborderId(newSubOrderId);
			}
			//if NOT the the original suborder was picked
			if (newSubOrderId != ticketVD.getSuborderId()) {
				checkEmployeeOrders(ticketVD, messageResources);
			}
			
//			for(TicketViewDecorator tv: ticketsForm.getTicketDecorators()) {
//				if(ticketVD.getId() != tv.getId()) {
//					if (ticketVD.getJiraTicketKey().equals(tv.getJiraTicketKey())) {
//						if(ticketVD.getPickedSuborderId() == tv.getSuborderId()) {
//							ticketVD.setError("Ticket is allready assosiated with this Suborder");
//							return mapping.findForward("success");
//						}
//					}
//				}
//			}
			
			request.getSession().setAttribute("tickets", ticketsForm.getTicketDecorators());
		}
		
		else if ((ticketId = request.getParameter("setDate")) != null) {
			
			//get the Ticket Id
			Long ticketToSetL = Long.parseLong(ticketId);			
			//get the right Ticket from the Decorators
			TicketViewDecorator ticketVD = ticketsForm.getTicketDecoratorWithId(ticketToSetL);
			
			if (ticketVD != null) {
				//if dates changed
				if (!ticketVD.getPickedFromDate().toString().equals(ticketsForm.getFromDate())
				 || !ticketVD.getPickedUntilDate().toString().equals(ticketsForm.getUntilDate())) {
					
					String fromDate = ticketsForm.getFromDate();
					String untilDate = ticketsForm.getUntilDate();
					
					SimpleDateFormat simpleDateFormat = new SimpleDateFormat(GlobalConstants.DEFAULT_DATE_FORMAT);
					try {
						if (fromDate != null) {
							Date sqlFromDate = new Date(simpleDateFormat.parse(fromDate).getTime());
							ticketVD.setPickedFromDate(sqlFromDate);
						}
						if (untilDate != null) {
							Date sqlUntilDate = new Date(simpleDateFormat.parse(untilDate).getTime());
							ticketVD.setPickedUntilDate(sqlUntilDate);
						}
					} catch (ParseException e) {
						//this exception should never happen 
						throw new RuntimeException("horrible exception - this should never happen!");
					}
				}
				ticketVD.disableError();
				request.getSession().setAttribute("tickets", ticketsForm.getTicketDecorators());
			}
		}
		
		else if ((ticketId = request.getParameter("save")) != null) {
			
			//get the Ticket Id that we need to Save
			Long ticketToSaveL = Long.parseLong(ticketId);

			//get the Decorator for this Ticket
			TicketViewDecorator decorator = ticketsForm.getTicketDecoratorWithId(ticketToSaveL);
			
			//get the new Suborder for this Ticket
			Suborder pickedSuborder = suborderDAO.getSuborderById(decorator.getPickedSuborderId());
			
			//check if the Dates are inside the Suborder-Dates
			if (decorator.getPickedFromDate().before(decorator.getPickedUntilDate())
					&& decorator.getPickedFromDate().compareTo(pickedSuborder.getFromDate()) >= 0
					&& decorator.getPickedUntilDate().compareTo(pickedSuborder.getUntilDate()) <= 0) {
				
				JiraConnectionOAuthHelper jcHelper = new JiraConnectionOAuthHelper(loginEmployee.getSign());
				List<Timereport> timereports = timereportDAO.getTimereportsByTicketID(ticketToSaveL);
				
				//if dates have been changed
				if (decorator.getPickedFromDate() != decorator.getFromDate() || 
					decorator.getPickedUntilDate() != decorator.getUntilDate()) {
					
					//check if timereports are inside the new Ticket-Dates
					for(Timereport tr: timereports) {
						if (tr.getReferenceday().getRefdate().before(decorator.getPickedFromDate()) 
						 || tr.getReferenceday().getRefdate().after(decorator.getPickedUntilDate())) {
							
							decorator.setError(messageResources.getMessage("form.showTickets.error.outofrange.reports"));
							return mapping.findForward("success");
						}
					}
					
					//set dates to the ticket
					decorator.setFromDate(decorator.getPickedFromDate());
					decorator.setUntilDate(decorator.getPickedUntilDate());
				}
				
				//if Suborder has changed - assosiate Timereports with the new Suborder
				if (decorator.getPickedSuborderId() != decorator.getSuborderId()) {
					
					for(Timereport tr: timereports) {
						if(tr.getSuborder().getId() == decorator.getSuborder().getId()) {
							tr.setSuborder(pickedSuborder);
							Worklog salatWorklog = worklogDAO.getWorklogByTimereportID(tr.getId());							
							if (salatWorklog != null) {
								int responseUpdateWorklog = 0;
								String jiraAccessToken = tr.getEmployeecontract().getEmployee().getJira_oauthtoken();
								if (jiraAccessToken != null) {
									AtlassianOAuthClient.setAccessToken(jiraAccessToken);
									responseUpdateWorklog = jcHelper.updateWorklog(tr, salatWorklog.getJiraTicketKey(), salatWorklog.getJiraWorklogID());
								} 
								if (responseUpdateWorklog == 404) { // if worklog not found / has been deleted
									int[] create_status = jcHelper.createWorklog(tr, salatWorklog.getJiraTicketKey());
									if (create_status[0] != 200) {
										decorator.setError(messageResources.getMessage("form.general.error.jiraWorklogsNotFoundAndCreateNewFailed"));
									} else {
										salatWorklog.setJiraWorklogID(create_status[1]);
										salatWorklog.setType("updated");
										salatWorklog.setUpdatecounter(salatWorklog.getUpdatecounter() + 1);
										worklogDAO.save(salatWorklog);
									}
								}
								else if (responseUpdateWorklog != 200) {
									if (responseUpdateWorklog == 0) {
										decorator.setError("Könnte eine oder mehrere Worklogs nicht editieren weil bei dem zuständigen Benutzer der Jira-Access-Token fehlt.");
									} else {
										decorator.setError(messageResources.getMessage("form.general.error.jiraworklog.updateerror"));
									}
									try {
										JiraSalatHelper.saveFailedWorklog(worklogMemoryDAO, timereportDAO, null, salatWorklog.getJiraTicketKey(), 0, GlobalConstants.CREATE_WORKLOG);
									} catch (Exception e) {
				                		decorator.setError(decorator.getErrorMessage() + "\n" + messageResources.getMessage("form.general.error.worklogmemoryfailed"));
				                	}
									return mapping.findForward("success");
								}
							}
							
						}
					}
					
					//set Suborder to the Ticket
					decorator.setSuborder(pickedSuborder);
				}
				
				//save the Ticket
				ticketDAO.save(decorator.getTicket());		
				decorator.disableError();
			} else {
				decorator.setError(messageResources.getMessage("form.showTickets.error.outofrange.suborder") + 
						" (" + pickedSuborder.getFromDate() + " - " + pickedSuborder.getUntilDate() + ")");
			}
			
		}
		
		else if (request.getParameter("task") == null || request.getParameter("task").equals("refresh")) {
		
			if (currentOrderId > 0) {
				request.getSession().setAttribute("currentOrderDescr", customerorderDAO.getCustomerorderById(currentOrderId).getSignAndDescription());
			}
			
			if (currentOrderId == -1L) {
				ticketsForm.setSuborderId(-1);
			}
			
			//if Order Set -> get the Suborders for this order and write to Session	
			if (currentOrderId != -1L) {
				long customerOrderId = customerorderDAO.getCustomerorderById(ticketsForm.getOrderId()).getId();
				List<Suborder> suborders = suborderDAO.getSubordersByCustomerorderId(customerOrderId);
				request.getSession().setAttribute("suborders", suborders);
			}
			//if not -> Set an empty list
			else {
				request.getSession().setAttribute("suborders", new ArrayList<Suborder>());
			}
			
			//write actual Suborder to Session 			
			currentSuborderId = ticketsForm.getSuborderId();
			request.getSession().setAttribute("currentSuborderId", currentSuborderId);		
			
			//if Suborder Set get tickets by Suborder
			if (currentSuborderId > 0) {
		    	
	    		List<Ticket> tickets = ticketDAO.getTicketsBySuborderID(currentSuborderId);		    		
	    		setTicketViewDecorators(tickets, request, ticketsForm);					
			}
			//if Suborder NOT Set get tickets by Order
			else {		    	
				List<Ticket> tickets = ticketDAO.getTicketsByCustomerorderID(currentOrderId);
				setTicketViewDecorators(tickets, request, ticketsForm);
			}
		} 
		
		return mapping.findForward("success");
	}	
    
    // Wenn ein Unterauftrag ausgewählt wurde, für den nicht für alle Mitarbeiter (die schon auf den dummy-Auftrag für das Ticket gebucht haben) 
    // MA-Aufträge vorhanden sind, wird nicht gespeichert sondern Fehlermeldung angezeigt.
    private boolean checkEmployeeOrders(TicketViewDecorator ticketVD, MessageResources messageResources) {
		
		Suborder suborder = suborderDAO.getSuborderById(ticketVD.getSuborderId());		
		List<Employeeorder> employeeOrders = suborder.getEmployeeorders();
		
		//für alle Mitarbeiteraufträge vom aktuellen(Dummy-) Unterauftrag		
		for (Employeeorder employeeorder: employeeOrders) {
			//wenn auf dem Mittarbeiterauftrag gebucht wurde
			if (timereportDAO.getTimereportsByEmployeeOrderId(employeeorder.getId()).isEmpty() == false) {
				//überprüfe ob es auch für diesen Mitarbeiter ein Mitarbeiterauftrag bei dem neuausgewählten Unterauftrag existiert
				if (employeeorderDAO.getEmployeeOrdersByEmployeeContractIdAndSuborderId(employeeorder.getEmployeecontract().getId(), ticketVD.getPickedSuborderId()).isEmpty()) {
					ticketVD.setError(messageResources.getMessage("form.showTickets.error.employeeorders"));
					return false;
				}
			}
		}
		return true;
	}

	private void setTicketViewDecorators(List<Ticket> tickets, HttpServletRequest request, ShowTicketsForm ticketsForm) {
    	
    	List<TicketViewDecorator> decorators = new LinkedList<TicketViewDecorator>();
		
		for (int i = 0; i < tickets.size(); i++) {
			Ticket ticket = tickets.get(i);
			TicketViewDecorator decorator = new TicketViewDecorator(ticket);
			decorator.setIndex(i+1);
			decorator.setPickedSuborderId(ticket.getSuborder().getId());
			decorator.setPickedFromDate(ticket.getFromDate());
			decorator.setPickedUntilDate(ticket.getUntilDate());
			decorators.add(decorator);
		}
		ticketsForm.setTicketDecorators(decorators);
		request.getSession().setAttribute("tickets", decorators);
	}
}
