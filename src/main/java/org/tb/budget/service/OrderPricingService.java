package org.tb.budget.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.budget.domain.OrderPricing;
import org.tb.budget.domain.OrderPricingData;
import org.tb.budget.persistence.OrderPricingRepository;
import org.tb.common.exception.ErrorCode;
import org.tb.common.exception.InvalidDataException;

@Service
@Transactional
@RequiredArgsConstructor
@Authorized
public class OrderPricingService {

    private final OrderPricingRepository orderPricingRepository;

    @Transactional(readOnly = true)
    public OrderPricing getById(long id) {
        return orderPricingRepository.findById(id)
            .orElseThrow(() -> new InvalidDataException(ErrorCode.BU_PRICING_NOT_FOUND, id));
    }

    @Transactional(readOnly = true)
    public List<OrderPricing> getByCustomerorderSign(String customerorderSign) {
        return orderPricingRepository.findByCustomerorderSign(customerorderSign);
    }

    /**
     * Fallback hierarchy: employee-specific → suborder-wide → order-wide.
     */
    @Transactional(readOnly = true)
    public Optional<OrderPricing> findEffectiveRate(String customerorderSign, String suborderSign, String employeeSign, LocalDate date) {
        if (suborderSign != null && employeeSign != null) {
            var rates = orderPricingRepository.findEffectiveEmployeeSpecific(customerorderSign, suborderSign, employeeSign, date);
            if (!rates.isEmpty()) return Optional.of(rates.get(0));
        }
        if (suborderSign != null) {
            var rates = orderPricingRepository.findEffectiveSuborderWide(customerorderSign, suborderSign, date);
            if (!rates.isEmpty()) return Optional.of(rates.get(0));
        }
        var rates = orderPricingRepository.findEffectiveOrderWide(customerorderSign, date);
        if (!rates.isEmpty()) return Optional.of(rates.get(0));
        return Optional.empty();
    }

    @Authorized(requiresManager = true)
    public void save(OrderPricingData data) {
        var pricing = new OrderPricing();
        apply(pricing, data);
        orderPricingRepository.save(pricing);
    }

    @Authorized(requiresManager = true)
    public void update(long id, OrderPricingData data) {
        var pricing = getById(id);
        apply(pricing, data);
        orderPricingRepository.save(pricing);
    }

    @Authorized(requiresManager = true)
    public void delete(long id) {
        orderPricingRepository.deleteById(id);
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
