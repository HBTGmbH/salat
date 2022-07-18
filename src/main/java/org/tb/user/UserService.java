package org.tb.user;

import lombok.RequiredArgsConstructor;
import org.passay.*;
import org.springframework.stereotype.Service;
import org.tb.common.GlobalConstants;
import org.tb.employee.domain.Employee;
import org.tb.employee.persistence.EmployeeDAO;

import static org.tb.common.GlobalConstants.EMPLOYEE_PASSWORD_MAX_LENGTH;
import static org.tb.common.GlobalConstants.EMPLOYEE_PASSWORD_MIN_LENGTH;

@Service
@RequiredArgsConstructor
public class UserService {

    private final EmployeeDAO employeeDAO;

    public boolean changePassword(long employeeId, String newPassword) {
        var characterRule = new CharacterCharacteristicsRule();
        characterRule.setNumberOfCharacteristics(3);
        characterRule.getRules().add(new CharacterRule(GermanCharacterData.UpperCase, 1));
        characterRule.getRules().add(new CharacterRule(GermanCharacterData.LowerCase, 1));
        characterRule.getRules().add(new CharacterRule(EnglishCharacterData.Digit, 1));
        characterRule.getRules().add(new CharacterRule(EnglishCharacterData.Special, 1));
        PasswordValidator validator = new PasswordValidator(
                new LengthRule(EMPLOYEE_PASSWORD_MIN_LENGTH, EMPLOYEE_PASSWORD_MAX_LENGTH),
                characterRule
        );
        var valid = validator.validate(new PasswordData(newPassword)).isValid();
        if(valid) {
            var employee = employeeDAO.getEmployeeById(employeeId);
            employee.changePassword(newPassword);
            employeeDAO.save(employee);
        }
        return valid;
    }

}
