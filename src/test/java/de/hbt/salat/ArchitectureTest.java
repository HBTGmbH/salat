package de.hbt.salat;

import static com.tngtech.archunit.lang.Priority.HIGH;
import static com.tngtech.archunit.lang.Priority.MEDIUM;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.priority;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static com.tngtech.archunit.library.freeze.FreezingArchRule.freeze;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
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
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.data.domain.Persistable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import de.hbt.salat.common.filter.UiStateFilter;
import de.hbt.salat.common.scheduling.SchedulerRequestAttributes;
import de.hbt.salat.common.util.ClockProvider;

@AnalyzeClasses(packages = "de.hbt.salat", importOptions = {DoNotIncludeTests.class, DoNotIncludeJars.class, DoNotIncludeGradleTestFixtures.class})
public class ArchitectureTest {

  @ArchTest
  static final ArchRule daoMethodsMustStartWithGetOrFind = priority(MEDIUM).methods()
        .that().areNotPrivate()
        .and().areDeclaredInClassesThat().haveSimpleNameEndingWith("DAO")
        .should().haveNameStartingWith("get")
        .orShould().haveNameStartingWith("find");

  @ArchTest
  static final ArchRule commonShouldNotAccessOtherSalatPackages = priority(HIGH).noClasses().that()
      .resideInAPackage("de.hbt.salat.common..")
      .should().dependOnClassesThat(new OnlyOwnDependencyPredicate("common must import nothing", "de.hbt.salat.common."));

  @ArchTest
  static final ArchRule authShouldAccessCommonOnly = priority(HIGH).noClasses().that()
      .resideInAPackage("de.hbt.salat.auth..")
      .should().dependOnClassesThat(new OnlyOwnDependencyPredicate("auth must only import common", "de.hbt.salat.common.", "de.hbt.salat.auth."));

  @ArchTest
  static final ArchRule customerShouldAccessCommonAuthOnly = priority(HIGH).noClasses().that()
      .resideInAPackage("de.hbt.salat.customer..")
      .should().dependOnClassesThat(new OnlyOwnDependencyPredicate("customer must only import common, auth", "de.hbt.salat.common.", "de.hbt.salat.auth.", "de.hbt.salat.customer."));

  /**
   * settings and notification are cross-cutting capabilities that a domain module may use directly,
   * not layers below it:
   * <ul>
   *   <li>settings holds the generic per-user preference store. The typed facades on top of it live
   *       in the domain modules on purpose ({@code EmployeePreferences}, {@code DailyPreferences}),
   *       so that settings does not have to know any domain module. The dependency runs in the
   *       intended direction.</li>
   *   <li>notification is used directly by budget, dailyreport and employee alike, so employee is
   *       not an exception here. Routing only this one call through an application event would make
   *       it the odd one out without removing the dependency class. Making notifications
   *       event-driven everywhere is a separate, deliberate decision.</li>
   * </ul>
   */
  @ArchTest
  static final ArchRule employeeShouldAccessCommonAuthSettingsNotificationOnly = priority(HIGH).noClasses().that()
      .resideInAPackage("de.hbt.salat.employee..")
      .should().dependOnClassesThat(new OnlyOwnDependencyPredicate("employee must only import common, auth, settings, notification", "de.hbt.salat.common.", "de.hbt.salat.auth.", "de.hbt.salat.settings.", "de.hbt.salat.notification.", "de.hbt.salat.employee."));

  @ArchTest
  static final ArchRule orderShouldAccessCommonAuthCustomerEmployeeOnly = priority(HIGH).noClasses().that()
      .resideInAPackage("de.hbt.salat.order..")
      .should().dependOnClassesThat(new OnlyOwnDependencyPredicate("order must only import common,auth,customer,employee", "de.hbt.salat.common.", "de.hbt.salat.auth.", "de.hbt.salat.employee.", "de.hbt.salat.customer.", "de.hbt.salat.order."));

  @ArchTest
  static final ArchRule settingsShouldAccessCommonAuthOnly = priority(HIGH).noClasses().that()
      .resideInAPackage("de.hbt.salat.settings..")
      .should().dependOnClassesThat(new OnlyOwnDependencyPredicate(
          "settings must only import common, auth",
          "de.hbt.salat.common.", "de.hbt.salat.auth.", "de.hbt.salat.settings."));

