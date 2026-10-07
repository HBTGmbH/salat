package de.hbt.salat.order.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import de.hbt.salat.common.command.CommandPublisher;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.customer.persistence.CustomerDAO;
import de.hbt.salat.employee.persistence.EmployeeDAO;
import de.hbt.salat.order.persistence.CustomerorderDAO;
import de.hbt.salat.order.persistence.CustomerorderRepository;
import de.hbt.salat.order.persistence.SuborderDAO;
import de.hbt.salat.order.persistence.SuborderRepository;

/**
 * Löschen und Kopieren eines Auftrags oder Unterauftrags, den es nicht gibt, lehnt der Service mit „nicht gefunden"
 * ab, statt nichts zu tun und Erfolg zu melden oder an der fehlenden Zeile zu scheitern (#1401).
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class OrderNotFoundTest {

  private static final long UNKNOWN_ID = 999L;

  private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
  private final CustomerorderRepository customerorderRepository = mock(CustomerorderRepository.class);
  private final SuborderRepository suborderRepository = mock(SuborderRepository.class);

  @Test
  void deleting_an_unknown_customer_order_is_refused() {
    assertThatThrownBy(() -> customerorderService().deleteCustomerorderById(UNKNOWN_ID))
        .isInstanceOf(InvalidDataException.class)
        .hasMessageContaining(ErrorCode.CO_NOT_FOUND.getCode());
    verify(eventPublisher, never()).publishEvent(any());
    verify(customerorderRepository, never()).deleteById(anyLong());
  }

  @Test
  void deleting_an_unknown_suborder_is_refused() {
    assertThatThrownBy(() -> suborderService().deleteSuborderById(UNKNOWN_ID))
        .isInstanceOf(InvalidDataException.class)
        .hasMessageContaining(ErrorCode.SO_NOT_FOUND.getCode());
    verify(eventPublisher, never()).publishEvent(any());
    verify(suborderRepository, never()).deleteById(anyLong());
  }

  @Test
  void copying_an_unknown_suborder_is_refused() {
    assertThatThrownBy(() -> suborderService().createCopy(UNKNOWN_ID))
        .isInstanceOf(InvalidDataException.class)
        .hasMessageContaining(ErrorCode.SO_NOT_FOUND.getCode());
  }

  private CustomerorderService customerorderService() {
    return new CustomerorderService(eventPublisher, mock(CommandPublisher.class), mock(CustomerorderDAO.class),
        mock(CustomerDAO.class), mock(EmployeeDAO.class), customerorderRepository, mock(SpecialOrders.class));
  }

  private SuborderService suborderService() {
    return new SuborderService(eventPublisher, mock(CommandPublisher.class), mock(SuborderDAO.class),
        suborderRepository, mock(CustomerorderService.class), mock(SpecialOrders.class));
  }
}
