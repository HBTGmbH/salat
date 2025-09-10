package org.tb;

import static com.tngtech.archunit.lang.Priority.HIGH;
import static com.tngtech.archunit.lang.Priority.MEDIUM;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.priority;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeGradleTestFixtures;
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeJars;
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import java.util.Set;
import org.springframework.data.domain.Persistable;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

@AnalyzeClasses(packages = "org.tb", importOptions = {DoNotIncludeTests.class, DoNotIncludeJars.class, DoNotIncludeGradleTestFixtures.class})
public class ArchitectureTest {

  @ArchTest
  static final ArchRule daoMethodsMustStartWithGetOrFind = priority(MEDIUM).methods()
        .that().areNotPrivate()
        .and().areDeclaredInClassesThat().haveSimpleNameEndingWith("DAO")
        .should().haveNameStartingWith("get")
        .orShould().haveNameStartingWith("find");

  @ArchTest
  static final ArchRule commonShouldNotAccessOtherSalatPackages = priority(HIGH).noClasses().that()
      .resideInAPackage("org.tb.common..")
      .should().dependOnClassesThat(new OnlyOwnDependencyPredicate("common must import nothing", "org.tb.common."));

  @ArchTest
  static final ArchRule authShouldAccessCommonOnly = priority(HIGH).noClasses().that()
      .resideInAPackage("org.tb.auth..")
      .should().dependOnClassesThat(new OnlyOwnDependencyPredicate("auth must only import common", "org.tb.common.", "org.tb.auth."));

  @ArchTest
  static final ArchRule customerShouldAccessCommonAuthOnly = priority(HIGH).noClasses().that()
      .resideInAPackage("org.tb.auth..")
      .should().dependOnClassesThat(new OnlyOwnDependencyPredicate("customer must only import common, auth", "org.tb.common.", "org.tb.auth.", "org.tb.customer."));

  @ArchTest
  static final ArchRule employeeShouldAccessCommonAuthOnly = priority(HIGH).noClasses().that()
      .resideInAPackage("org.tb.auth..")
      .should().dependOnClassesThat(new OnlyOwnDependencyPredicate("employee must only import common, auth", "org.tb.common.", "org.tb.auth.", "org.tb.employee."));

  @ArchTest
  static final ArchRule orderShouldAccessCommonAuthCustomerEmployeeOnly = priority(HIGH).noClasses().that()
      .resideInAPackage("org.tb.auth..")
      .should().dependOnClassesThat(new OnlyOwnDependencyPredicate("order must only import common,auth,customer,employee", "org.tb.common.", "org.tb.auth.", "org.tb.employee.", "org.tb.customer.", "org.tb.order."));

  @ArchTest
  static final ArchRule entitiesDoNotAccessServicesDAOsRepositories = priority(HIGH).noClasses().that()
      .areAnnotatedWith(Entity.class)
      .should().accessClassesThat().areAnnotatedWith(Repository.class)
      .orShould().accessClassesThat().areAnnotatedWith(Service.class)
      .orShould().accessClassesThat().haveSimpleNameEndingWith("DAO");

  @ArchTest
  static final ArchRule accessDataAccessObjectsOnlyInServices = priority(HIGH).noClasses()
        .that().areNotAnnotatedWith(Service.class).and().haveSimpleNameNotEndingWith("DAO")
        .should().accessClassesThat().haveSimpleNameEndingWith("DAO");

  @ArchTest
  static final ArchRule accessRepositoriesOnlyInServicesOrDAOs = priority(HIGH).noClasses()
        .that().areNotAnnotatedWith(Service.class).and().haveSimpleNameNotEndingWith("DAO")
        .should().accessClassesThat().areAnnotatedWith(Repository.class);

  // TODO @ArchTest
  static final ArchRule accessEntitiesOnlyInServicesOrDAOs = priority(HIGH).noClasses()
        .that().areNotAnnotatedWith(Entity.class)
        .and().areNotAnnotatedWith(Repository.class)
        .and().areNotAnnotatedWith(Service.class)
        .and().haveSimpleNameNotEndingWith("DAO")
        .should().accessClassesThat().areAnnotatedWith(Entity.class);

  // TODO @ArchTest
  static final ArchRule noEntityInPublicInterfaceOfService = priority(HIGH).methods()
      .that().areDeclaredInClassesThat().areAnnotatedWith(Service.class).and().areNotPrivate()
      .should(new ArchCondition<JavaMethod>("not have JPA entities in their signature") {
        @Override
        public void check(JavaMethod item, ConditionEvents events) {
          var location = item.getSourceCodeLocation();
          String sourceFileName = location.getSourceFileName();
          int lineNumber = location.getLineNumber() - 1;
          var sourceLink = String.format(" (%s:%d)", sourceFileName, lineNumber);
          item.getAllInvolvedRawTypes().forEach(cls -> {
            if(cls.isAnnotatedWith(Entity.class) || cls.isAssignableFrom(Persistable.class)) {
              events.add(new SimpleConditionEvent(item, false, "Entity " + cls.getFullName() + " in public interface of @Service method " +item.getFullName()  + " > " + sourceLink));
            }
          });
        }
      });

  @ArchTest
  static final ArchRule beFreeOfCycles = slices().matching("org.tb.(*)..").should().beFreeOfCycles();

  private static class OnlyOwnDependencyPredicate extends DescribedPredicate<JavaClass> {

    private final Set<String> notFiringPackagePrefixes;

    public OnlyOwnDependencyPredicate(String description, String... notFiringPackagePrefixes) {
      super(description);
      this.notFiringPackagePrefixes = Set.of(notFiringPackagePrefixes);
    }

    @Override
    public boolean test(JavaClass javaClass) {
      return javaClass.getFullName().startsWith("org.tb.") && !notFiringPackagePrefixes.stream().anyMatch(javaClass.getFullName()::startsWith);
    }
  }

}
