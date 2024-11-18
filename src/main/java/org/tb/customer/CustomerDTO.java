package org.tb.customer;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class CustomerDTO {

  private Long id;
  private String name;
  private String shortName;
  private String address;
  private String createdBy;
  private LocalDateTime createdAt;
  private String updatedBy;
  private LocalDateTime updatedAt;
  private Integer updateCounter;
  
  public static CustomerDTO from(Customer customer) {
    return CustomerDTO.builder()
        .id(customer.getId())
        .name(customer.getName())
        .shortName(customer.getShortname())
        .address(customer.getAddress())
        .createdBy(customer.getCreatedby())
        .createdAt(customer.getCreated())
        .updatedBy(customer.getLastupdatedby())
        .updatedAt(customer.getLastupdate())
        .updateCounter(customer.getUpdatecounter())
        .build();
  }

  public void copyTo(Customer customer) {
    customer.setName(name);
    customer.setShortname(shortName);
    customer.setAddress(address);
    // do not copy id and audit fields
  }

  public boolean isNew() {
    return id == null;
  }

}
