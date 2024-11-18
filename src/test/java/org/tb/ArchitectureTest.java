package org.tb;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
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
  public void accessDataAccessObjectsOnlyInServices() {
    ArchRule rule = noClasses()
        .that().areNotAnnotatedWith(Service.class).and().haveNameNotMatching(".*DAO")
        .should().accessClassesThat().haveNameMatching(".*DAO");
    rule.check(importedClasses);
  }

  @Test
  public void accessRepositoriesOnlyInServicesOrDAOs() {
    ArchRule rule = noClasses()
        .that().areNotAnnotatedWith(Service.class).and().haveNameNotMatching(".*DAO")
        .should().accessClassesThat().areAnnotatedWith(Repository.class);
    rule.check(importedClasses);
  }

  @Test
  public void accessEntitiesOnlyInServicesOrDAOs() {
    ArchRule rule = noClasses()
        .that().areNotAnnotatedWith(Entity.class).and().areNotAnnotatedWith(Service.class).and().haveNameNotMatching(".*DAO")
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
            .flatMap(cls -> cls.getAllInvolvedRawTypes().stream())
            .filter(cls -> cls.isAnnotatedWith(Entity.class) || cls.isAssignableFrom(Persistable.class)).findAny()
            .isPresent();
      }
    };
    DescribedPredicate<? super JavaClass> entityReturnType = new DescribedPredicate<JavaClass>("Entity") {
      @Override
      public boolean test(JavaClass javaClass) {
        Set<JavaClass> rawTypes = javaClass.getAllInvolvedRawTypes();
        return rawTypes.stream()
            .filter(cls -> cls.isAnnotatedWith(Entity.class) || cls.isAssignableFrom(Persistable.class)).findAny()
            .isPresent();
      }
    };
    ArchRule rule = methods()
        .that().areDeclaredInClassesThat().areAnnotatedWith(Service.class).and().areNotPrivate()
        .should().notHaveRawParameterTypes(entityTypeParam).andShould().notHaveRawReturnType(entityReturnType);
    rule.check(importedClasses);
  }

  @Test
  public void beFreeOfCycles() {
    ArchRule rule = slices().matching("org.tb.(*)..").should().beFreeOfCycles();
    rule.check(importedClasses);
  }

}
