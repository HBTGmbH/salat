package org.tb.order.action;

import static org.tb.common.util.DateUtils.format;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.LinkedList;
import java.util.List;
import org.apache.struts.action.ActionForm;
import org.tb.auth.struts.LoginRequiredAction;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeecontractService;
import org.tb.order.domain.Employeeorder;
import org.tb.order.domain.Suborder;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.EmployeeorderService;
import org.tb.order.service.SuborderService;
import org.tb.order.viewhelper.EmployeeOrderViewDecorator;

public abstract class EmployeeOrderAction<F extends ActionForm> extends LoginRequiredAction<F> {

    protected void refreshEmployeeOrders(HttpServletRequest request,
                                         ShowEmployeeOrderForm orderForm, EmployeeorderService employeeorderService,
                                         EmployeecontractService employeecontractService) {

        Employeecontract loginEmployeeContract = (Employeecontract) request.getSession().getAttribute("loginEmployeeContract");
        Employeecontract currentEmployeeContract = (Employeecontract) request.getSession().getAttribute("currentEmployeeContract");

        long employeeContractId = 0L;
        Long customerOrderId = 0L;

        if (orderForm != null) {
            employeeContractId = orderForm.getEmployeeContractId();
            customerOrderId = orderForm.getOrderId();
        }

        if (employeeContractId == 0) {
            if (currentEmployeeContract != null) {
                employeeContractId = currentEmployeeContract.getId();
            } else {
                employeeContractId = loginEmployeeContract.getId();
            }
        }

        if (customerOrderId == 0) {
            if (request.getSession().getAttribute("currentOrderId") != null) {
                customerOrderId = (Long) request.getSession().getAttribute("currentOrderId");
            }
        }
        if (customerOrderId == null || customerOrderId == 0) {
            customerOrderId = -1L;
        }

        request.getSession().setAttribute("currentOrderId", customerOrderId);

        if (orderForm != null) {
            orderForm.setEmployeeContractId(employeeContractId);
            orderForm.setOrderId(customerOrderId);

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
            orderForm.setOrderId(customerOrderId);
            orderForm.setShow(show);
            orderForm.setShowActualHours(showActualHours);

            if (showActualHours) {
                /* show actual hours */
                List<Employeeorder> employeeOrders = employeeorderService.getEmployeeordersByFilters(show, filter, employeeContractId, customerOrderId);
                List<EmployeeOrderViewDecorator> decorators = new LinkedList<EmployeeOrderViewDecorator>();

                for (Employeeorder employeeorder : employeeOrders) {
                    EmployeeOrderViewDecorator decorator = new EmployeeOrderViewDecorator(employeeorderService, employeeorder);
                    decorators.add(decorator);
                }
                request.getSession().setAttribute("employeeorders", decorators);
            } else {
                request.getSession().setAttribute("employeeorders", employeeorderService.getEmployeeordersByFilters(show, filter, employeeContractId, customerOrderId));
            }
        }

        if (employeeContractId == -1) {
            request.getSession().setAttribute("currentEmployeeId", loginEmployeeContract.getEmployee().getId());
            request.getSession().setAttribute("currentEmployeeContract", null);
        } else {
            currentEmployeeContract = employeecontractService.getEmployeecontractById(employeeContractId);
            request.getSession().setAttribute("currentEmployeeId", currentEmployeeContract.getEmployee().getId());
            request.getSession().setAttribute("currentEmployeeContract", currentEmployeeContract);
        }
    }


    protected void refreshEmployeeOrdersAndSuborders(HttpServletRequest request,
                                                     ShowEmployeeOrderForm orderForm, EmployeeorderService employeeorderService,
                                                     EmployeecontractService employeecontractService, SuborderService suborderService,
                                                     CustomerorderService customerorderService, boolean onlyValid) {

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

                    request.getSession().setAttribute("suborders", suborderService.getSubordersByEmployeeContractIdAndCustomerorderId(
                            orderForm.getEmployeeContractId(), customerorderService.getCustomerorderById(orderForm.getOrderId()).getId(), onlyValid));
                } else {
                    request.getSession().setAttribute("suborders", suborderService.getSubordersByCustomerorderId(orderForm.getOrderId(), onlyValid));
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
                        employeeorderService.getEmployeeordersByFilters(show, filter, employeeContractId, orderId, suborderId);
                List<EmployeeOrderViewDecorator> decorators = new LinkedList<EmployeeOrderViewDecorator>();

                for (Employeeorder employeeorder : employeeOrders) {
                    EmployeeOrderViewDecorator decorator = new EmployeeOrderViewDecorator(employeeorderService, employeeorder);
                    decorators.add(decorator);
                }
                request.getSession().setAttribute("employeeorders", decorators);
            } else {

                List<Employeeorder> leo = employeeorderService.getEmployeeordersByFilters(show, filter, employeeContractId, orderId, suborderId);

                request.getSession().setAttribute("employeeorders", leo);
            }
        }

        if (employeeContractId == -1) {
            request.getSession().setAttribute("currentEmployeeId", loginEmployeeContract.getEmployee().getId());
            request.getSession().setAttribute("currentEmployeeContract", null);
        } else {
            currentEmployeeContract = employeecontractService.getEmployeecontractById(employeeContractId);
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

        LocalDate ecFromDate = null;
        LocalDate ecUntilDate = null;
        LocalDate soFromDate = null;
        LocalDate soUntilDate = null;

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
            if (ecFromDate.isBefore(soFromDate)) {
                employeeOrderForm.setValidFrom(format(soFromDate));
            } else {
                employeeOrderForm.setValidFrom(format(ecFromDate));
            }
        } else if (ecFromDate != null) {
            employeeOrderForm.setValidFrom(format(ecFromDate));
        } else if (soFromDate != null) {
            employeeOrderForm.setValidFrom(format(soFromDate));
        }

        // set until date
        if (ecUntilDate != null && soUntilDate != null) {
            if (ecUntilDate.isAfter(soUntilDate)) {
                employeeOrderForm.setValidUntil(format(soUntilDate));
            } else {
                employeeOrderForm.setValidUntil(format(ecUntilDate));
            }
        } else if (ecUntilDate != null) {
            employeeOrderForm.setValidUntil(format(ecUntilDate));
        } else if (soUntilDate != null) {
            employeeOrderForm.setValidUntil(format(soUntilDate));
        } else {
            employeeOrderForm.setValidUntil("");
        }
    }

}
