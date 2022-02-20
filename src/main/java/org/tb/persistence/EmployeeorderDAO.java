package org.tb.persistence;

import static org.tb.GlobalConstants.CUSTOMERORDER_SIGN_EXTRA_VACATION;
import static org.tb.GlobalConstants.CUSTOMERORDER_SIGN_REMAINING_VACATION;
import static org.tb.GlobalConstants.CUSTOMERORDER_SIGN_VACATION;
import static org.tb.GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION;

import java.util.ArrayList;
import java.util.stream.Collectors;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Timereport;
import org.tb.bdom.comparators.EmployeeOrderComparator;

import java.util.Date;
import java.util.List;

@Component
public class EmployeeorderDAO extends AbstractDAO {

    private static final Logger LOG = LoggerFactory.getLogger(EmployeeorderDAO.class);
    private final TimereportDAO timereportDAO;
    private final EmployeeorderRepository employeeorderRepository;

    @Autowired
    public EmployeeorderDAO(SessionFactory sessionFactory, TimereportDAO timereportDAO,
        EmployeeorderRepository employeeorderRepository) {
        super(sessionFactory);
        this.timereportDAO = timereportDAO;
        this.employeeorderRepository = employeeorderRepository;
    }

    /**
     * Gets the employeeorder for the given id.
     */
    public Employeeorder getEmployeeorderById(long id) {
        return (Employeeorder) getSession().createQuery("select eo from Employeeorder eo where eo.id = ?").setLong(0, id).uniqueResult();
    }

    public List<Employeeorder> getEmployeeordersForEmployeeordercontentWarning(Employeecontract ec) {
        return employeeorderRepository.findAllByEmployeeIdAndEmployeeOrderContentUncommitted(ec.getEmployee().getId());
    }

    @SuppressWarnings("unchecked")
    public List<Employeeorder> getEmployeeOrdersByEmployeeContractIdAndCustomerOrderSignAndDate(long employeecontractId, String customerOrderSign, Date date) {
        return getSession().createQuery("select eo from Employeeorder eo " +
                                                "where eo.employeecontract.id = ? " +
                                                "and eo.suborder.customerorder.sign = ? " +
                                                "and eo.fromDate <= ? " +
                                                "and (eo.untilDate is null " +
                                                "or eo.untilDate >= ? )")
                           .setLong(0, employeecontractId)
                           .setString(1, customerOrderSign)
                           .setDate(2, date)
                           .setDate(3, date)
                           .list();
    }

    public List<Employeeorder> getVacationEmployeeOrdersByEmployeeContractIdAndDate(long employeecontractId, final Date date) {
        LOG.debug("starting read vacation list");
        var customerOrderSigns = new ArrayList<String>();
        customerOrderSigns.add(CUSTOMERORDER_SIGN_REMAINING_VACATION);
        customerOrderSigns.add(CUSTOMERORDER_SIGN_EXTRA_VACATION);
        customerOrderSigns.add(CUSTOMERORDER_SIGN_VACATION);
        var employeeorders= employeeorderRepository.findAllByEmployeecontractIdAndSuborderCustomerorderSignIn(
            employeecontractId,
            customerOrderSigns
        );

        employeeorders = employeeorders.stream()
            .filter(eo -> !eo.getFromDate().after(date))
            .filter(eo -> eo.getUntilDate() == null || !eo.getUntilDate().before(date))
            .filter(eo -> !eo.getSuborder().getSign().equals(SUBORDER_SIGN_OVERTIME_COMPENSATION))
            .collect(Collectors.toList());

        LOG.debug("read vacations.");
        return employeeorders;
    }

    /**
     * Returns the {@link Employeeorder} associated to the given employeecontractID and suborderId, that is valid for the given date.
     */
    public Employeeorder getEmployeeorderByEmployeeContractIdAndSuborderIdAndDate(long employeecontractId, long suborderId, Date date) {
        return (Employeeorder) getSession()
                .createQuery("from Employeeorder eo where eo.employeecontract.id = ? and eo.suborder.id = ? and eo.fromDate <= ? and (eo.untilDate is null or eo.untilDate >= ?)")
                .setLong(0, employeecontractId).setLong(1, suborderId).setDate(2, date).setDate(3, date).uniqueResult();
    }

    /**
     * Gets the list of employeeorders for the given employee contract id.
     */
    @SuppressWarnings("unchecked")
    public List<Employeeorder> getEmployeeOrdersByEmployeeContractId(long employeeContractId) {
        return getSession().createQuery("select eo from Employeeorder eo where eo.employeecontract.id = ? order by eo.suborder.customerorder.sign asc, eo.suborder.sign asc, eo.fromDate asc").setLong(0, employeeContractId)
                           .list();
    }

