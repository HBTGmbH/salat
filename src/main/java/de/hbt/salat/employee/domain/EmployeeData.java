package de.hbt.salat.employee.domain;

/**
 * A person as the employee form saves it (#1460): the values of the person and of their login. The
 * login belongs to the auth module; {@code EmployeeService} hands its values over there.
 *
 * @param id     the person, {@code null} for a new one
 * @param gender {@link de.hbt.salat.common.GlobalConstants#GENDER_MALE} or
 *               {@link de.hbt.salat.common.GlobalConstants#GENDER_FEMALE}
 */
public record EmployeeData(Long id, String firstname, String lastname, String sign, char gender, boolean hide,
                           String loginname, String status) {
}
