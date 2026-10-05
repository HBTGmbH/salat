package de.hbt.salat.budget.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import de.hbt.salat.common.domain.AuditedEntity;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.CustomerorderOption;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.service.CustomerorderService;
import de.hbt.salat.order.service.SuborderService;

/**
 * A small order tree for the budget tests (#1205): customer orders and suborders with ids, named by
 * their signs so that the cases stay readable, and answered by mocked order services the way the real
 * ones answer. Plans, flat rates and cost assignments refer to this tree by id.
 */
class OrderTree {

  /** An id no order and no suborder of the tree carries. */
  static final long UNKNOWN_ID = 9_999L;

  private final Map<String, Customerorder> orders = new LinkedHashMap<>();
  private final Map<String, Suborder> suborders = new LinkedHashMap<>();
  private long nextId = 1;

  /** Adds a suborder by its complete order sign; its order and its parents come along. */
  OrderTree with(String completeOrderSign) {
    suborderOf(completeOrderSign);
    return this;
  }

  Customerorder order(String sign) {
    return orders.computeIfAbsent(sign, key -> {
      var order = new Customerorder();
      setId(order, nextId++);
      order.setSign(key);
      order.setShortdescription("Order " + key);
      return order;
    });
  }

  long orderId(String sign) {
    return order(sign).getId();
  }

  /** The id of a suborder of the tree, {@code null} for none, {@link #UNKNOWN_ID} for one it does not hold. */
  Long suborderId(String completeOrderSign) {
    if (completeOrderSign == null) {
      return null;
    }
    var suborder = suborders.get(completeOrderSign);
    return suborder == null ? UNKNOWN_ID : suborder.getId();
  }

  Suborder suborder(String completeOrderSign) {
    return suborders.get(completeOrderSign);
  }

  /** Lets the mocked services answer from this tree. */
  OrderTree stub(CustomerorderService customerorderService, SuborderService suborderService) {
    when(customerorderService.getCustomerorderById(anyLong())).thenAnswer(i -> byId(i.getArgument(0)));
    when(customerorderService.getCustomerorderBySign(anyString())).thenAnswer(i -> orders.get(i.<String>getArgument(0)));
    when(customerorderService.getCustomerordersBySigns(any())).thenAnswer(i -> {
      Collection<String> signs = i.getArgument(0);
      return orders.values().stream().filter(order -> signs.contains(order.getSign())).toList();
    });
    when(customerorderService.getCustomerordersByIds(any())).thenAnswer(i -> {
      Collection<Long> ids = i.getArgument(0);
      return orders.values().stream().filter(order -> ids.contains(order.getId())).toList();
    });
    when(customerorderService.getCustomerorderIdBySign(anyString())).thenAnswer(i -> {
      var order = orders.get(i.<String>getArgument(0));
      return order == null ? null : order.getId();
    });
    when(customerorderService.getCustomerorderOptionsByIds(any())).thenAnswer(i -> {
      Collection<Long> ids = i.getArgument(0);
      return orders.values().stream().filter(order -> ids.contains(order.getId()))
          .sorted(Comparator.comparing(Customerorder::getSign))
          .map(order -> new CustomerorderOption(order.getId(), order.getSign(), order.getShortdescription(),
              order.getDescription(), null, null, order.getHide()))
          .toList();
    });
    when(customerorderService.getCustomerorderSignsByIds(any())).thenAnswer(i -> {
      Collection<Long> ids = i.getArgument(0);
      return orders.values().stream().filter(order -> ids.contains(order.getId()))
          .collect(Collectors.toMap(Customerorder::getId, Customerorder::getSign));
    });
    when(suborderService.getCompleteOrderSignsByIds(any())).thenAnswer(i -> {
      Collection<Long> ids = i.getArgument(0);
      return suborders.values().stream().filter(suborder -> ids.contains(suborder.getId()))
          .collect(Collectors.toMap(Suborder::getId, Suborder::getCompleteOrderSign));
    });
    when(suborderService.getSuborderById(anyLong())).thenAnswer(i -> suborders.values().stream()
        .filter(suborder -> suborder.getId().equals(i.<Long>getArgument(0))).findFirst().orElse(null));
    when(suborderService.getSubordersByCustomerorderId(anyLong())).thenAnswer(i -> suborders.values().stream()
        .filter(suborder -> suborder.getCustomerorder().getId().equals(i.<Long>getArgument(0))).toList());
    return this;
  }

  private Customerorder byId(long id) {
    return orders.values().stream().filter(order -> order.getId() == id).findFirst().orElse(null);
  }

  private Suborder suborderOf(String completeOrderSign) {
    var existing = suborders.get(completeOrderSign);
    if (existing != null) {
      return existing;
    }
    var separator = completeOrderSign.lastIndexOf('/');
    var parentSign = completeOrderSign.substring(0, separator);
    var parent = parentSign.contains("/") ? suborderOf(parentSign) : null;
    var suborder = new Suborder();
    setId(suborder, nextId++);
    suborder.setCustomerorder(order(completeOrderSign.substring(0, completeOrderSign.indexOf('/'))));
    suborder.setParentorder(parent);
    suborder.setSign(completeOrderSign.substring(separator + 1));
    suborder.setShortdescription(completeOrderSign);
    suborder.deriveCompleteOrderSign();
    suborders.put(completeOrderSign, suborder);
    return suborder;
  }

  private static void setId(AuditedEntity entity, long id) {
    try {
      var field = AuditedEntity.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(entity, id);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("cannot assign an id to the test record", e);
    }
  }

}
