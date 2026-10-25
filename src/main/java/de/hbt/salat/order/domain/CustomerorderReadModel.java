package de.hbt.salat.order.domain;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import de.hbt.salat.customer.domain.CustomerReadModel;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.tag.domain.Tag;

/**
 * A customer order as values, with what the order list shows everybody who may open it (#1534): neither the contacts
 * at the customer nor order type, ticket rule or surcharges, which only the management sees in the form. For a module
 * that describes an order without handling the entity (ADR-0021).
 *
 * @param description           the description as stored, not the short one
 * @param validUntil            {@code null} for open-ended
 * @param debitHoursUnit        {@code null} without a unit
 * @param executionResponsibles the responsible employees of HBT, for carrying the order out
 * @param contractResponsible   the employee of HBT responsible for the contract, {@code null} without one
 * @param tags                  the names of the tags, by name regardless of case
 * @param intermediary          the customer who placed the order on behalf of the customer (#1536), {@code null} for
 *                              none
 */
public record CustomerorderReadModel(long id, String sign, String description, LocalDate validFrom,
    LocalDate validUntil, Duration debitHours, DebitHoursUnit debitHoursUnit, boolean hidden,
    List<Person> executionResponsibles, Person contractResponsible, List<String> tags, CustomerReadModel customer,
    CustomerReadModel intermediary) {

  public static CustomerorderReadModel of(Customerorder order) {
    return new CustomerorderReadModel(order.getId(), order.getSign(), order.getDescription(), order.getFromDate(),
        order.getUntilDate(), order.getDebithours(), DebitHoursUnit.of(order.getDebithoursunit()), order.getHide(),
        order.getResponsibleHbt().stream().map(Person::of).toList(),
        order.getRespEmpHbtContract() == null ? null : Person.of(order.getRespEmpHbtContract()),
        order.getTags().stream().map(Tag::getName).toList(),
        CustomerReadModel.of(order.getCustomer()),
        order.getIntermediary() == null ? null : CustomerReadModel.of(order.getIntermediary()));
  }

  /** A person by id, sign and name. */
  public record Person(long id, String sign, String name) {

    static Person of(Employee employee) {
      return new Person(employee.getId(), employee.getSign(), employee.getName());
    }
  }
}
