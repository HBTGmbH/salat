package org.tb.employee.controller;

import lombok.Data;

@Data
public class EmployeeForm {

    private Long id;
    private String firstname;
    private String lastname;
    private String sign;
    private String loginname;
    private String status;
    private String gender;

}
