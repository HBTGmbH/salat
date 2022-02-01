package org.tb.action.admin;

import org.apache.struts.action.ActionForm;
import org.tb.GlobalConstants;
import org.tb.action.LoginRequiredAction;
import org.tb.bdom.EmployeeOrderViewDecorator;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Suborder;
import org.tb.persistence.*;
import org.tb.web.form.AddEmployeeOrderForm;
import org.tb.web.form.ShowEmployeeOrderForm;

import javax.servlet.http.HttpServletRequest;
import java.text.SimpleDateFormat;
import java.util.LinkedList;
import java.util.List;

public abstract class EmployeeOrderAction<F extends ActionForm> extends LoginRequiredAction<F> {

    /**
     * Refreshes the list of employee orders and stores it in the session.
     */
    protected void refreshEmployeeSubOrders(HttpServletRequest request,
                                            ShowEmployeeOrderForm orderForm, SuborderDAO suborderDAO, CustomerorderDAO customerorderDAO, boolean onlyValid) {

        Long suborderId = orderForm.getSuborderId();

        if ((orderForm.getEmployeeContractId() > -1) && ((Long) request.getSession().getAttribute("currentOrderId") != -1L)) {
            request.getSession().setAttribute("suborders", suborderDAO.getSubordersByEmployeeContractIdAndCustomerorderId(orderForm.getEmployeeContractId(), customerorderDAO
                    .getCustomerorderById(orderForm.getOrderId())
                    .getId(), onlyValid));
        } else {
            request.getSession().setAttribute("suborders", suborderDAO.getSubordersByCustomerorderId(orderForm.getOrderId(), onlyValid));
        }

        request.getSession().setAttribute("curretntSuborder", suborderId);
    }

    protected void refreshEmployeeOrders(HttpServletRequest request,
                                         ShowEmployeeOrderForm orderForm, EmployeeorderDAO employeeorderDAO,
                                         EmployeecontractDAO employeecontractDAO, TimereportDAO timereportDAO) {

        Employeecontract loginEmployeeContract = (Employeecontract) request.getSession().getAttribute("loginEmployeeContract");
        Employeecontract currentEmployeeContract = (Employeecontract) request.getSession().getAttribute("currentEmployeeContract");

        long employeeContractId = 0L;
        Long orderId = 0L;

        if (orderForm != null) {
            employeeContractId = orderForm.getEmployeeContractId();
            orderId = orderForm.getOrderId();
        }

        if (employeeContractId == 0) {
            if (currentEmployeeContract != null) {
                employeeContractId = currentEmployeeContract.getId();
            } else {
                employeeContractId = loginEmployeeContract.getId();
            }
        }

        if (orderId == 0) {
            if (request.getSession().getAttribute("currentOrderId") != null) {
                orderId = (Long) request.getSession().getAttribute("currentOrderId");
            }
        }
        if (orderId == null || orderId == 0) {
            orderId = -1L;
        }

        request.getSession().setAttribute("currentOrderId", orderId);

        if (orderForm != null) {
            orderForm.setEmployeeContractId(employeeContractId);
            orderForm.setOrderId(orderId);

            String filter = null;
            Boolean show = null;

            if ((request.getParameter("task") != null) && (request.getParameter("task").equals("refresh"))) {
                filter = orderForm.getFilter();
                request.getSession().setAttribute("employeeOrderFilter", filter);

                show = orderForm.getShow();
                request.getSession().setAttribute("employeeOrderShow", show);

            } else {
                if (request.getSession().getAttribute("employeeOrderFilter") != null) {
                    filter = (String) request.getSession().getAttribute("employeeOrderFilter");
                    orderForm.setFilter(filter);
                }
                if (request.getSession().getAttribute("employeeOrderShow") != null) {
                    show = (Boolean) request.getSession().getAttribute("employeeOrderShow");
                    orderForm.setShow(show);
                }
            }

            boolean showActualHours = orderForm.getShowActualHours();
            request.getSession().setAttribute("showActualHours", showActualHours);

            orderForm.setFilter(filter);
            orderForm.setOrderId(orderId);
            orderForm.setShow(show);
            orderForm.setShowActualHours(showActualHours);

            if (showActualHours) {
                /* show actual hours */
                List<Employeeorder> employeeOrders = employeeorderDAO.getEmployeeordersByFilters(show, filter, employeeContractId, orderId);
                List<EmployeeOrderViewDecorator> decorators = new LinkedList<EmployeeOrderViewDecorator>();

                for (Employeeorder employeeorder : employeeOrders) {
                    EmployeeOrderViewDecorator decorator = new EmployeeOrderViewDecorator(timereportDAO, employeeorder);
                    decorators.add(decorator);
                }
                request.getSession().setAttribute("employeeorders", decorators);
            } else {
                request.getSession().setAttribute("employeeorders", employeeorderDAO.getEmployeeordersByFilters(show, filter, employeeContractId, orderId));
            }
        }

        if (employeeContractId == -1) {
            request.getSession().setAttribute("currentEmployeeId", loginEmployeeContract.getEmployee().getId());
            request.getSession().setAttribute("currentEmployeeContract", null);
        } else {
            currentEmployeeContract = employeecontractDAO.getEmployeeContractById(employeeContractId);
            request.getSession().setAttribute("currentEmployeeId", currentEmployeeContract.getEmployee().getId());
            request.getSession().setAttribute("currentEmployeeContract", currentEmployeeContract);
        }
    }


