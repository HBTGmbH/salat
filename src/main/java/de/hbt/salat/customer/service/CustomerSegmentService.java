package de.hbt.salat.customer.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.customer.domain.CustomerSegment;
import de.hbt.salat.customer.persistence.CustomerSegmentRepository;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.InvalidDataException;

@Service
@Transactional
@RequiredArgsConstructor
@Authorized
public class CustomerSegmentService {

    private final CustomerSegmentRepository customerSegmentRepository;

    @Transactional(readOnly = true)
    public CustomerSegment getById(long id) {
        return customerSegmentRepository.findById(id)
            .orElseThrow(() -> new InvalidDataException(ErrorCode.CU_SEGMENT_NOT_FOUND, id));
    }

    @Transactional(readOnly = true)
    public List<CustomerSegment> getAll() {
        return customerSegmentRepository.findAllByOrderByNameAsc();
    }

    @Authorized(requiresManager = true)
    public CustomerSegment create(String name) {
        var segment = new CustomerSegment();
        segment.setName(name);
        return customerSegmentRepository.save(segment);
    }

    @Authorized(requiresManager = true)
    public void update(long id, String name) {
        var segment = getById(id);
        segment.setName(name);
        customerSegmentRepository.save(segment);
    }

    @Authorized(requiresManager = true)
    public void delete(long id) {
        customerSegmentRepository.deleteById(id);
    }

}
