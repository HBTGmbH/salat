package org.tb.user;

import java.time.LocalDateTime;

public record DecryptedUserAccessToken(Long id, String tokenId, String tokenSecret, LocalDateTime validUntil, String comment, long employeeId) {

}
