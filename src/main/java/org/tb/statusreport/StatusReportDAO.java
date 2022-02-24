package org.tb.statusreport;

import static java.lang.Boolean.TRUE;
import static java.util.Comparator.comparing;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tb.common.util.DateUtils;
import org.tb.employee.Employee;
import org.tb.order.Customerorder;

@Component
@RequiredArgsConstructor
public class StatusReportDAO {

    private final StatusreportRepository statusreportRepository;

    /**
     * Gets the {@link Statusreport} for the given id.
     */
    public Statusreport getStatusReportById(long id) {
        return statusreportRepository.findById(id).orElse(null);
    }

    /**
     * Get a list of all {@link Statusreport}s.
     */
    public List<Statusreport> getStatusReports() {
        return StreamSupport.stream(statusreportRepository.findAll().spliterator(), false)
            .sorted(comparing((Statusreport sr) -> sr.getCustomerorder().getSign())
                .thenComparing(Statusreport::getSort)
                .thenComparing(Statusreport::getFromdate)
                .thenComparing(Statusreport::getUntildate)
                .thenComparing(sr -> sr.getSender().getSign()))
            .collect(Collectors.toList());
    }

    /**
     * Get a list of all visible {@link Statusreport}s.
     */
    public List<Statusreport> getVisibleStatusReports() {
        final LocalDate now = DateUtils.today();
        return StreamSupport.stream(statusreportRepository.findAll().spliterator(), false)
            .filter(sr -> !TRUE.equals(sr.getCustomerorder().getHide()))
            .filter(sr -> !sr.getCustomerorder().isValidAt(now))
            .sorted(comparing((Statusreport sr) -> sr.getCustomerorder().getSign())
                .thenComparing(Statusreport::getSort)
                .thenComparing(Statusreport::getFromdate)
                .thenComparing(Statusreport::getUntildate)
                .thenComparing(sr -> sr.getSender().getSign()))
            .collect(Collectors.toList());
    }

    /**
     * Get a list of all {@link Statusreport}s associated with the given {@link Customerorder}.
     */
    public List<Statusreport> getStatusReportsByCustomerOrderId(long coId) {
        return statusreportRepository.findAllByCustomerorderId(coId).stream()
            .sorted(comparing((Statusreport sr) -> sr.getCustomerorder().getSign())
                .thenComparing(Statusreport::getSort)
                .thenComparing(Statusreport::getFromdate)
                .thenComparing(Statusreport::getUntildate)
                .thenComparing(sr -> sr.getSender().getSign()))
            .collect(Collectors.toList());
    }

    /**
     * Get a list of all released final {@link Statusreport}s associated with the given {@link Customerorder}.
     */
    public List<Statusreport> getReleasedFinalStatusReportsByCustomerOrderId(long coId) {
        return statusreportRepository.getReleasedFinalStatusReportsByCustomerOrderId(coId);
    }

    /**
     * Get a list of all unreleased final {@link Statusreport}s with an untildate > the given date,
     * that are associated with the given customerOrderId, senderId.
     */
    public List<Statusreport> getUnreleasedFinalStatusReports(long customerOrderId, long senderId, LocalDate date) {
        return statusreportRepository.getUnreleasedFinalStatusReports(customerOrderId, senderId, date);
    }

    /**
     * Get a list of all unreleased periodical {@link Statusreport}s with an untildate > the given date,
     * that are associated with the given customerOrderId, senderId.
     */
    public List<Statusreport> getUnreleasedPeriodicalStatusReports(long customerOrderId, long senderId, LocalDate date) {
        return statusreportRepository.getUnreleasedPeriodicalStatusReports(customerOrderId, senderId, date);
    }

    /**
     * Get a list of all released but not accepted {@link Statusreport}s associated with the given recipient.
     */
    public List<Statusreport> getReleasedStatusReportsByRecipientId(long employeeId) {
        return statusreportRepository.getReleasedStatusReportsByRecipientId(employeeId);
    }

    public LocalDate getMaxUntilDateForCustomerOrderId(long coId) {
        return statusreportRepository.getMaxUntilDateForCustomerOrderId(coId).orElse(null);
    }


    /**
     * Calls {@link StatusReportDAO#save(Statusreport, Employee)} with {@link Employee} = null.
     *
     * @param sr The {@link Statusreport} to save
     */
    public void save(Statusreport sr) {
        save(sr, null);
    }

    /**
     * Saves the given {@link Statusreport} and sets creation-/update-user and creation-/update-date.
     *
     * @param sr            The {@link Statusreport} to save
     * @param loginEmployee The login employee
     */
    public void save(Statusreport sr, Employee loginEmployee) {
        statusreportRepository.save(sr);
    }

    /**
     * Deletes the given {@link Statusreport}.
     *
     * @param srId The id of the {@link Statusreport} to delete
     * @return boolean
     */
    public boolean deleteStatusReportById(long srId) {
        statusreportRepository.deleteById(srId);
        return true;
    }

}
