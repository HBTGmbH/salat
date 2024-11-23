package org.tb.auth.service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.tb.auth.AuthorizationRuleRepository;
import org.tb.auth.AuthorizedUser;
import org.tb.auth.domain.AuthorizationRule;
import org.tb.auth.domain.AuthorizationRule.Category;
import org.tb.auth.domain.AccessLevel;
import org.tb.common.SalatProperties;
import org.tb.dailyreport.domain.Referenceday;
import org.tb.dailyreport.domain.Timereport;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Suborder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthorizedUser authorizedUser;

    @Mock
    private AuthorizationRuleRepository authorizationRuleRepository;

    @Mock
    private SalatProperties salatProperties;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        var authServiceProps = new SalatProperties.AuthService();
        authServiceProps.setCacheExpiry(Duration.ofMillis(1000));
        when(salatProperties.getAuthService()).thenReturn(authServiceProps);
        when(authorizedUser.getSign()).thenReturn("auth-sign");
        authService.init();
    }

    @Test
    void testObjectId() {
        // Arrange
        var rule = new AuthorizationRule();
        rule.setObjectId(new HashSet<>());
        rule.setValidFrom(LocalDate.of(2011, 1, 1));
        rule.setGrantorId("test-grantor");
        rule.setGranteeId(Set.of("test-grantee1", "test-grantee2", "auth-sign"));
        rule.setCategory(Category.TIMEREPORT);
        rule.setAccessLevels(Set.of(AccessLevel.READ));
        rule.setObjectId(Set.of("4444"));
        when(authorizationRuleRepository.findAll()).thenReturn(List.of(
            rule
        ));

        // Act
        Timereport timereport = new Timereport();
        Referenceday referenceday = new Referenceday();
        referenceday.setRefdate(LocalDate.of(2011, 1, 2));
        timereport.setReferenceday(referenceday);
        Employeecontract employeecontract = new Employeecontract();
        Employee employee = new Employee();
        Suborder suborder = new Suborder();
        Customerorder customerorder = new Customerorder();
        suborder.setCustomerorder(customerorder);
        timereport.setSuborder(suborder);
        timereport.setEmployeecontract(employeecontract);
        employeecontract.setEmployee(employee);
        employee.setSign("test-grantor");
        customerorder.setResponsible_hbt(employee);
        customerorder.setRespEmpHbtContract(employee);
        customerorder.setSign("4444");
        var authorized = authService.isAuthorized(timereport, AccessLevel.READ);

        // Assert
        assertEquals(true, authorized); // Assuming no matches in this case
    }

    @Test
    void testEmptyObjectId() {
        // Arrange
        var rule = new AuthorizationRule();
        rule.setObjectId(new HashSet<>());
        rule.setValidFrom(LocalDate.of(2011, 1, 1));
        rule.setGrantorId("test-grantor");
        rule.setGranteeId(Set.of("test-grantee1", "test-grantee2", "auth-sign"));
        rule.setCategory(Category.TIMEREPORT);
        rule.setAccessLevels(Set.of(AccessLevel.READ));
        when(authorizationRuleRepository.findAll()).thenReturn(List.of(
            rule
        ));

        // Act
        Timereport timereport = new Timereport();
        Referenceday referenceday = new Referenceday();
        referenceday.setRefdate(LocalDate.of(2011, 1, 2));
        timereport.setReferenceday(referenceday);
        Employeecontract employeecontract = new Employeecontract();
        Employee employee = new Employee();
        Suborder suborder = new Suborder();
        Customerorder customerorder = new Customerorder();
        suborder.setCustomerorder(customerorder);
        timereport.setSuborder(suborder);
        timereport.setEmployeecontract(employeecontract);
        employeecontract.setEmployee(employee);
        employee.setSign("test-grantor");
        ReflectionTestUtils.setField(employee, "id", 2L);
        customerorder.setResponsible_hbt(employee);
        customerorder.setRespEmpHbtContract(employee);
        var authorized = authService.isAuthorized(timereport, AccessLevel.READ);

        // Assert
        assertEquals(true, authorized); // Assuming no matches in this case
    }

}