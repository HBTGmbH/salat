package org.tb.dailyreport.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.dailyreport.domain.TrainingInformation;
import org.tb.dailyreport.persistence.TrainingDAO;
import org.tb.employee.domain.Employeecontract;

@Service
@Transactional
@RequiredArgsConstructor
public class TrainingService {

  private final TrainingDAO trainingDAO;


  public List<TrainingInformation> getCommonTrainingTimesByDates(LocalDate startdate, LocalDate enddate, Long orderID) {
    return trainingDAO.getCommonTrainingTimesByDates(startdate, enddate, orderID);
  }

  public Optional<TrainingInformation> getCommonTrainingTimesByDatesAndEmployeeContractId(
      Employeecontract employeecontract, LocalDate startdate, LocalDate enddate, long trainingCustomerorderId) {
    return trainingDAO.getCommonTrainingTimesByDatesAndEmployeeContractId(employeecontract, startdate, enddate, trainingCustomerorderId);
  }

  public Optional<TrainingInformation> getProjectTrainingTimesByDatesAndEmployeeContractId(
      Employeecontract employeecontract, LocalDate startdate, LocalDate enddate) {
    return trainingDAO.getProjectTrainingTimesByDatesAndEmployeeContractId(employeecontract, startdate, enddate);
  }

  public List<TrainingInformation> getProjectTrainingTimesByDates(LocalDate startdate, LocalDate enddate) {
    return trainingDAO.getProjectTrainingTimesByDates(startdate, enddate);
  }
}