  /**
   * budget sits high in the stack: it evaluates orders, suborders and time reports, prices them with
   * employee data and notifies about overruns. It is therefore allowed to import a lot — what
   * matters is that nothing imports it back, which is what makes the explicit booking-to-budget
   * assignment possible in the first place (see {@link #dailyreportShouldNotAccessBudget}).
   *
   * <p>{@code customer} is in the list because the dashboard filters by customer segment.
   * {@code reporting} used to be, for a single import: the alert scheduler borrowed the report
   * scheduler's nested request-scope helper. That was a scheduling utility at the wrong address, so
   * it moved to {@code common.scheduling} and the edge is gone.
   */
  @ArchTest
  static final ArchRule budgetShouldAccessOnlyItsKnownDependencies = priority(HIGH).noClasses().that()
      .resideInAPackage("de.hbt.salat.budget..")
      .should().dependOnClassesThat(new OnlyOwnDependencyPredicate(
          "budget must only import common, auth, customer, dailyreport, employee, notification, order",
          "de.hbt.salat.common.", "de.hbt.salat.auth.", "de.hbt.salat.customer.", "de.hbt.salat.dailyreport.", "de.hbt.salat.employee.",
          "de.hbt.salat.notification.", "de.hbt.salat.order.", "de.hbt.salat.budget."));

  /**
   * invoice bills what was booked, so it reads orders, suborders, time reports and the customer.
   * Since #915 it may also pick a budget plan instead of a suborder as the billing boundary, which
   * adds {@code invoice -> budget}. That edge is free of cycles because budget does not import
   * invoice — and it reaches budget through a narrow query port that hands out records, not
   * entities.
   */
  @ArchTest
  static final ArchRule invoiceShouldAccessOnlyItsKnownDependencies = priority(HIGH).noClasses().that()
      .resideInAPackage("de.hbt.salat.invoice..")
      .should().dependOnClassesThat(new OnlyOwnDependencyPredicate(
          "invoice must only import common, auth, budget, customer, dailyreport, order",
          "de.hbt.salat.common.", "de.hbt.salat.auth.", "de.hbt.salat.budget.", "de.hbt.salat.customer.", "de.hbt.salat.dailyreport.",
          "de.hbt.salat.order.", "de.hbt.salat.invoice."));

  /**
   * The assumption the whole assignment data model rests on (#908): a booking's budget lives in the
   * budget module, in its own table, referencing the time report only by id. A field on the
   * {@code Timereport} entity would force {@code dailyreport -> budget}, and since
   * {@code budget -> dailyreport} already exists that would be a cycle. {@code beFreeOfCycles} would
   * catch it eventually, but only after the fact and with a message about slices; this says what is
   * actually meant.
   */
  @ArchTest
  static final ArchRule dailyreportShouldNotAccessBudget = priority(HIGH).noClasses().that()
      .resideInAPackage("de.hbt.salat.dailyreport..")
      .should().dependOnClassesThat().resideInAPackage("de.hbt.salat.budget..");

  /**
   * dailyreport owns the bookings and is imported by everything that evaluates them (budget,
   * invoice, statistic), so its own list has to stay narrow.
   *
   * <p>{@code favorites} is the one to watch: the booking screens offer favourites, so dailyreport
   * reaches into that module. It works only because favorites knows nothing about bookings; the
   * reverse import would close a cycle.
   *
   * <p>{@code jira} is the entry that costs an explanation, because jira <em>does</em> evaluate
   * bookings since #1007 — it writes the booked hours back to JIRA as worklogs. It does not read
   * them itself: it asks with a {@code CommandEvent} from {@code de.hbt.salat.jira.command}, and
   * {@code TimereportService} answers it. The event class has to be visible from the listener,
   * hence this edge. What must stay forbidden is the other direction, {@code jira -> dailyreport}
   * — see {@link #jiraShouldAccessCommonAuthOrderSecretOnly}, which does not list dailyreport and is what
   * keeps the pair free of a cycle.
   */
  @ArchTest
  static final ArchRule dailyreportShouldAccessOnlyItsKnownDependencies = priority(HIGH).noClasses().that()
      .resideInAPackage("de.hbt.salat.dailyreport..")
      .should().dependOnClassesThat(new OnlyOwnDependencyPredicate(
          "dailyreport must only import common, auth, customer, employee, favorites, jira, notification, order, settings",
          "de.hbt.salat.common.", "de.hbt.salat.auth.", "de.hbt.salat.customer.", "de.hbt.salat.employee.", "de.hbt.salat.favorites.",
          "de.hbt.salat.jira.", "de.hbt.salat.notification.", "de.hbt.salat.order.", "de.hbt.salat.settings.", "de.hbt.salat.dailyreport."));