    /**
     * Gets the list of employeeorders for the given suborder id.
     */
    @SuppressWarnings("unchecked")
    public List<Employeeorder> getEmployeeOrdersBySuborderId(long suborderId) {
        return getSession().createQuery("from Employeeorder eo where eo.suborder.id = ? order by eo.suborder.customerorder.sign asc, eo.suborder.sign asc, eo.fromDate asc").setLong(0, suborderId).list();
    }

    /**
     * Gets the list of employeeorders for the given employee contract and suborder id.
     */
    @SuppressWarnings("unchecked")
    public List<Employeeorder> getEmployeeOrdersByEmployeeContractIdAndSuborderId(long employeeContractId, long suborderId) {
        return employeeorderRepository.findAllByEmployeecontractIdAndSuborderId(
            employeeContractId,
            suborderId
        );
    }

    /**
     * Gets the list of employeeorders for the given employee contract and suborder id and date.
     */
    @SuppressWarnings("unchecked")
    public List<Employeeorder> getEmployeeOrderByEmployeeContractIdAndSuborderIdAndDate2(long employeeContractId, long suborderId, Date date) {
        return employeeorderRepository.findAllByEmployeecontractIdAndSuborderIdAndUntilDateGreaterThanEqual(
            employeeContractId,
            suborderId,
            date
        );
    }

    /**
     * Gets the list of employeeorders for the given employee contract and suborder id and date.
     */
    @SuppressWarnings("unchecked")
    public List<Employeeorder> getEmployeeOrderByEmployeeContractIdAndSuborderIdAndDate3(long employeeContractId, long suborderId, Date date) {
        return employeeorderRepository.findAllByEmployeecontractIdAndSuborderIdAndUntilDateGreaterThanEqual(
            employeeContractId,
            suborderId,
            date
        );
    }

    /**
     * Get a list of all Employeeorders ordered by their sign.
     *
     * @return List<Employeeorder>
     */
    @SuppressWarnings("unchecked")
    public List<Employeeorder> getEmployeeorders() {
        return getSession().createQuery("select eo from Employeeorder eo order by eo.employeecontract.employee.sign asc, eo.suborder.customerorder.sign asc, eo.suborder.sign asc, eo.fromDate asc").list();
    }

    /**
     * @return Returns a list of all {@link Employeeorder}s associated to the given orderId.
     */
    @SuppressWarnings("unchecked")
    public List<Employeeorder> getEmployeeordersByOrderId(long orderId) {
        return getSession()
                .createQuery("select eo from Employeeorder eo where eo.suborder.customerorder.id = ? order by eo.employeecontract.employee.sign asc, eo.suborder.customerorder.sign asc, eo.suborder.sign asc, eo.fromDate asc")
                .setLong(0, orderId).list();
    }

    /**
     * @param eocId The id of the associated {@link org.tb.bdom.Employeeordercontent}
     * @return Returns the {@link Employeeorder} associated to the given eocId.
     */
    public Employeeorder getEmployeeOrderByContentId(long eocId) {
        return (Employeeorder) getSession().createQuery("from Employeeorder eo where eo.employeeOrderContent.id = ? ").setLong(0, eocId).uniqueResult();
    }

    /**
     * @return Returns a list of all {@link Employeeorder}s associated to the given orderId and employeeContractId.
     */
    @SuppressWarnings("unchecked")
    public List<Employeeorder> getEmployeeordersByOrderIdAndEmployeeContractId(long orderId, long employeeContractId) {
        return getSession()
                .createQuery("select eo from Employeeorder eo where eo.suborder.customerorder.id = ? and eo.employeecontract.id = ? order by eo.employeecontract.employee.sign asc, eo.suborder.customerorder.sign asc, eo.suborder.sign asc, eo.fromDate asc")
                .setLong(0, orderId).setLong(1, employeeContractId).list();
    }

    /**
     * Get a list of all Employeeorders ordered by employee, customer order, suborder.
     */
    @SuppressWarnings("unchecked")
    public List<Employeeorder> getSortedEmployeeorders() {
        List<Employeeorder> employeeorders = getSession().createQuery("from Employeeorder").list();
        employeeorders.sort(EmployeeOrderComparator.INSTANCE);
        return employeeorders;
    }