    protected void refreshEmployeeOrdersAndSuborders(HttpServletRequest request,
                                                     ShowEmployeeOrderForm orderForm, EmployeeorderDAO employeeorderDAO,
                                                     EmployeecontractDAO employeecontractDAO, TimereportDAO timereportDAO, SuborderDAO suborderDAO, CustomerorderDAO customerorderDAO,
                                                     boolean onlyValid) {

        Employeecontract loginEmployeeContract = (Employeecontract) request.getSession().getAttribute("loginEmployeeContract");
        Employeecontract currentEmployeeContract = (Employeecontract) request.getSession().getAttribute("currentEmployeeContract");

        Long employeeContractId = 0L;
        Long orderId = 0L;
        long suborderId;

        if (orderForm != null) {
            employeeContractId = orderForm.getEmployeeContractId();
            orderId = orderForm.getOrderId();
        }

        if (employeeContractId == 0) {
            if (currentEmployeeContract != null) {
                employeeContractId = currentEmployeeContract.getId();
            } else {
                employeeContractId = loginEmployeeContract.getId();
            }
        }

        if (orderId == 0) {
            if (request.getSession().getAttribute("currentOrderId") != null) {
                orderId = (Long) request.getSession().getAttribute("currentOrderId");
            }
        }

        if (orderId == null || orderId == 0) {
            orderId = -1L;
        }

        Long currentOrderId = (Long) request.getSession().getAttribute("currentOrderId");
        if (currentOrderId == null || currentOrderId != -2) { //has been deleted in DeleteCustomerOrderAction
            request.getSession().setAttribute("currentOrderId", orderId);
        }

        if (orderForm != null) {
            orderForm.setEmployeeContractId(employeeContractId);
            orderForm.setOrderId(orderId);

            if ((Long) request.getSession().getAttribute("currentOrderId") == -1L) {
                orderForm.setSuborderId(-1);
            }

            suborderId = orderForm.getSuborderId();

            if (request.getSession().getAttribute("currentOrderId") != null) {

                if ((orderForm.getEmployeeContractId() > -1) && ((Long) request.getSession().getAttribute("currentOrderId") != -1L)) {

                    request.getSession().setAttribute("suborders", suborderDAO.getSubordersByEmployeeContractIdAndCustomerorderId(
                            orderForm.getEmployeeContractId(), customerorderDAO.getCustomerorderById(orderForm.getOrderId()).getId(), onlyValid));
                } else {
                    request.getSession().setAttribute("suborders", suborderDAO.getSubordersByCustomerorderId(orderForm.getOrderId(), onlyValid));
                }
                // actual suborder
                request.getSession().setAttribute("currentSub", suborderId);
            }

            String filter = null;
            Boolean show = null;

            if ((request.getParameter("task") != null) && (request.getParameter("task").equals("refresh"))) {
                filter = orderForm.getFilter();

                request.getSession().setAttribute("employeeOrderFilter", filter);

                show = orderForm.getShow();
                request.getSession().setAttribute("employeeOrderShow", show);

            } else {
                if (request.getSession().getAttribute("employeeOrderFilter") != null) {
                    filter = (String) request.getSession().getAttribute("employeeOrderFilter");
                    orderForm.setFilter(filter);
                }
                if (request.getSession().getAttribute("employeeOrderShow") != null) {
                    show = (Boolean) request.getSession().getAttribute("employeeOrderShow");
                    orderForm.setShow(show);
                }
            }

            boolean showActualHours = orderForm.getShowActualHours();
            request.getSession().setAttribute("showActualHours", showActualHours);

            orderForm.setFilter(filter);
            orderForm.setOrderId(orderId);
            orderForm.setSuborderId(suborderId);
            orderForm.setShow(show);
            orderForm.setShowActualHours(showActualHours);

            if (showActualHours) {
                /* show actual hours */
                List<Employeeorder> employeeOrders =
                        employeeorderDAO.getEmployeeordersByFilters(show, filter, employeeContractId, orderId, suborderId);
                List<EmployeeOrderViewDecorator> decorators = new LinkedList<EmployeeOrderViewDecorator>();

                for (Employeeorder employeeorder : employeeOrders) {
                    EmployeeOrderViewDecorator decorator = new EmployeeOrderViewDecorator(timereportDAO, employeeorder);
                    decorators.add(decorator);
                }
                request.getSession().setAttribute("employeeorders", decorators);
            } else {

                List<Employeeorder> leo = employeeorderDAO.getEmployeeordersByFilters(show, filter, employeeContractId, orderId, suborderId);

                request.getSession().setAttribute("employeeorders", leo);
            }
        }

        if (employeeContractId == -1) {
            request.getSession().setAttribute("currentEmployeeId", loginEmployeeContract.getEmployee().getId());
            request.getSession().setAttribute("currentEmployeeContract", null);
        } else {
            currentEmployeeContract = employeecontractDAO.getEmployeeContractById(employeeContractId);
            request.getSession().setAttribute("currentEmployeeId", currentEmployeeContract.getEmployee().getId());
            request.getSession().setAttribute("currentEmployeeContract", currentEmployeeContract);
        }
    }