  /**
   * statistic aggregates bookings into numbers. It reads dailyreport and order and is imported by
   * nobody — which is what keeps that edge harmless. It carries no authorization of its own because
   * it runs off booking events rather than off requests.
   */
  @ArchTest
  static final ArchRule statisticShouldAccessCommonDailyreportOrderOnly = priority(HIGH).noClasses().that()
      .resideInAPackage("de.hbt.salat.statistic..")
      .should().dependOnClassesThat(new OnlyOwnDependencyPredicate(
          "statistic must only import common, dailyreport, order",
          "de.hbt.salat.common.", "de.hbt.salat.dailyreport.", "de.hbt.salat.order.", "de.hbt.salat.statistic."));

  /**
   * favorites stores which suborder a person books on most. It deliberately does <em>not</em> know
   * dailyreport — the booking screens reach into favorites, not the other way round (see
   * {@link #dailyreportShouldAccessOnlyItsKnownDependencies}). It happens to need nothing from common
   * either; the rule lists it anyway, because needing a utility later is no architectural event.
   *
   * <p>{@code order} because a favourite refers to its employee order (#1369, ADR-0036) and finds
   * its person through it — a column of its own for the person was redundant. order does not import
   * favorites, so the edge closes no cycle.
   *
   * <p>{@code settings} because the person chooses how their favourites are ordered (#1414), and
   * that choice is a preference like the others — settings is the cross-cutting store a domain
   * module uses directly (see {@link #employeeShouldAccessCommonAuthSettingsNotificationOnly}).
   */
  @ArchTest
  static final ArchRule favoritesShouldAccessCommonAuthEmployeeOrderSettingsOnly = priority(HIGH).noClasses().that()
      .resideInAPackage("de.hbt.salat.favorites..")
      .should().dependOnClassesThat(new OnlyOwnDependencyPredicate(
          "favorites must only import common, auth, employee, order, settings",
          "de.hbt.salat.common.", "de.hbt.salat.auth.", "de.hbt.salat.employee.", "de.hbt.salat.order.",
          "de.hbt.salat.settings.", "de.hbt.salat.favorites."));

  /**
   * etl imports data from outside and needs the employee behind a record to attribute it. Nothing
   * imports etl, so it stays a leaf.
   */
  @ArchTest
  static final ArchRule etlShouldAccessCommonAuthEmployeeOnly = priority(HIGH).noClasses().that()
      .resideInAPackage("de.hbt.salat.etl..")
      .should().dependOnClassesThat(new OnlyOwnDependencyPredicate(
          "etl must only import common, auth, employee",
          "de.hbt.salat.common.", "de.hbt.salat.auth.", "de.hbt.salat.employee.", "de.hbt.salat.etl."));

  /**
   * The error page needs to know who is looking at it to decide how much to show, hence auth and
   * employee. It is reached through Spring's error handling, not by an import, so nothing depends on
   * it (→ ADR-0015).
   */
  @ArchTest
  static final ArchRule errorShouldAccessCommonAuthEmployeeOnly = priority(HIGH).noClasses().that()
      .resideInAPackage("de.hbt.salat.error..")
      .should().dependOnClassesThat(new OnlyOwnDependencyPredicate(
          "error must only import common, auth, employee",
          "de.hbt.salat.common.", "de.hbt.salat.auth.", "de.hbt.salat.employee.", "de.hbt.salat.error."));