    /**
     * Get a list of all Employeeorders fitting to the given filters ordered by employee, customer order, and suborder.
     */
    @SuppressWarnings("unchecked")
    public List<Employeeorder> getEmployeeordersByFilters(Boolean showInvalid, String filter, Long employeeContractId, Long customerOrderId, Long customerSuborderId) {
        List<Employeeorder> employeeorders = null;
        boolean isFilter = filter != null && !filter.trim().isEmpty();
        if (isFilter) {
            filter = "%" + filter.toUpperCase() + "%";
        }
        if (showInvalid == null || !showInvalid) {
            Date now = new Date();
            if (!isFilter) {
                if (employeeContractId == null || employeeContractId == 0 || employeeContractId == -1) {
                    if (customerOrderId == null || customerOrderId == 0 || customerOrderId == -1) {
                        // case 01: only valid, no filter, no employeeContractId, no customerOrderId
                        employeeorders = getSession().createQuery("select eo from Employeeorder eo where " +
                                                                          "eo.fromDate <= ? " +
                                                                          "and (eo.untilDate = null " +
                                                                          "or eo.untilDate >= ?) " +
                                                                          "order by eo.employeecontract.employee.sign asc, eo.suborder.customerorder.sign asc, eo.suborder.sign asc, eo.fromDate asc")
                                                     .setDate(0, now)
                                                     .setDate(1, now)
                                                     .list();
                    } else {
                        // case 02: only valid, no filter, no employeeContractId, customerOrderId
                        if (customerSuborderId == 0 || customerSuborderId == -1) {
                            // suborder wasn't selected
                            employeeorders = getSession().createQuery("select eo from Employeeorder eo where " +
                                                                              "eo.suborder.customerorder.id = ? " +
                                                                              "and eo.fromDate <= ? " +
                                                                              "and (eo.untilDate = null " +
                                                                              "or eo.untilDate >= ?) " +
                                                                              "order by eo.employeecontract.employee.sign asc, eo.suborder.customerorder.sign asc, eo.suborder.sign asc, eo.fromDate asc")
                                                         .setLong(0, customerOrderId)
                                                         .setDate(1, now)
                                                         .setDate(2, now)
                                                         .list();
                        } else { // suborder was selected
                            employeeorders = getSession().createQuery("select eo from Employeeorder eo where " +
                                                                              "eo.suborder.customerorder.id = ? " +
                                                                              "and eo.suborder.id = ? " +
                                                                              "and eo.fromDate <= ? " +
                                                                              "and (eo.untilDate = null " +
                                                                              "or eo.untilDate >= ?) " +
                                                                              "order by eo.employeecontract.employee.sign asc, eo.suborder.customerorder.sign asc, eo.suborder.sign asc, eo.fromDate asc")
                                                         .setLong(0, customerOrderId)
                                                         .setLong(1, customerSuborderId)
                                                         .setDate(2, now)
                                                         .setDate(3, now)
                                                         .list();
                        }
                    }
                } else {
                    if (customerOrderId == null || customerOrderId == 0 || customerOrderId == -1) {
                        // case 03: only valid, no filter, employeeContractId, no customerOrderId  
                        employeeorders = getSession().createQuery("select eo from Employeeorder eo where " +
                                                                          "eo.employeecontract.id = ? " +
                                                                          "and eo.fromDate <= ? " +
                                                                          "and (eo.untilDate = null " +
                                                                          "or eo.untilDate >= ?) " +
                                                                          "order by eo.employeecontract.employee.sign asc, eo.suborder.customerorder.sign asc, eo.suborder.sign asc, eo.fromDate asc")
                                                     .setLong(0, employeeContractId)
                                                     .setDate(1, now)
                                                     .setDate(2, now)
                                                     .list();
                    } else {
                        // case 04: only valid, no filter, employeeContractId, customerOrderId
                        if (customerSuborderId == -1 || customerSuborderId == 0) {
                            // when no suborder was selected
                            employeeorders = getSession().createQuery("select eo from Employeeorder eo where " +
                                                                              "eo.suborder.customerorder.id = ? " +
                                                                              "and eo.employeecontract.id = ? " +
                                                                              "and eo.fromDate <= ? " +
                                                                              "and (eo.untilDate = null " +
                                                                              "or eo.untilDate >= ?) " +
                                                                              "order by eo.employeecontract.employee.sign asc, eo.suborder.customerorder.sign asc, eo.suborder.sign asc, eo.fromDate asc")
                                                         .setLong(0, customerOrderId)
                                                         .setLong(1, employeeContractId)
                                                         .setDate(2, now)
                                                         .setDate(3, now)
                                                         .list();
                        } else {
                            // when suborder was selected
                            employeeorders = getSession().createQuery("select eo from Employeeorder eo where " +
                                                                              "eo.suborder.customerorder.id = ? " +
                                                                              "and eo.employeecontract.id = ? " +
                                                                              "and eo.suborder.id = ? " +
                                                                              "and eo.fromDate <= ? " +
                                                                              "and (eo.untilDate = null " +
                                                                              "or eo.untilDate >= ?) " +
                                                                              "order by eo.employeecontract.employee.sign asc, eo.suborder.customerorder.sign asc, eo.suborder.sign asc, eo.fromDate asc")
                                                         .setLong(0, customerOrderId)
                                                         .setLong(1, employeeContractId)
                                                         .setLong(2, customerSuborderId)
                                                         .setDate(3, now)
                                                         .setDate(4, now)
                                                         .list();
                        }
                    }
                }
            } else {
                employeeorders = queryValidFilter(filter, employeeContractId, customerOrderId, now);
            }
        } else {
            if (!isFilter) {
                if (employeeContractId == null || employeeContractId == 0 || employeeContractId == -1) {
                    if (customerOrderId == null || customerOrderId == 0 || customerOrderId == -1) {
                        // case 09: valid + invalid, no filter, no employeeContractId, no customerOrderId  
                        employeeorders = getSession().createQuery("from Employeeorder eo " +
                                                                          "order by eo.employeecontract.employee.sign asc, eo.suborder.customerorder.sign asc, eo.suborder.sign asc, eo.fromDate asc")
                                                     .list();
                    } else {
                        // case 10: valid + invalid, no filter, no employeeContractId, customerOrderId  
                        if (customerSuborderId == 0 || customerSuborderId == -1) {
                            // suborder was not selected
                            employeeorders = getSession().createQuery("from Employeeorder eo where " +
                                                                              "suborder.customerorder.id = ? " +
                                                                              "order by eo.employeecontract.employee.sign asc, eo.suborder.customerorder.sign asc, eo.suborder.sign asc, eo.fromDate asc")
                                                         .setLong(0, customerOrderId)
                                                         .list();
                        } else {// suborder was selected
                            employeeorders = getSession().createQuery("from Employeeorder eo where " +
                                                                              "suborder.customerorder.id = ? " +
                                                                              "and suborder.id = ? " +
                                                                              "order by eo.employeecontract.employee.sign asc, eo.suborder.customerorder.sign asc, eo.suborder.sign asc, eo.fromDate asc")
                                                         .setLong(0, customerOrderId)
                                                         .setLong(1, customerSuborderId)
                                                         .list();
                        }
                    }
                } else {
                    if (customerOrderId == null || customerOrderId == 0 || customerOrderId == -1) {
                        // case 11: valid + invalid, no filter, employeeContractId, no customerOrderId  
                        employeeorders = getSession().createQuery("from Employeeorder eo where " +
                                                                          "employeecontract.id = ? " +
                                                                          "order by eo.employeecontract.employee.sign asc, eo.suborder.customerorder.sign asc, eo.suborder.sign asc, eo.fromDate asc")
                                                     .setLong(0, employeeContractId)
                                                     .list();
                    } else {
                        // case 12: valid + invalid, no filter, employeeContractId, customerOrderId
                        if (customerSuborderId == 0 || customerSuborderId == -1) {
                            // no suborder was selected
                            employeeorders = getSession().createQuery("from Employeeorder eo where " +
                                                                              "suborder.customerorder.id = ? " +
                                                                              "and employeecontract.id = ? " +
                                                                              "order by eo.employeecontract.employee.sign asc, eo.suborder.customerorder.sign asc, eo.suborder.sign asc, eo.fromDate asc")
                                                         .setLong(0, customerOrderId)
                                                         .setLong(1, employeeContractId)
                                                         .list();
                        }
                        // the suborder was selected also
                        else {
                            employeeorders = getSession().createQuery("from Employeeorder eo where " +
                                                                              "suborder.customerorder.id = ? " +
                                                                              "and employeecontract.id = ? " +
                                                                              "and suborder.id = ? " +
                                                                              "order by eo.employeecontract.employee.sign asc, eo.suborder.customerorder.sign asc, eo.suborder.sign asc, eo.fromDate asc")
                                                         .setLong(0, customerOrderId)
                                                         .setLong(1, employeeContractId)
                                                         .setLong(2, customerSuborderId)
                                                         .list();
                        }
                    }
                }
            } else {
                employeeorders = queryValidInvalidFilter(filter, employeeContractId, customerOrderId);
            }
        }
        return employeeorders;
    }

