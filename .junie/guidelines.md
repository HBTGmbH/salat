# Project Guidelines for Salat

## Project Overview

**Salat** is a time tracking and timesheet management application developed by HBT GmbH. It allows employees to track their working hours, manage timereports, handle customer orders, generate reports, and manage invoices.

## Technology Stack

- **Java**: 21 (LTS)
- **Framework**: Spring Boot 3.5.6
- **Build Tool**: Maven (mvnw wrapper included)
- **Database**: MySQL with Liquibase for migrations
- **ORM**: Spring Data JPA with Hibernate
- **Security**: Spring Security with OAuth2 resource server support
- **UI Technologies**:
  - Legacy UI: JSP/JSTL with Struts
  - Modern UI: Thymeleaf with Tabler.io
  - Frontend libraries: Bootstrap 5, jQuery, Plotly.js
- **Mapping**: MapStruct for DTO/Entity conversions
- **Code Generation**: Lombok for reducing boilerplate
- **Excel Export**: Apache POI
- **API Documentation**: SpringDoc OpenAPI (Swagger UI)
- **Testing**: JUnit 5, Mockito, Spring Boot Test, ArchUnit
- **Deployment**: Docker with docker-compose

## Project Structure

```
salat/
├── src/main/java/org/tb/          # Main application code
│   ├── auth/                       # Authentication and authorization
│   ├── employee/                   # Employee management
│   ├── customer/                   # Customer management
│   ├── order/                      # Order/Project management
│   ├── dailyreport/                # Daily reports and timereports
│   ├── reporting/                  # Report generation
│   ├── invoice/                    # Invoice management
│   └── common/                     # Shared utilities
├── src/main/resources/
│   ├── db/changelog/               # Liquibase database migrations
│   ├── org/tb/web/                 # i18n message resources
│   └── templates/                  # Thymeleaf templates
├── src/main/webapp/                # Legacy JSP pages and static resources
│   ├── WEB-INF/                    # Web configuration
│   ├── employee/, customer/, etc.  # JSP pages by module
│   ├── scripts/                    # JavaScript files
│   └── style/                      # CSS files
├── src/test/java/                  # Unit and integration tests
├── docs/                           # Documentation
├── testdb/                         # Test database scripts
└── pom.xml                         # Maven configuration
```

## Running the Project

### Requirements
- Java 21
- Docker (running)
- docker-compose

### Local Development

1. **Build the Docker image**:
   ```bash
   ./mvnw spring-boot:build-image
   ```

2. **Start the application**:
   ```bash
   docker-compose up -d
   ```

3. **Access the application**:
   - Open: http://localhost:8080?employee-sign=tt
   - Valid test users: `admin`, `bm`, `tt`

4. **Shutdown**:
   ```bash
   docker-compose down
   ```

### Database Only (for debugging)
```bash
docker-compose -f docker-compose-infra.yml up
```

