package de.hbt.salat.graphqlspike.probe;

import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.employee.domain.AuthorizedEmployee;
import de.hbt.salat.employee.service.EmployeeService;
import de.hbt.salat.graphqlspike.config.GraphqlSpikeConfiguration;

/**
 * Makes the runtime context of a resolver observable (concept 6.4): which thread it runs on, whether the
 * request-scoped {@link AuthorizedUser} and {@link AuthorizedEmployee} answer there, and whether Open-in-View has
 * an entity manager bound to it — directly, through a {@link Callable}, on a foreign thread and in a batch loader.
 * Two more fields show {@code @Authorized} at work at the resolver and at the service. Instrumentation of the
 * spike, not carried over.
 */
@Controller
@Profile(GraphqlSpikeConfiguration.PROFILE)
@Authorized
@RequiredArgsConstructor
class ProbeResolver {

  private final AuthorizedUser authorizedUser;
  private final AuthorizedEmployee authorizedEmployee;
  private final EmployeeService employeeService;

  @QueryMapping
  @Authorized
  Probe probe() {
    return new Probe();
  }

  @SchemaMapping
  @Authorized
  ProbeResult direct(Probe probe) {
    return capture();
  }

  @SchemaMapping
  @Authorized
  Callable<ProbeResult> callable(Probe probe) {
    return this::capture;
  }

  @SchemaMapping
  @Authorized
  CompletableFuture<ProbeResult> foreignThread(Probe probe) {
    return CompletableFuture.supplyAsync(this::capture);
  }

  @SchemaMapping
  @Authorized
  List<ProbeItem> batched(Probe probe) {
    return IntStream.range(0, 3).mapToObj(ProbeItem::new).toList();
  }

  @BatchMapping(typeName = "ProbeItem", field = "result")
  Map<ProbeItem, ProbeResult> batchedResult(List<ProbeItem> items) {
    ProbeResult result = capture();
    return items.stream().collect(Collectors.toMap(Function.identity(), item -> result));
  }

  @SchemaMapping
  @Authorized(requiresManager = true)
  boolean resolverRequiresManager(Probe probe) {
    return true;
  }

  @SchemaMapping
  @Authorized
  boolean serviceRequiresManager(Probe probe) {
    employeeService.getLoginsOfEmployees(List.of());
    return true;
  }

  private ProbeResult capture() {
    var errors = new ArrayList<String>();
    String userSign = read(() -> authorizedUser.getLoginSign(), "AuthorizedUser", errors);
    String employeeSign = read(() -> authorizedEmployee.getSign(), "AuthorizedEmployee", errors);
    boolean entityManagerBound = TransactionSynchronizationManager.getResourceMap().keySet().stream()
        .anyMatch(EntityManagerFactory.class::isInstance);
    return new ProbeResult(Thread.currentThread().getName(), userSign, employeeSign, entityManagerBound,
        errors.isEmpty() ? null : String.join("; ", errors));
  }

  private static String read(Callable<String> value, String name, List<String> errors) {
    try {
      return value.call();
    } catch (Exception ex) {
      errors.add(name + ": " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
      return null;
    }
  }

  public record Probe() {
  }

  public record ProbeItem(int index) {
  }

  public record ProbeResult(String thread, String authorizedUserSign, String authorizedEmployeeSign,
      boolean entityManagerBound, String error) {
  }
}
