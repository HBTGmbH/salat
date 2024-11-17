## Run locally

Requirements:

- Java 21
- Docker (running)
- docker-compose

Steps to start Salat locally:

1. Build the image: `./mvnw spring-boot:build-image`
2. Run docker-compose: `docker-compose up -d` (in newer docker versions use `docker compose up -d`)
3. That's it. Salat should now be running. To check, open in browser: <http://localhost:8080?employee-sign=tt>

Shutdown:
1. Stop docker-compose: CTRL+C
2. Stop built containers: `docker-compose stop` (in newer docker versions use `docker compose stop`)
3. If you want to remove the containers: `docker-compose down` (in newer docker versions use `docker compose down`)

### Login
Open the URL <http://localhost:8080?employee-sign=<sign>>

You can change the `employee-sign` parameter to login as a different user.
It is even possible to append `?employee-sign=<sign>` to any URL, to log in the user.

Valid employee-signs in the test-dataset are:
  1. **admin**: Administrator
  2. **bm**: "Bossy Bossmann", Administrator
  3. **tt**: "Testy Testmann", Employee

### Logout
Open the URL <http://localhost:8080?logout>

### Debugging
To start only the local database, without the Salat application:
`docker-compose -f docker-compose-infra.yml up`

⚠️ Be sure to remove existing docker containers before by running `docker-compose down` if you had the application running before.


### Troubleshooting

#### Missing BuildProperties
Should you encounter the error

    Consider defining a bean of type 'org.springframework.boot.info.BuildProperties' in your configuration.

This can be fixed by running

    ./mvnw package

Explanation: Maven creates a file named `build-info.properties`.
This file might be missing when an IDE does not create it. (Happened to Klaus with IntelliJ IDEA, despite Maven Integration).

(by Klaus)

#### DB-Changes
Changes to the data base are collected via Liquibase in the following file:

    src/main/resources/db/changelog/db.changelog-master.yaml

## Environment variables

The following environment variables must be set in each environment/stage:

```
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
SPRING_DATASOURCE_URL
```

The following is an example for local testing with the included docker-compose file:

```
SPRING_DATASOURCE_USERNAME=salattest
SPRING_DATASOURCE_PASSWORD=salattest
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/salat?useUnicode=true&useJDBCCompliantTimezoneShift=true&serverTimezone=Europe/Berlin&useLegacyDatetimeCode=false&autoReconnect=true
```

## Business-logic that should be refactored (i.e. move to service)

- AfterLogin#createWarnings - erzeugt Warnungen, die dem User angezeigt werden sollen. Diese weisen 
  auf Fehler bzw. offenen TODOs hin. Könnte man in einen Service packen und dann via
  scheduled Job regelmässig erzeugen lassen und in der DB speichern.
- AfterLogin#handleOvertime - berechnet Überstunden zur Anzeige beim User. Sollte in den OvertimeService
  überführt werden. Dieser existiert bereits und ist auch in der Lage, Überstunden zu
  berechnen. Könnte man entsprechend zusammenführen.
- StoreCustomerAction#executeAuthenticated - bei task=save wird ein Customer angelegt. Sollte im 
  CustomerService sein.
- DeleteCustomerAction#executeAuthenticated - auch dies sollte weitestgehend im CustomerService sein.
- TimereportHelper#determineBeginTimeToDisplay - move to WorkingdayService
- TimereportHelper#determineTimesToDisplay - move to WorkingdayService and introduce a better value class
  to carry the result
- TimereportHelper#calculateLaborTime - more or less just a sum up time on Timereports,
  maybe this can be done without any service, just use Java streaming API and Duration.plus
- TimereportHelper#checkLaborTimeMaximum - move to WorkingdayService? Or maybe a business rule in
  TimereportService that creates a warning? Or when it's a warning, maybe add to the WarningService.
- TimereportHelper#calculateQuittingTime - nach WorkingdayService
- TrainingHelper#fromDBtimeToString - nach DurationUtils als neue Methode
  formatWithWorkingsdays(Duration duration, Duration dailyWorkingTime
- TrainingHelper#hoursMinToString - better use Duration and then DurationUtils#format
- StoreEmployeeAction#executeAuthenticated - move storing of Employee to EmployeeService
- DeleteEmployeeAction#executeAuthenticated - move deletion to EmployeeService
- StoreEmployeecontractAction#executeAuthenticated - move storing of Employeecontract to EmployeecontractService
- DeleteEmployeecontractAction#executeAuthenticated - move deletion to EmployeecontractService
- StoreCustomerorderAction#executeAuthenticated - move storing to CustomerorderService
- DeleteCustomerorderAction#executeAuthenticated - move deletion to CustomerorderService
- StoreEmployeeorderAction#executeAuthenticated - move storing to EmployeeorderService
- DeleteEmployeeorderAction#executeAuthenticated - move deletion to EmployeeorderService
- StoreSuborderAction#executeAuthenticated - move storing to SuborderService
- StoreSuborderAction#executeAuthenticated - move copy to SuborderService
- DeleteSuborderAction#executeAuthenticated - move deletion to SuborderService
- GenerateMultipleEmployeeordersAction - move storing to EmployeeorderService