    /**
     * Sets the from and until date in the form
     */
    protected void setFormDates(HttpServletRequest request,
                                AddEmployeeOrderForm employeeOrderForm) {
        Employeecontract employeecontract = (Employeecontract) request.getSession().getAttribute("currentEmployeeContract");
        Suborder suborder = (Suborder) request.getSession().getAttribute("selectedsuborder");

        java.util.Date ecFromDate = null;
        java.util.Date ecUntilDate = null;
        java.util.Date soFromDate = null;
        java.util.Date soUntilDate = null;

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(GlobalConstants.DEFAULT_DATE_FORMAT);

        if (employeecontract != null) {
            ecFromDate = employeecontract.getValidFrom();
            ecUntilDate = employeecontract.getValidUntil();
        }
        if (suborder != null) {
            soFromDate = suborder.getFromDate();
            soUntilDate = suborder.getUntilDate();
        }

        // set from date
        if (ecFromDate != null && soFromDate != null) {
            if (ecFromDate.before(soFromDate)) {
                employeeOrderForm.setValidFrom(simpleDateFormat.format(soFromDate));
            } else {
                employeeOrderForm.setValidFrom(simpleDateFormat.format(ecFromDate));
            }
        } else if (ecFromDate != null) {
            employeeOrderForm.setValidFrom(simpleDateFormat.format(ecFromDate));
        } else if (soFromDate != null) {
            employeeOrderForm.setValidFrom(simpleDateFormat.format(soFromDate));
        }

        // set until date
        if (ecUntilDate != null && soUntilDate != null) {
            if (ecUntilDate.after(soUntilDate)) {
                employeeOrderForm.setValidUntil(simpleDateFormat.format(soUntilDate));
            } else {
                employeeOrderForm.setValidUntil(simpleDateFormat.format(ecUntilDate));
            }
        } else if (ecUntilDate != null) {
            employeeOrderForm.setValidUntil(simpleDateFormat.format(ecUntilDate));
        } else if (soUntilDate != null) {
            employeeOrderForm.setValidUntil(simpleDateFormat.format(soUntilDate));
        } else {
            employeeOrderForm.setValidUntil("");
        }
    }

}
