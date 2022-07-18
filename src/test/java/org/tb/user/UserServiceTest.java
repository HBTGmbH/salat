package org.tb.user;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.tb.employee.domain.Employee;
import org.tb.employee.persistence.EmployeeDAO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith({SpringExtension.class})
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class UserServiceTest {

    @Mock
    private EmployeeDAO employeeDAO;

    @Test
    public void should_accept_quality_password_1() {
        Employee testEmployee = new Employee();
        when(employeeDAO.getEmployeeById(1)).thenReturn(testEmployee);
        var valid = new UserService(employeeDAO).changePassword(1, "abc123ABC");
        assertThat(valid).isTrue();
    }

    @Test
    public void should_accept_quality_password_2() {
        Employee testEmployee = new Employee();
        when(employeeDAO.getEmployeeById(1)).thenReturn(testEmployee);
        var valid = new UserService(employeeDAO).changePassword(1, "abcABC%");
        assertThat(valid).isTrue();
    }

    @Test
    public void should_accept_quality_password_3() {
        Employee testEmployee = new Employee();
        when(employeeDAO.getEmployeeById(1)).thenReturn(testEmployee);
        var valid = new UserService(employeeDAO).changePassword(1, "123ABC$");
        assertThat(valid).isTrue();
    }

    @Test
    public void should_reject_too_short_password() {
        Employee testEmployee = new Employee();
        when(employeeDAO.getEmployeeById(1)).thenReturn(testEmployee);
        var valid = new UserService(employeeDAO).changePassword(1, "12BC$");
        assertThat(valid).isFalse();
    }

    @Test
    public void should_reject_insufficient_password_1() {
        Employee testEmployee = new Employee();
        when(employeeDAO.getEmployeeById(1)).thenReturn(testEmployee);
        var valid = new UserService(employeeDAO).changePassword(1, "1234ABC");
        assertThat(valid).isFalse();
    }

    @Test
    public void should_reject_insufficient_password_2() {
        Employee testEmployee = new Employee();
        when(employeeDAO.getEmployeeById(1)).thenReturn(testEmployee);
        var valid = new UserService(employeeDAO).changePassword(1, "1234abc");
        assertThat(valid).isFalse();
    }

    @Test
    public void should_reject_insufficient_password_3() {
        Employee testEmployee = new Employee();
        when(employeeDAO.getEmployeeById(1)).thenReturn(testEmployee);
        var valid = new UserService(employeeDAO).changePassword(1, "12$%&/()");
        assertThat(valid).isFalse();
    }

}
