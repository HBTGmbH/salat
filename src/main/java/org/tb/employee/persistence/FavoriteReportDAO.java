package org.tb.employee.persistence;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tb.auth.AuthService;
import org.tb.auth.AuthorizedUser;
import org.tb.employee.domain.EmployeeFavoriteReport;

@Component
@RequiredArgsConstructor
public class FavoriteReportDAO {

  private final FavoriteReportRepository favoriteReportRepository;
  private final AuthorizedUser authorizedUser;
  private final AuthService authService;


  /**
   * Gets the employee with the given id.
   */
  public List<EmployeeFavoriteReport> getByEmployeeId() {
        return favoriteReportRepository.findAllByEmployeeId(authorizedUser.getEmployeeId());
  }

  public EmployeeFavoriteReport save(EmployeeFavoriteReport favoriteReport) {
    return favoriteReportRepository.save(favoriteReport);
  }

  public void delete(Long id) {
    favoriteReportRepository.deleteById(id);
  }

}
