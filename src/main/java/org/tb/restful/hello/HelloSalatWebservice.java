package org.tb.restful.hello;

import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.tb.bdom.Employee;
import org.tb.persistence.EmployeeDAO;

@RequiredArgsConstructor
@RestController("/rest/HelloSalatWebservice")
public class HelloSalatWebservice {

    private final EmployeeDAO employeeDAO;

    @GetMapping(path = "/sayHello", produces = APPLICATION_JSON_VALUE)
    @ResponseStatus(OK)
    public Message sayHello(@RequestParam("sign") String sign) {
        Employee employee = employeeDAO.getEmployeeBySign(sign);
        if (employee != null) {
          return new Message("Hello!", new Person(employee.getFirstname()));
        } else {
          return new Message("not found " + sign, null);
        }
    }

}

