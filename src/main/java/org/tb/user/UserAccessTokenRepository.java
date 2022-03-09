package org.tb.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserAccessTokenRepository extends CrudRepository<UserAccessToken, Long> {

  List<UserAccessToken> findAllByEmployeeIdOrderById(long employeeId);

  Optional<UserAccessToken> findByTokenId(String tokenId);

}