  /**
   * jira replicates tickets against a remote API, and since #1025 the scope of a replication is a
   * place in the order tree — either a whole customer order or one suborder at any depth. A module
   * that administers that scope without knowing the tree can neither offer it, nor check it, nor
   * follow it upwards for the suggestions of a booking, so the edge to order is what the capability
   * is made of rather than a shortcut.
   *
   * <p>The edge is free of cycles: the transitive hull of order is {common, auth, customer,
   * employee, settings, notification}, none of which imports jira — {@link #beFreeOfCycles} covers
   * that for good. Replications, tickets and worklogs refer to order and suborder as references to
   * master data (#1368, ADR-0036), which this edge allows.
   *
   * <p>{@code dailyreport} is deliberately <em>not</em> in this list, although the worklog sync
   * (#1007) needs the booked minutes. That is what the command event in {@code de.hbt.salat.jira.command}
   * is for: dailyreport imports it and answers, jira never reaches into dailyreport. Since
   * dailyreport now imports jira (see
   * {@link #dailyreportShouldAccessOnlyItsKnownDependencies}), adding dailyreport here would close
   * a cycle.
   *
   * <p>{@code secret} keeps the credentials of the replications encrypted (#1432, ADR-0038); it
   * imports no domain module, so the edge closes no cycle.
   */
  @ArchTest
  static final ArchRule jiraShouldAccessCommonAuthOrderSecretOnly = priority(HIGH).noClasses().that()
      .resideInAPackage("de.hbt.salat.jira..")
      .should().dependOnClassesThat(new OnlyOwnDependencyPredicate(
          "jira must only import common, auth, order, secret",
          "de.hbt.salat.common.", "de.hbt.salat.auth.", "de.hbt.salat.order.", "de.hbt.salat.secret.",
          "de.hbt.salat.jira."));

  /**
   * secret stores secrets encrypted and hands them out by id (#1432, ADR-0038). It knows none of
   * their owners: an owner imports it and remembers the id, and a new integration brings a foreign
   * key, not a new edge out of secret.
   */
  @ArchTest
  static final ArchRule secretShouldAccessCommonAuthOnly = priority(HIGH).noClasses().that()
      .resideInAPackage("de.hbt.salat.secret..")
      .should().dependOnClassesThat(new OnlyOwnDependencyPredicate(
          "secret must only import common, auth",
          "de.hbt.salat.common.", "de.hbt.salat.auth.", "de.hbt.salat.secret."));

  /**
   * beta holds the opt-in betas, counts their use and asks about them (#1447, ADR-0039). The switch
   * is a preference ({@code settings}); a use and a participation belong to a person
   * ({@code employee}). A module whose page carries a beta imports beta — never the other way round,
   * so that a beta costs no edge out of it.
   */
  @ArchTest
  static final ArchRule betaShouldAccessCommonAuthSettingsEmployeeOnly = priority(HIGH).noClasses().that()
      .resideInAPackage("de.hbt.salat.beta..")
      .should().dependOnClassesThat(new OnlyOwnDependencyPredicate(
          "beta must only import common, auth, settings, employee",
          "de.hbt.salat.common.", "de.hbt.salat.auth.", "de.hbt.salat.settings.", "de.hbt.salat.employee.",
          "de.hbt.salat.beta."));

  /**
   * The other half (ADR-0038): the entity of a secret and its repository never leave the module.
   * Their fields mean nothing without the key, and a secret is read decrypted through
   * {@code SecretService} alone — an owner that loaded the row would hold the cipher text and a
   * reason to decrypt it itself.
   */
  @ArchTest
  static final ArchRule nothingOutsideSecretTouchesTheStoredSecret = priority(HIGH).noClasses().that()
      .resideOutsideOfPackage("de.hbt.salat.secret..")
      .should().dependOnClassesThat().resideInAPackage("de.hbt.salat.secret.persistence..")
      .orShould().dependOnClassesThat().haveFullyQualifiedName("de.hbt.salat.secret.domain.Secret");

  /**
   * reporting runs report definitions as SQL and renders the result generically, so it needs no
   * domain module — that is the whole point of the design, and this rule keeps it that way.
   */
  @ArchTest
  static final ArchRule reportingShouldAccessCommonAuthOnly = priority(HIGH).noClasses().that()
      .resideInAPackage("de.hbt.salat.reporting..")
      .should().dependOnClassesThat(new OnlyOwnDependencyPredicate(
          "reporting must only import common, auth",
          "de.hbt.salat.common.", "de.hbt.salat.auth.", "de.hbt.salat.reporting."));

