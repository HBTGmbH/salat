package org.tb;

import static com.tngtech.archunit.lang.Priority.HIGH;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.priority;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.Priority;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Persistable;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

@Disabled
public class ArchitectureTest {

  private static JavaClasses importedClasses;

  @BeforeAll
  public static void loadClass() {
    importedClasses = new ClassFileImporter()
        .withImportOption(new ImportOption.DoNotIncludeTests())
        .importPackages("org.tb");
  }

  @Test
  public void entitiesDoNotAccessServicesDAOsRepositories() {
    ArchRule rule = priority(HIGH).noClasses().that()
        .areAnnotatedWith(Entity.class)
        .should().accessClassesThat().areAnnotatedWith(Repository.class)
        .orShould().accessClassesThat().areAnnotatedWith(Service.class)
        .orShould().accessClassesThat().haveNameMatching(".*DAO");
    rule.check(importedClasses);
  }

  @Test
  public void accessDataAccessObjectsOnlyInServices() {
    ArchRule rule = priority(HIGH).noClasses()
        .that().areNotAnnotatedWith(Service.class).and().haveNameNotMatching(".*DAO")
        .should().accessClassesThat().haveNameMatching(".*DAO");
    rule.check(importedClasses);
  }

  @Test
  public void accessRepositoriesOnlyInServicesOrDAOs() {
    ArchRule rule = priority(HIGH).noClasses()
        .that().areNotAnnotatedWith(Service.class).and().haveNameNotMatching(".*DAO")
        .should().accessClassesThat().areAnnotatedWith(Repository.class);
    rule.check(importedClasses);
  }

  @Test
  public void accessEntitiesOnlyInServicesOrDAOs() {
    ArchRule rule = priority(HIGH).noClasses()
        .that().areNotAnnotatedWith(Entity.class)
        .and().areNotAnnotatedWith(Repository.class)
        .and().areNotAnnotatedWith(Service.class)
        .and().haveNameNotMatching(".*DAO")
        .should().accessClassesThat().areAnnotatedWith(Entity.class);
    rule.check(importedClasses);
  }

  /*
   * Generics werden nicht unterstützt.
   */
  @Test
  public void noEntityInPublicInterfaceOfService() {



    DescribedPredicate<? super List<JavaClass>> entityTypeParam = new DescribedPredicate<List<JavaClass>>("Entity") {
      @Override
      public boolean test(List<JavaClass> javaClasses) {
        return javaClasses.stream()
            .flatMap(cls -> extractAllTypes(cls).stream())
            .filter(cls -> cls.isAnnotatedWith(Entity.class) || cls.isAssignableFrom(Persistable.class)).findAny()
            .isPresent();
      }

    };
    DescribedPredicate<? super JavaClass> entityReturnType = new DescribedPredicate<JavaClass>("Entity") {
      @Override
      public boolean test(JavaClass javaClass) {
        Set<JavaClass> rawTypes = extractAllTypes(javaClass);
        return rawTypes.stream()
            .filter(cls -> cls.isAnnotatedWith(Entity.class) || cls.isAssignableFrom(Persistable.class)).findAny()
            .isPresent();
      }
    };
    ArchCondition<? super JavaMethod> methodPredicate = new ArchCondition<JavaMethod>("test") {
      @Override
      public void check(JavaMethod item, ConditionEvents events) {
        var location = item.getSourceCodeLocation();
        String sourceFileName = location.getSourceFileName();
        int lineNumber = location.getLineNumber();
        var sourceLink = String.format(" (%s:%d)", sourceFileName, lineNumber);
        item.getAllInvolvedRawTypes().forEach(cls -> {
          if(cls.isAnnotatedWith(Entity.class) || cls.isAssignableFrom(Persistable.class)) {
            events.add(new SimpleConditionEvent(item, false, "Entity " + cls.getFullName() + " in public interface of @Service method " +item.getFullName()  + " > " + sourceLink));
          }
        });
      }
    };
    ArchRule rule = priority(HIGH).methods()
        .that().areDeclaredInClassesThat().areAnnotatedWith(Service.class).and().areNotPrivate()
        .should(methodPredicate);
    rule.check(importedClasses);
  }

  private static Set<JavaClass> extractAllTypes(JavaClass cls) {
    return cls.getAllInvolvedRawTypes();
  }

  @Test
  public void beFreeOfCycles() {
    ArchRule rule = slices().matching("org.tb.(*)..").should().beFreeOfCycles();
    rule.check(importedClasses);
  }

}