### Environment Variables
Required for each environment:
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_DATASOURCE_URL`

## Testing Guidelines

### Running Tests

**Run all tests**:
```bash
./mvnw test
```

**Run specific test class**:
```bash
./mvnw test -Dtest=EmployeeorderServiceTest
```

**Run tests for a specific package**:
```bash
./mvnw test -Dtest="org.tb.order.**"
```

### Test Structure
- Tests are located in `src/test/java/` matching the source package structure
- Test classes follow the naming convention: `*Test.java`
- Use JUnit 5 annotations: `@Test`, `@BeforeEach`, `@AfterEach`
- Integration tests use `@SpringBootTest` annotation
- Mock dependencies using Mockito's `@Mock` and `@InjectMocks`

### When to Run Tests
- **Always** run tests related to modified files before submitting changes
- If modifying core services (employee, order, timereport), run the full test suite
- Integration tests may require database setup

## Build Instructions

### Standard Build
```bash
./mvnw clean package
```

### Skip Tests
```bash
./mvnw clean package -DskipTests
```

### Build Docker Image
```bash
./mvnw spring-boot:build-image
```

### Troubleshooting Build Issues

**Missing BuildProperties Error**:
If you encounter `Consider defining a bean of type 'org.springframework.boot.info.BuildProperties'`:
```bash
./mvnw package
```
This generates the required `build-info.properties` file.

## Code Style Guidelines

### General Conventions
- **Code Style**: Google Java Style (see `intellij-java-google-style.xml`)
- **Encoding**: UTF-8 for all files
- **Line Endings**: LF (Unix-style)

### Java Conventions
- Use Lombok annotations (`@Data`, `@Builder`, `@Slf4j`) to reduce boilerplate
- Use MapStruct for entity-to-DTO conversions
- Follow Spring Boot conventions for component naming:
  - `*Controller` for REST/Web controllers
  - `*Service` for business logic
  - `*Repository` or `*DAO` for data access
  - `*Mapper` for MapStruct interfaces
- Use constructor injection over field injection
- Prefer immutable objects where possible

### Package Organization
- Group by feature/module (employee, order, customer, etc.)
- Within each module:
  - `domain/` - JPA entities
  - `persistence/` - Repositories/DAOs
  - `service/` - Business logic
  - `action/` - Legacy Struts actions
  - `rest/` - REST controllers
  - `viewmodel/` - DTOs for UI

### Database Changes
- **Always** use Liquibase for database changes
- Add changesets to: `src/main/resources/db/changelog/db.changelog-master.yaml`
- Never modify the database schema directly

### Testing Conventions
- Write tests for new business logic
- Aim for meaningful test names: `shouldReturnEmployeeWhenValidIdProvided()`
- Test both success and failure scenarios
- Mock external dependencies

## Architecture Notes

### System Architecture
- **Architectural Style**: Modular Monolith
  - The codebase is organized into modules by domain capability (employee, order, customer, etc.)
  - Modules are packaged and deployed together as a single application
  - Module boundaries define allowed dependencies and collaboration patterns

### Dependency and Coupling Rules
- **Intra-module coupling** is allowed; **inter-module coupling** must respect declared boundaries
- **Cyclic dependencies** between modules are **not permitted**
- Cross-module collaboration that would introduce cycles must be decoupled via **Spring's event mechanism**:
  - Modules publish domain/application events via `ApplicationEventPublisher`
  - Other modules subscribe using event listeners
  - Events should carry stable, minimal data contracts to reduce coupling

### Technology Positioning

**Legacy UI Stack (Maintenance Mode)**:
- Struts 1.2
- JSP/JSTL
- **Important**: Existing screens may continue to function but should not receive major new features
- Any new UI work should avoid adding to the legacy stack

**Target UI Stack (Future Direction)**:
- Spring Web MVC (controllers, validation, handler methods)
- Thymeleaf templates (server-side rendering)
- Bootstrap 5 for styling and components
- Tabler (tabler.io) design system layered on top of Bootstrap

### Migration from JSP to Thymeleaf
The project is actively migrating from legacy JSP/Struts pages to modern Thymeleaf templates. Both UIs coexist:
- Legacy UI: `/src/main/webapp/**/*.jsp`
- Modern UI: `/src/main/resources/templates/**/*.html`
- Test new UI features via: http://localhost:8080/reporting/jobs2?employee-sign=tt

**Migration Guidelines**:
- Do **not** expand Struts/JSP usage for new features
- New screens/features should use Spring MVC + Thymeleaf
- When touching legacy screens for significant changes, consider opportunistic migration to the target stack
- Maintain compatibility during migration; multiple UI technologies may coexist temporarily

### Controller and View Guidelines (Target Stack)

**Controllers**:
- Use Spring Web MVC controllers to expose application capabilities
- Keep controller logic thin; delegate to services within the same module
- Place cross-cutting Spring Boot/Spring Security enabling annotations (e.g., `@EnableMethodSecurity`, `@EnableScheduling`, `@EnableAsync`) on the application class (`SalatApplication`) unless there is a strong, explicit reason to scope them to a specific configuration class

**Views**:
- Prefer Thymeleaf templates backed by the module's controllers
- Adopt Bootstrap 5 + Tabler components for UI layout and widgets
- Prefer Thymeleaf fragments to reduce duplication
- Extract reusable sections (tables, headers, toolbars, forms) into `templates/fragments` and include them via `th:replace`/`th:include`
- Shared layout and fragments should live under `templates/layout` and `templates/fragments`

### Business Logic Refactoring
Some business logic is currently in helper classes and should be moved to services:
- `TimereportHelper` → `WorkingdayService`
- `TrainingHelper` → `DurationUtils`

Refer to README.md section "Business-logic that should be refactored" for details.

## Additional Resources

- **API Documentation**: Available at `/swagger-ui.html` when running locally
- **Test UI Guide**: See `docs/how-to-test-new-ui.md`
- **Database Migrations**: Check `src/main/resources/db/changelog/`
- **Message Resources**: Located in `src/main/resources/org/tb/web/MessageResources_*.properties`

## Before Submitting Changes

1. ✓ Run tests related to modified code, including ArchitectureTest
2. ✓ Ensure the project builds successfully: `./mvnw clean package`
3. ✓ Check that no existing tests are broken
4. ✓ Verify code follows the Google Java Style conventions
5. ✓ Add Liquibase changeset if database schema changes are required
6. ✓ Update relevant documentation if adding new features