  /**
   * notification sends mails and is used directly by budget, dailyreport and employee alike (see
   * {@link #employeeShouldAccessCommonAuthSettingsNotificationOnly}). It must therefore stay free of
   * every domain module: a notification that knew what it notifies about would turn a cross-cutting
   * capability into the centre of the dependency graph.
   */
  @ArchTest
  static final ArchRule notificationShouldAccessCommonAuthOnly = priority(HIGH).noClasses().that()
      .resideInAPackage("de.hbt.salat.notification..")
      .should().dependOnClassesThat(new OnlyOwnDependencyPredicate(
          "notification must only import common, auth",
          "de.hbt.salat.common.", "de.hbt.salat.auth.", "de.hbt.salat.notification."));

  /**
   * palette runs the object search of the command palette (#1157, ADR-0031) and knows none of the
   * modules it searches: each of them implements {@code PaletteProvider} from {@code common.palette},
   * and Spring hands the implementations over — the pattern of {@code UiStateKeyContributor}. A
   * palette that asked order or employee itself would grow an edge with every kind of hit, and it
   * would take over the decision that has to stay with the modules: who may see a hit, and which of
   * its targets would answer 403.
   */
  @ArchTest
  static final ArchRule paletteShouldAccessCommonAuthOnly = priority(HIGH).noClasses().that()
      .resideInAPackage("de.hbt.salat.palette..")
      .should().dependOnClassesThat(new OnlyOwnDependencyPredicate(
          "palette must only import common, auth",
          "de.hbt.salat.common.", "de.hbt.salat.auth.", "de.hbt.salat.palette."));

  /**
   * The other half of the same design (#1157): the modules contribute through {@code common.palette}
   * alone and never import the collector. Since palette imports no module,
   * {@link #beFreeOfCycles} would stay green with such an edge — but a provider reaching for, say,
   * {@code PaletteGroup} would tie its module to the one module that runs all of them, and the first
   * import in the other direction would close a cycle. This says what is meant before that.
   */
  @ArchTest
  static final ArchRule nothingShouldAccessPalette = priority(HIGH).noClasses().that()
      .resideOutsideOfPackage("de.hbt.salat.palette..")
      .should().dependOnClassesThat().resideInAPackage("de.hbt.salat.palette..");

  // settingseditor is an aggregator: it may import from any module.
  // The beFreeOfCycles rule below ensures no other module can accidentally depend back on it.

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

  /**
   * Eine JPA-Entity überquert keine Modulgrenze über die Schnittstelle eines Services (ADR-0021, #1244).
   * Wer aus einem anderen Modul eine nicht-private Methode eines {@code @Service} aufruft, bekommt und
   * übergibt Records oder DTOs aus Werten und Ids, keine Entity — weder als Parameter noch als
   * Rückgabe, auch nicht als Typargument einer Liste oder eines {@code Optional}. Innerhalb des Moduls
   * darf die Entity bleiben, wo sie ist: dass ein Service sie an den eigenen Controller gibt, verbietet
   * ADR-0021 nicht.
   *
   * <p>Der Bestand bei Einführung der Regel ist eingefroren ({@code src/test/resources/archunit_store}):
   * 219 Aufrufe auf 56 Methoden, die meisten aus {@code dailyreport} in {@code employee} und
   * {@code order}. Dazu kamen 39 Aufrufe aus den Umbauten #1204, #1205 und #1212, die parallel zur
   * Regel fertig wurden; 24 alte sind dabei entfallen, zusammen 234. ADR-0021 verlangt keinen Umbau bestehender Lesepfade. Ein neuer Aufruf dieser Art
   * lässt den Build rot werden; ein behobener verschwindet von selbst aus der Liste.
   *
   * <p>Was die Regel nicht sieht: den Inhalt eines {@code @Query}. Ein Join in ein Modul, das nicht
   * importiert werden darf, bleibt Sache des Reviews.
   */
  @ArchTest
  static final ArchRule noEntityCrossesAModuleBoundaryThroughAService = freeze(priority(HIGH).classes()
      .should(new ArchCondition<JavaClass>("call no @Service method of another module that has a JPA entity in its signature") {
        @Override
        public void check(JavaClass caller, ConditionEvents events) {
          for (JavaMethodCall call : caller.getMethodCallsFromSelf()) {
            var owner = call.getTargetOwner();
            if (!owner.isAnnotatedWith(Service.class) || moduleOf(owner).equals(moduleOf(caller))) {
              continue;
            }
            call.getTarget().resolveMember()
                .filter(method -> !method.getModifiers().contains(JavaModifier.PRIVATE))
                .ifPresent(method -> method.getAllInvolvedRawTypes().stream()
                    .filter(ArchitectureTest::isEntity)
                    .map(JavaClass::getName)
                    .distinct()
                    .forEach(entity -> events.add(SimpleConditionEvent.violated(call,
                        "%s calls %s, which carries entity %s from module %s into module %s %s".formatted(
                            call.getOrigin().getFullName(), method.getFullName(), entity, moduleOf(owner),
                            moduleOf(caller), call.getSourceCodeLocation())))));
          }
        }
      }));