    @SuppressWarnings("unchecked")
    private List<Employeeorder> queryValidFilter(String filter, Long employeeContractId, Long customerOrderId,
                                                 Date now) {
        if (employeeContractId == null || employeeContractId == 0 || employeeContractId == -1) {
            if (customerOrderId == null || customerOrderId == 0 || customerOrderId == -1) {
                // case 05: only valid, filter, no employeeContractId, no customerOrderId
                return getSession().createQuery("from Employeeorder eo where " +
                                                        "(upper(id) like ? " +
                                                        "or upper(employeecontract.employee.sign) like ? " +
                                                        "or upper(employeecontract.employee.firstname) like ? " +
                                                        "or upper(employeecontract.employee.lastname) like ? " +
                                                        "or upper(suborder.customerorder.sign) like ? " +
                                                        "or upper(suborder.customerorder.description) like ? " +
                                                        "or upper(suborder.customerorder.shortdescription) like ? " +
                                                        "or upper(suborder.sign) like ? " +
                                                        "or upper(suborder.description) like ? " +
                                                        "or upper(suborder.shortdescription) like ?) " +
                                                        "and eo.fromDate <= ? " +
                                                        "and (eo.untilDate = null " +
                                                        "or eo.untilDate >= ?) " +
                                                        "order by eo.employeecontract.employee.sign asc, eo.suborder.customerorder.sign asc, eo.suborder.sign asc, eo.fromDate asc")
                                   .setString(0, filter)
                                   .setString(1, filter)
                                   .setString(2, filter)
                                   .setString(3, filter)
                                   .setString(4, filter)
                                   .setString(5, filter)
                                   .setString(6, filter)
                                   .setString(7, filter)
                                   .setString(8, filter)
                                   .setString(9, filter)
                                   .setDate(10, now)
                                   .setDate(11, now)
                                   .list();
            } else {
                // case 06: only valid, filter, no employeeContractId, customerOrderId
                return getSession().createQuery("from Employeeorder eo where " +
                                                        "suborder.customerorder.id = ? " +
                                                        "and (upper(id) like ? " +
                                                        "or upper(employeecontract.employee.sign) like ? " +
                                                        "or upper(employeecontract.employee.firstname) like ? " +
                                                        "or upper(employeecontract.employee.lastname) like ? " +
                                                        "or upper(suborder.customerorder.sign) like ? " +
                                                        "or upper(suborder.customerorder.description) like ? " +
                                                        "or upper(suborder.customerorder.shortdescription) like ? " +
                                                        "or upper(suborder.sign) like ? " +
                                                        "or upper(suborder.description) like ? " +
                                                        "or upper(suborder.shortdescription) like ?) " +
                                                        "and eo.fromDate <= ? " +
                                                        "and (eo.untilDate = null " +
                                                        "or eo.untilDate >= ?) " +
                                                        "order by eo.employeecontract.employee.sign asc, eo.suborder.customerorder.sign asc, eo.suborder.sign asc, eo.fromDate asc")
                                   .setLong(0, customerOrderId)
                                   .setString(1, filter)
                                   .setString(2, filter)
                                   .setString(3, filter)
                                   .setString(4, filter)
                                   .setString(5, filter)
                                   .setString(6, filter)
                                   .setString(7, filter)
                                   .setString(8, filter)
                                   .setString(9, filter)
                                   .setString(10, filter)
                                   .setDate(11, now)
                                   .setDate(12, now)
                                   .list();
            }
        } else {
            if (customerOrderId == null || customerOrderId == 0 || customerOrderId == -1) {
                // case 07: only valid, filter, employeeContractId, no customerOrderId
                return getSession().createQuery("from Employeeorder eo where " +
                                                        "employeecontract.id = ? " +
                                                        "and (upper(id) like ? " +
                                                        "or upper(employeecontract.employee.sign) like ? " +
                                                        "or upper(employeecontract.employee.firstname) like ? " +
                                                        "or upper(employeecontract.employee.lastname) like ? " +
                                                        "or upper(suborder.customerorder.sign) like ? " +
                                                        "or upper(suborder.customerorder.description) like ? " +
                                                        "or upper(suborder.customerorder.shortdescription) like ? " +
                                                        "or upper(suborder.sign) like ? " +
                                                        "or upper(suborder.description) like ? " +
                                                        "or upper(suborder.shortdescription) like ?) " +
                                                        "and eo.fromDate <= ? " +
                                                        "and (eo.untilDate = null " +
                                                        "or eo.untilDate >= ?) " +
                                                        "order by eo.employeecontract.employee.sign asc, eo.suborder.customerorder.sign asc, eo.suborder.sign asc, eo.fromDate asc")
                                   .setLong(0, employeeContractId)
                                   .setString(1, filter)
                                   .setString(2, filter)
                                   .setString(3, filter)
                                   .setString(4, filter)
                                   .setString(5, filter)
                                   .setString(6, filter)
                                   .setString(7, filter)
                                   .setString(8, filter)
                                   .setString(9, filter)
                                   .setString(10, filter)
                                   .setDate(11, now)
                                   .setDate(12, now)
                                   .list();
            } else {
                // case 08: only valid, filter, employeeContractId, customerOrderId

                return getSession().createQuery("from Employeeorder eo where " +
                                                        "suborder.customerorder.id = ? " +
                                                        "and employeecontract.id = ? " +
                                                        "and (upper(id) like ? " +
                                                        "or upper(employeecontract.employee.sign) like ? " +
                                                        "or upper(employeecontract.employee.firstname) like ? " +
                                                        "or upper(employeecontract.employee.lastname) like ? " +
                                                        "or upper(suborder.customerorder.sign) like ? " +
                                                        "or upper(suborder.customerorder.description) like ? " +
                                                        "or upper(suborder.customerorder.shortdescription) like ? " +
                                                        "or upper(suborder.sign) like ? " +
                                                        "or upper(suborder.description) like ? " +
                                                        "or upper(suborder.shortdescription) like ?) " +
                                                        "and eo.fromDate <= ? " +
                                                        "and (eo.untilDate = null " +
                                                        "or eo.untilDate >= ?) " +
                                                        "order by eo.employeecontract.employee.sign asc, eo.suborder.customerorder.sign asc, eo.suborder.sign asc, eo.fromDate asc")
                                   .setLong(0, customerOrderId)
                                   .setLong(1, employeeContractId)
                                   .setString(2, filter)
                                   .setString(3, filter)
                                   .setString(4, filter)
                                   .setString(5, filter)
                                   .setString(6, filter)
                                   .setString(7, filter)
                                   .setString(8, filter)
                                   .setString(9, filter)
                                   .setString(10, filter)
                                   .setString(11, filter)
                                   .setDate(12, now)
                                   .setDate(13, now)
                                   .list();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<Employeeorder> queryValidInvalidFilter(String filter, Long employeeContractId, Long customerOrderId) {
        if (employeeContractId == null || employeeContractId == 0 || employeeContractId == -1) {
            if (customerOrderId == null || customerOrderId == 0 || customerOrderId == -1) {
                // case 13: valid + invalid, filter, no employeeContractId, no customerOrderId
                return getSession().createQuery("from Employeeorder eo where " +
                                                        "upper(id) like ? " +
                                                        "or upper(employeecontract.employee.sign) like ? " +
                                                        "or upper(employeecontract.employee.firstname) like ? " +
                                                        "or upper(employeecontract.employee.lastname) like ? " +
                                                        "or upper(suborder.customerorder.sign) like ? " +
                                                        "or upper(suborder.customerorder.description) like ? " +
                                                        "or upper(suborder.customerorder.shortdescription) like ? " +
                                                        "or upper(suborder.sign) like ? " +
                                                        "or upper(suborder.description) like ? " +
                                                        "or upper(suborder.shortdescription) like ? " +
                                                        "order by eo.employeecontract.employee.sign asc, eo.suborder.customerorder.sign asc, eo.suborder.sign asc, eo.fromDate asc")
                                   .setString(0, filter)
                                   .setString(1, filter)
                                   .setString(2, filter)
                                   .setString(3, filter)
                                   .setString(4, filter)
                                   .setString(5, filter)
                                   .setString(6, filter)
                                   .setString(7, filter)
                                   .setString(8, filter)
                                   .setString(9, filter)
                                   .list();
            } else {
                // case 14: valid + invalid, filter, no employeeContractId, customerOrderId
                return getSession().createQuery("from Employeeorder eo where " +
                                                        "suborder.customerorder.id = ? " +
                                                        "and (upper(id) like ? " +
                                                        "or upper(employeecontract.employee.sign) like ? " +
                                                        "or upper(employeecontract.employee.firstname) like ? " +
                                                        "or upper(employeecontract.employee.lastname) like ? " +
                                                        "or upper(suborder.customerorder.sign) like ? " +
                                                        "or upper(suborder.customerorder.description) like ? " +
                                                        "or upper(suborder.customerorder.shortdescription) like ? " +
                                                        "or upper(suborder.sign) like ? " +
                                                        "or upper(suborder.description) like ? " +
                                                        "or upper(suborder.shortdescription) like ?) " +
                                                        "order by eo.employeecontract.employee.sign asc, eo.suborder.customerorder.sign asc, eo.suborder.sign asc, eo.fromDate asc")
                                   .setLong(0, customerOrderId)
                                   .setString(1, filter)
                                   .setString(2, filter)
                                   .setString(3, filter)
                                   .setString(4, filter)
                                   .setString(5, filter)
                                   .setString(6, filter)
                                   .setString(7, filter)
                                   .setString(8, filter)
                                   .setString(9, filter)
                                   .setString(10, filter)
                                   .list();
            }
        } else {
            if (customerOrderId == null || customerOrderId == 0 || customerOrderId == -1) {
                // case 15: valid + invalid, filter, employeeContractId, no customerOrderId
                return getSession().createQuery("from Employeeorder eo where " +
                                                        "employeecontract.id = ? " +
                                                        "and (upper(id) like ? " +
                                                        "or upper(employeecontract.employee.sign) like ? " +
                                                        "or upper(employeecontract.employee.firstname) like ? " +
                                                        "or upper(employeecontract.employee.lastname) like ? " +
                                                        "or upper(suborder.customerorder.sign) like ? " +
                                                        "or upper(suborder.customerorder.description) like ? " +
                                                        "or upper(suborder.customerorder.shortdescription) like ? " +
                                                        "or upper(suborder.sign) like ? " +
                                                        "or upper(suborder.description) like ? " +
                                                        "or upper(suborder.shortdescription) like ?) " +
                                                        "order by eo.employeecontract.employee.sign asc, eo.suborder.customerorder.sign asc, eo.suborder.sign asc, eo.fromDate asc")
                                   .setLong(0, employeeContractId)
                                   .setString(1, filter)
                                   .setString(2, filter)
                                   .setString(3, filter)
                                   .setString(4, filter)
                                   .setString(5, filter)
                                   .setString(6, filter)
                                   .setString(7, filter)
                                   .setString(8, filter)
                                   .setString(9, filter)
                                   .setString(10, filter)
                                   .list();
            } else {
                // case 16: valid + invalid, filter, employeeContractId, customerOrderId
                return getSession().createQuery("from Employeeorder eo where " +
                                                        "suborder.customerorder.id = ? " +
                                                        "and employeecontract.id = ? " +
                                                        "and (upper(id) like ? " +
                                                        "or upper(employeecontract.employee.sign) like ? " +
                                                        "or upper(employeecontract.employee.firstname) like ? " +
                                                        "or upper(employeecontract.employee.lastname) like ? " +
                                                        "or upper(suborder.customerorder.sign) like ? " +
                                                        "or upper(suborder.customerorder.description) like ? " +
                                                        "or upper(suborder.customerorder.shortdescription) like ? " +
                                                        "or upper(suborder.sign) like ? " +
                                                        "or upper(suborder.description) like ? " +
                                                        "or upper(suborder.shortdescription) like ?) " +
                                                        "order by eo.employeecontract.employee.sign asc, eo.suborder.customerorder.sign asc, eo.suborder.sign asc, eo.fromDate asc")
                                   .setLong(0, customerOrderId)
                                   .setLong(1, employeeContractId)
                                   .setString(2, filter)
                                   .setString(3, filter)
                                   .setString(4, filter)
                                   .setString(5, filter)
                                   .setString(6, filter)
                                   .setString(7, filter)
                                   .setString(8, filter)
                                   .setString(9, filter)
                                   .setString(10, filter)
                                   .setString(11, filter)
                                   .list();
            }
        }
    }

    /**
     * Get a list of all Employeeorders fitting to the given filters ordered by employee, customer order, suborder.
     *
     * @return List<Employeeorder>
     */
    @SuppressWarnings("unchecked")
    public List<Employeeorder> getEmployeeordersByFilters(Boolean showInvalid, String filter, Long employeeContractId, Long customerOrderId) {
        List<Employeeorder> employeeorders = null;
        boolean isFilter = filter != null && !filter.trim().isEmpty();
        if (isFilter) {
            filter = "%" + filter.toUpperCase() + "%";
        }
        if (showInvalid == null || !showInvalid) {
            Date now = new Date();
            if (!isFilter) {
                if (employeeContractId == null || employeeContractId == 0 || employeeContractId == -1) {
                    if (customerOrderId == null || customerOrderId == 0 || customerOrderId == -1) {
                        // case 01: only valid, no filter, no employeeContractId, no customerOrderId
                        employeeorders = getSession().createQuery("from Employeeorder eo where " +
                                                                          "eo.fromDate <= ? " +
                                                                          "and (eo.untilDate = null " +
                                                                          "or eo.untilDate >= ?) " +
                                                                          "order by eo.employeecontract.employee.sign asc, eo.suborder.customerorder.sign asc, eo.suborder.sign asc, eo.fromDate asc")
                                                     .setDate(0, now)
                                                     .setDate(1, now)
                                                     .list();
                    } else {
                        // case 02: only valid, no filter, no employeeContractId, customerOrderId 
                        employeeorders = getSession().createQuery("from Employeeorder eo where " +
                                                                          "suborder.customerorder.id = ? " +
                                                                          "and eo.fromDate <= ? " +
                                                                          "and (eo.untilDate = null " +
                                                                          "or eo.untilDate >= ?) " +
                                                                          "order by eo.employeecontract.employee.sign asc, eo.suborder.customerorder.sign asc, eo.suborder.sign asc, eo.fromDate asc")
                                                     .setLong(0, customerOrderId)
                                                     .setDate(1, now)
                                                     .setDate(2, now)
                                                     .list();
                    }
                } else {
                    if (customerOrderId == null || customerOrderId == 0 || customerOrderId == -1) {
                        // case 03: only valid, no filter, employeeContractId, no customerOrderId  
                        employeeorders = getSession().createQuery("from Employeeorder eo where " +
                                                                          "employeecontract.id = ? " +
                                                                          "and eo.fromDate <= ? " +
                                                                          "and (eo.untilDate = null " +
                                                                          "or eo.untilDate >= ?) " +
                                                                          "order by eo.employeecontract.employee.sign asc, eo.suborder.customerorder.sign asc, eo.suborder.sign asc, eo.fromDate asc")
                                                     .setLong(0, employeeContractId)
                                                     .setDate(1, now)
                                                     .setDate(2, now)
                                                     .list();
                    } else {
                        // case 04: only valid, no filter, employeeContractId, customerOrderId  
                        employeeorders = getSession().createQuery("from Employeeorder eo where " +
                                                                          "suborder.customerorder.id = ? " +
                                                                          "and employeecontract.id = ? " +
                                                                          "and eo.fromDate <= ? " +
                                                                          "and (eo.untilDate = null " +
                                                                          "or eo.untilDate >= ?) " +
                                                                          "order by eo.employeecontract.employee.sign asc, eo.suborder.customerorder.sign asc, eo.suborder.sign asc, eo.fromDate asc")
                                                     .setLong(0, customerOrderId)
                                                     .setLong(1, employeeContractId)
                                                     .setDate(2, now)
                                                     .setDate(3, now)
                                                     .list();
                    }
                }
            } else {
                employeeorders = queryValidFilter(filter, employeeContractId, customerOrderId, now);
            }
        } else {
            if (!isFilter) {
                if (employeeContractId == null || employeeContractId == 0 || employeeContractId == -1) {
                    if (customerOrderId == null || customerOrderId == 0 || customerOrderId == -1) {
                        // case 09: valid + invalid, no filter, no employeeContractId, no customerOrderId  
                        employeeorders = getSession().createQuery("from Employeeorder eo " +
                                                                          "order by eo.employeecontract.employee.sign asc, eo.suborder.customerorder.sign asc, eo.suborder.sign asc, eo.fromDate asc")
                                                     .list();
                    } else {
                        // case 10: valid + invalid, no filter, no employeeContractId, customerOrderId  
                        employeeorders = getSession().createQuery("from Employeeorder eo where " +
                                                                          "suborder.customerorder.id = ? " +
                                                                          "order by eo.employeecontract.employee.sign asc, eo.suborder.customerorder.sign asc, eo.suborder.sign asc, eo.fromDate asc")
                                                     .setLong(0, customerOrderId)
                                                     .list();
                    }
                } else {
                    if (customerOrderId == null || customerOrderId == 0 || customerOrderId == -1) {
                        // case 11: valid + invalid, no filter, employeeContractId, no customerOrderId  
                        employeeorders = getSession().createQuery("from Employeeorder eo where " +
                                                                          "employeecontract.id = ? " +
                                                                          "order by eo.employeecontract.employee.sign asc, eo.suborder.customerorder.sign asc, eo.suborder.sign asc, eo.fromDate asc")
                                                     .setLong(0, employeeContractId)
                                                     .list();
                    } else {
                        // case 12: valid + invalid, no filter, employeeContractId, customerOrderId  
                        employeeorders = getSession().createQuery("from Employeeorder eo where " +
                                                                          "suborder.customerorder.id = ? " +
                                                                          "and employeecontract.id = ? " +
                                                                          "order by eo.employeecontract.employee.sign asc, eo.suborder.customerorder.sign asc, eo.suborder.sign asc, eo.fromDate asc")
                                                     .setLong(0, customerOrderId)
                                                     .setLong(1, employeeContractId)
                                                     .list();
                    }
                }
            } else {
                employeeorders = queryValidInvalidFilter(filter, employeeContractId, customerOrderId);
            }
        }
        return employeeorders;
    }

    /**
     * Calls {@link EmployeeorderDAO#save(Employeeorder, Employee)} with {@link Employee} = null.
     */
    public void save(Employeeorder eo) {
        save(eo, null);
    }

    /**
     * Saves the given Employeeorder and sets creation-/update-user and creation-/update-date.
     */
    public void save(Employeeorder eo, Employee loginEmployee) {
        if (loginEmployee == null) {
            throw new RuntimeException("the login-user must be passed to the db");
        }
        Session session = getSession();
        java.util.Date creationDate = eo.getCreated();
        if (creationDate == null) {
            eo.setCreated(new java.util.Date());
            eo.setCreatedby(loginEmployee.getSign());
        } else {
            eo.setLastupdate(new java.util.Date());
            eo.setLastupdatedby(loginEmployee.getSign());
            Integer updateCounter = eo.getUpdatecounter();
            updateCounter = updateCounter == null ? 1 : updateCounter + 1;
            eo.setUpdatecounter(updateCounter);
        }
        session.saveOrUpdate(eo);
        session.flush();
    }

    /**
     * Deletes the given employee order.
     */
    public boolean deleteEmployeeorderById(long eoId) {
        List<Employeeorder> allEmployeeorders = getEmployeeorders();
        Employeeorder eoToDelete = getEmployeeorderById(eoId);
        boolean eoDeleted = false;

        for (Object element : allEmployeeorders) {
            Employeeorder eo = (Employeeorder) element;
            if (eo.getId() == eoToDelete.getId()) {
                boolean deleteOk = false;

                // check if related status reports exist - if so, no deletion possible				
                // TODO as soon as table STATUSREPORT is available...

                List<Timereport> timereports = timereportDAO.getTimereportsByEmployeeOrderId(eo.getId());
                if (timereports == null || timereports.isEmpty()) {
                    deleteOk = true;
                }

                if (deleteOk) {
                    Session session = getSession();
                    session.delete(eoToDelete);
                    session.flush();
                    eoDeleted = true;
                }
                break;
            }
        }

        return eoDeleted;
    }
}
