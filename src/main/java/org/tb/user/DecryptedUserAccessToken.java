package org.tb.user;

import java.time.LocalDateTime;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class DecryptedUserAccessToken {
  private final Long id;
  private final String tokenId;
  private final String tokenSecret;
  private final LocalDateTime validUntil;
  private final String comment;
  private final long employeeId;
}