  /**
   * Ein Repository fragt nach seinem eigenen Aggregat (#1369): keine Methode eines Repositorys gibt
   * eine Entity eines anderen Moduls zurück, auch nicht als Typargument eines {@code Optional} oder
   * einer Liste. Ein Join über die Referenz in ein anderes Modul bleibt erlaubt (ADR-0021, ADR-0036),
   * solange die Abfrage bei der eigenen Entity beginnt oder Werte liefert. Wer eine Frage allein über
   * fremde Stammdaten hat — gibt es sie, wem gehören sie —, fragt das besitzende Modul; braucht er
   * die Entity als Referenz, holt er sie per {@code EntityManager.getReference}.
   *
   * <p>Was die Regel nicht sieht: die Wurzel eines {@code @Query}, das nur einen Wert liefert. Das
   * bleibt Sache des Reviews.
   */
  @ArchTest
  static final ArchRule noRepositoryReturnsAnEntityOfAnotherModule = priority(HIGH).classes()
      .that().areAssignableTo(org.springframework.data.repository.Repository.class)
      .or().areAnnotatedWith(Repository.class)
      .should(new ArchCondition<JavaClass>("return no JPA entity of another module") {
        @Override
        public void check(JavaClass repository, ConditionEvents events) {
          for (JavaMethod method : repository.getMethods()) {
            method.getReturnType().getAllInvolvedRawTypes().stream()
                .filter(ArchitectureTest::isEntity)
                .filter(entity -> !moduleOf(entity).equals(moduleOf(repository)))
                .distinct()
                .forEach(entity -> events.add(SimpleConditionEvent.violated(method,
                    "%s returns entity %s of module %s from module %s".formatted(
                        method.getFullName(), entity.getName(), moduleOf(entity), moduleOf(repository)))));
          }
        }
      });

  /** The module of a class: the first package below {@code de.hbt.salat}. */
  private static String moduleOf(JavaClass javaClass) {
    var below = javaClass.getPackageName().replaceFirst("^de\\.hbt\\.salat\\.?", "");
    var dot = below.indexOf('.');
    return dot < 0 ? below : below.substring(0, dot);
  }

  private static boolean isEntity(JavaClass javaClass) {
    return javaClass.isAnnotatedWith(Entity.class) || javaClass.isAssignableTo(Persistable.class);
  }

  /**
   * Eine Berechtigung wird als {@code @Authorized(requires…)} verlangt, nicht als
   * {@code @PreAuthorize("hasRole(…)")} (#926). Beide prüfen vor derselben Methode, aber sie fragen
   * nicht dasselbe: {@code hasRole} liest die Rollen aus dem {@code SecurityContext},
   * {@code @Authorized} fragt {@link de.hbt.salat.auth.domain.AuthorizedUser} — und der kennt die
   * übernommene Anmeldung. Solange beide Formen nebeneinander standen, hing es von der Annotation
   * ab, wen eine Seite während einer Impersonation sieht, und Controller und Service konnten sich
   * widersprechen.
   *
   * <p>Ein zweiter Grund steckt in der Antwort: die Ausnahme des Aspekts beantwortet
   * {@code AuthorizationExceptionHandler} mit {@code 403}, die von Spring Security die
   * Sicherheitskette. Eine Mischung aus beidem hat zwei Wege für dieselbe Aussage.
   */
  @ArchTest
  static final ArchRule noClassIsGuardedWithPreAuthorize = priority(HIGH).noClasses()
      .should().beAnnotatedWith(PreAuthorize.class)
      .because("an authorization requirement is spelled @Authorized(requires…) (#926)");

