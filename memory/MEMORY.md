# Project Memory

## Invoice Thymeleaf Migration (branch: 592-migrate-invoice-to-thymeleaf)
- New Spring MVC controller: `org/tb/invoice/controller/InvoiceController.java` at `/invoice`
- New form DTO: `org/tb/invoice/controller/InvoiceForm.java` (implements `InvoiceColumnHeaders`)
- New templates: `templates/invoice/show.html` (uses base layout) and `templates/invoice/print.html` (standalone)
- Print template is standalone (no base layout) to preserve exact printable invoice layout
- Session stores `invoiceData` (key `"invoiceData"`) between generate/print/export
- POST `/invoice` = updateOptions, POST `/invoice/generate` = generate, POST `/invoice/print` = print, POST `/invoice/export` = Excel
- JS `submitUpdate()` / `submitPrint()` / `submitExport()` change form action before submit
- Nav link in `layout/base.html` updated to `@{/invoice}`
- Duration formatting in templates: `${@durationUtils.format(value)}` (bean `DurationUtilsBean`)

## Welcome Page Thymeleaf Migration (branch: 596-migrate-showwelcome-to-thymeleaf)
- New Spring MVC controller: `org/tb/dailyreport/controller/WelcomeController.java` at `/welcome`
- New template: `templates/dailyreport/welcome.html` (uses base layout)
- `TimereportService` has new overloaded `createTimeReportWarnings(long, MessageSourceAccessor)` method
- POST `/welcome?task=refresh` = update selected employee contract (redirect to GET)
- POST `/welcome?task=switch-login` = switch login employee (redirect to GET)
- Session attrs still set (for Struts compatibility): `currentEmployeeContract`, `currentEmployeeId`, `loginEmployees`, `employeecontracts`, warnings, overtime, vacation
- Struts global forward `showWelcome` now redirects to `/welcome` (used by `LoginRequiredAction`)
- `backtomenu` and `cancel` Struts forwards updated from JSP path to `/welcome` redirect
- `base.html` nav link updated to `/welcome`
- Deleted: `ShowWelcomeAction.java`, `ShowWelcomeForm.java`, `WelcomeViewHelper.java`, `showWelcome.jsp`

## Employee Module Thymeleaf Migration (branch: 598-migrate-employee-employeecontract-and-employeeorder-to-thymeleaf)

### Employee CRUD (`/employees`)
- Controller: `org/tb/employee/controller/EmployeeController.java`
- Form DTO: `org/tb/employee/controller/EmployeeForm.java`
- Templates: `templates/employee/employee-list.html`, `templates/employee/employee-form.html`
- `@PreAuthorize("hasRole('MANAGER')")` on create/edit/delete; list is open
- Filter employees with `z_` prefix from all lists/dropdowns (internal accounts)
- Prevents deleting self (uses `employeeService.getLoginEmployee()`)

### Employee Contract CRUD (`/employees/contracts`)
- Controller: `org/tb/employee/controller/EmployeecontractController.java`
- Form DTO: `org/tb/employee/controller/EmployeecontractForm.java` (has typed getters)
- Templates: `templates/employee/employee-contract-list.html`, `templates/employee/employee-contract-form.html`
- `ContractStoredInfo.getLog()` returns conflict resolution log messages → flash attribute `logs` on list page
- Overtime management: POST `/{id}/overtime`, adjusts effective date if before contract start
- Supervisors: `employeeService.getEmployeesWithValidContracts()` (not all employees)

### Employee Order CRUD (`/orders/employeeorders`)
- Controller: `org/tb/order/controller/EmployeeorderController.java`
- Form DTO: `org/tb/order/controller/EmployeeorderForm.java` (has typed getters)
- Templates: `templates/order/employee-order-list.html`, `templates/order/employee-order-form.html`
- Manager check: `authorizedUser.isManager()` → all orders; else `getCustomerOrdersByResponsibleEmployeeId(authorizedEmployee.getEmployeeId())`
- Cascading dropdowns: form GET re-submit with `orderId` to refresh suborders server-side
- "Adjust dates" bulk action: POST `/adjust-dates` adjusts/deletes mismatched employee orders
- `showActualHours` toggle wraps results in `EmployeeOrderViewDecorator` (adds `duration` + `getDifference()`)
- Suborders filtered: `!so.isHide()` and optionally `so.getCurrentlyValid()`
- Deleted: all Struts action classes in `org/tb/employee/action/` and `org/tb/order/action/` (employee order ones)
- Deleted JSPs: `employee/{addEmployee,showEmployee,addEmployeeContract,showEmployeeContract}.jsp`, `order/{addEmployeeOrder,showEmployeeOrder}.jsp`

## Message Resource Files

- Files: `src/main/resources/org/tb/web/MessageResources.properties` (DE) and `MessageResources_en.properties` (EN)
- Encoding: UTF-8
- **Never use Edit/Write tools on these files** — they corrupt the encoding
- **Always append new keys using terminal commands:**
  ```bash
  printf '\nkey=Value\n' >> MessageResources_en.properties
  printf '\nkey=Wert mit Umlaut \xc3\xbc\n' >> MessageResources.properties
  ```
- Use `\xc3\xbc` for ü, `\xc3\xb6` for ö, `\xc3\xa4` for ä etc. (UTF-8 hex bytes) in printf for German umlauts
