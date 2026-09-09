package org.tb.budget.service;

import static org.apache.commons.lang3.StringUtils.trimToNull;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.budget.domain.OrderPricing;
import org.tb.budget.domain.OrderPricingData;
import org.tb.budget.domain.OrderPricingLookup;
import org.tb.budget.persistence.OrderPricingRepository;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.exception.ErrorCode;
import org.tb.common.exception.InvalidDataException;
import org.tb.order.service.SuborderService;

@Service
@Transactional
@RequiredArgsConstructor
@Authorized
public class OrderPricingService {

    private final OrderPricingRepository orderPricingRepository;
    private final SuborderService suborderService;

    @Transactional(readOnly = true)
    public List<OrderPricing> getAll() {
        return orderPricingRepository.findAllByOrderByCustomerorderSignAscValidFromAsc();
    }

    /**
     * The pricings of the list view (#949), optionally narrowed to one customer order and, unless
     * asked otherwise, to those that have not expired — see
     * {@link OrderPricing#getCurrentlyValid()} for what counts as expired.
     */
    @Transactional(readOnly = true)
    public List<OrderPricing> getFiltered(String customerorderSign, boolean showInactive) {
        var sign = trimToNull(customerorderSign);
        var pricings = sign == null ? getAll() : getByCustomerorderSign(sign);
        return showInactive ? pricings : pricings.stream().filter(OrderPricing::getCurrentlyValid).toList();
    }

    /** The customer orders that have at least one pricing — the filter options of the list view. */
    @Transactional(readOnly = true)
    public List<String> getCustomerorderSignsWithPricing() {
        return orderPricingRepository.findDistinctCustomerorderSigns();
    }

    @Transactional(readOnly = true)
    public OrderPricing getById(long id) {
        return orderPricingRepository.findById(id)
            .orElseThrow(() -> new InvalidDataException(ErrorCode.BU_PRICING_NOT_FOUND, id));
    }

    @Transactional(readOnly = true)
    public List<OrderPricing> getByCustomerorderSign(String customerorderSign) {
        return orderPricingRepository.findByCustomerorderSignOrderByValidFromAsc(customerorderSign);
    }

    /**
     * Loads the pricings of the given customer orders into an in-memory lookup, which resolves the
     * whole matching hierarchy. Rates are resolved once per time report, so they must not be
     * resolved by query.
     */
    @Transactional(readOnly = true)
    public OrderPricingLookup lookupFor(Collection<String> customerorderSigns) {
        if (customerorderSigns.isEmpty()) {
            return OrderPricingLookup.of(List.of());
        }
        return OrderPricingLookup.of(orderPricingRepository.findByCustomerorderSignInOrderByIdAsc(customerorderSigns));
    }

    @Authorized(requiresManager = true)
    public void save(OrderPricingData data) {
        var validUntil = data.validUntil() != null ? data.validUntil() : LocalDate.of(2999, 12, 31);
        checkSuborderPatternMatches(data.customerorderSign(), data.suborderSign());
        checkNoOverlap(data.customerorderSign(), data.suborderSign(), data.employeeSign(),
            data.validFrom(), validUntil, null);
        var pricing = new OrderPricing();
        apply(pricing, data);
        orderPricingRepository.save(pricing);
    }

    @Authorized(requiresManager = true)
    public void update(long id, OrderPricingData data) {
        var validUntil = data.validUntil() != null ? data.validUntil() : LocalDate.of(2999, 12, 31);
        checkSuborderPatternMatches(data.customerorderSign(), data.suborderSign());
        checkNoOverlap(data.customerorderSign(), data.suborderSign(), data.employeeSign(),
            data.validFrom(), validUntil, id);
        var pricing = getById(id);
        apply(pricing, data);
        orderPricingRepository.save(pricing);
    }

    @Authorized(requiresManager = true)
    public void delete(long id) {
        orderPricingRepository.deleteById(id);
    }

    /**
     * The suborder pattern is entered by hand, so it can be a typo or refer to another customer
     * order. It would then never match during controlling and the rate would silently fall back to
     * the order-wide one, so require that it covers at least one suborder of the chosen order.
     */
    private void checkSuborderPatternMatches(String customerorderSign, String suborderSign) {
        if (suborderSign != null && !suborderService.existsSuborderMatching(customerorderSign, suborderSign)) {
            throw new BusinessRuleException(ErrorCode.BU_SUBORDER_NOT_IN_ORDER);
        }
    }

    private void checkNoOverlap(String co, String so, String emp, LocalDate from, LocalDate until, Long excludeId) {
        var overlapping = orderPricingRepository.findOverlapping(co, so, emp, from, until, excludeId);
        if (!overlapping.isEmpty()) {
            throw new BusinessRuleException(ErrorCode.BU_PRICING_OVERLAP);
        }
    }

    private void apply(OrderPricing pricing, OrderPricingData data) {
        pricing.setCustomerorderSign(data.customerorderSign());
        pricing.setSuborderSign(data.suborderSign());
        pricing.setEmployeeSign(data.employeeSign());
        pricing.setDescription(data.description());
        pricing.setPriceCentsPerHour(data.priceCentsPerHour());
        pricing.setValidFrom(data.validFrom());
        pricing.setValidUntil(data.validUntil() != null ? data.validUntil() : LocalDate.of(2999, 12, 31));
    }

}