  @ArchTest
  static final ArchRule noMethodIsGuardedWithPreAuthorize = priority(HIGH).noMethods()
      .should().beAnnotatedWith(PreAuthorize.class)
      .because("an authorization requirement is spelled @Authorized(requires…) (#926)");

  @ArchTest
  static final ArchRule beFreeOfCycles = slices().matching("de.hbt.salat.(*)..").should().beFreeOfCycles();

  private static final Set<String> JAVA_TIME_NOW_OWNERS = Set.of(
      "java.time.LocalDate", "java.time.LocalDateTime", "java.time.LocalTime",
      "java.time.Instant", "java.time.ZonedDateTime", "java.time.OffsetDateTime",
      "java.time.OffsetTime", "java.time.Year", "java.time.YearMonth", "java.time.MonthDay");

  // Matches the no-arg java.time now() calls (e.g. LocalDate.now(), LocalDateTime.now()) that read
  // the ambient system clock. The explicit now(Clock) overload used by ClockProvider takes a
  // parameter and is therefore not matched.
  private static final DescribedPredicate<JavaMethodCall> AMBIENT_NOW =
      new DescribedPredicate<>("read the current time from the ambient system clock via java.time now()") {
        @Override
        public boolean test(JavaMethodCall call) {
          var target = call.getTarget();
          return "now".equals(target.getName())
              && target.getRawParameterTypes().isEmpty()
              && JAVA_TIME_NOW_OWNERS.contains(target.getOwner().getFullName());
        }
      };

  @ArchTest
  static final ArchRule readCurrentTimeOnlyViaClockProvider = priority(HIGH).noClasses()
      .that().doNotBelongToAnyOf(ClockProvider.class)
      .and().haveSimpleNameNotEndingWith("OpenApiConfiguration") // build-timestamp fallback only
      .should().callMethodWhere(AMBIENT_NOW)
      .because("the current date/time must be read via ClockProvider (or DateUtils.today() / "
          + "DateTimeUtils.now()) so it can be fixed deterministically in tests");

  @ArchTest
  static final ArchRule useDeterministicRandomness = priority(HIGH).noClasses()
      // carve-outs for intentional production randomness: SchedulerRequestAttributes mints the
      // session id of the stand-in request scope a scheduled job runs in (it moved out of
      // ScheduledReportJobScheduler in #918, and the carve-out moved with it);
      // UiStateFilter mints a random fallback HMAC signing key when none is configured (the key
      // must stay unpredictable, so it cannot be made deterministic).
      .that().doNotBelongToAnyOf(SchedulerRequestAttributes.class, UiStateFilter.class)
      .should().callMethod(Math.class, "random")
      .orShould().callMethod(UUID.class, "randomUUID")
      .orShould().callMethod(ThreadLocalRandom.class, "current")
      .orShould().callConstructor(Random.class)
      .because("non-deterministic randomness (Math.random(), new Random() without a seed, "
          + "UUID.randomUUID(), ThreadLocalRandom) makes behaviour and tests non-reproducible; "
          + "seed a Random or inject the value instead");

  private static class OnlyOwnDependencyPredicate extends DescribedPredicate<JavaClass> {

    private final Set<String> notFiringPackagePrefixes;

    public OnlyOwnDependencyPredicate(String description, String... notFiringPackagePrefixes) {
      super(description);
      this.notFiringPackagePrefixes = Set.of(notFiringPackagePrefixes);
    }

    @Override
    public boolean test(JavaClass javaClass) {
      return javaClass.getFullName().startsWith("de.hbt.salat.") && !notFiringPackagePrefixes.stream().anyMatch(javaClass.getFullName()::startsWith);
    }
  }

}
