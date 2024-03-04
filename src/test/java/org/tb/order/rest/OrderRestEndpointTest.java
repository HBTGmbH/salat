package org.tb.order.rest;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.tb.employee.domain.Employeecontract;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Employeeorder;
import org.tb.order.domain.Suborder;
import org.tb.order.persistence.EmployeeorderDAO;

@ExtendWith(MockitoExtension.class)
class OrderRestEndpointTest {

  @Mock
  EmployeeorderDAO employeeorderDAO;

  @InjectMocks
  OrderRestEndpoint orderRestEndpoint;

  @Test
  void createSuborderDataHierarchical() {
    Employeecontract employeecontract = newEmployeecontract();
    when(employeeorderDAO.getEmployeeorderByEmployeeContractIdAndSuborderIdAndDate(
        anyLong(), anyLong(), any())).thenReturn(newEmployeeorder());
    List<Suborder> testdata = createTestDatList(3, 3, createCustomerOrder());
    insertBacklinks(testdata);
    List<OrderData> res = orderRestEndpoint.createSuborderDataHierarchical(testdata,
        employeecontract,
        LocalDate.parse("2020-01-08"));
    assertThat(res.size()).isEqualTo(1);
    assertThat(res.getFirst().getSuborder().size()).isEqualTo(3);
  }

  private Employeeorder newEmployeeorder() {
    Employeeorder res = new Employeeorder();
    ReflectionTestUtils.setField(res, "id", (long) (Math.random() * 100));
    return res;

  }

  private Employeecontract newEmployeecontract() {
    Employeecontract res = new Employeecontract();
    ReflectionTestUtils.setField(res, "id", (long) (Math.random() * 100));
    return res;
  }

  private void insertBacklinks(List<Suborder> testdata) {
    testdata.forEach(order -> {
      order.getSuborders().forEach(suborder -> {
        suborder.setParentorder(order);
      });
      insertBacklinks(order.getSuborders());
    });

  }

  private Customerorder createCustomerOrder() {
    Customerorder res = new Customerorder();

    ReflectionTestUtils.setField(res, "id", (long) (Math.random() * 100));
    res.setShortdescription("Customerorder " + res.getId());
    return res;
  }

  private List<Suborder> createTestDatList(int depth, int length, Customerorder customerOrder) {
    List<Suborder> res = new ArrayList<>();
    for (int i = 0; i < length; i++) {
      res.add(createTestData(depth, customerOrder));
    }
    return res;
  }

  private Suborder createTestData(int depth, Customerorder customerOrder) {
    Suborder res = new Suborder();
    ReflectionTestUtils.setField(res, "id", (long) (Math.random() * 100));
    res.setDescription("order " + res.getId());
    res.setCustomerorder(customerOrder);
    res.setSuborders(createTestDatList(depth - 1, depth, customerOrder));
    return res;

  }
}