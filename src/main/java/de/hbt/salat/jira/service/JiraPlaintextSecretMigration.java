package de.hbt.salat.jira.service;

import static org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes;
import static org.springframework.web.context.request.RequestContextHolder.setRequestAttributes;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.scheduling.SchedulerRequestAttributes;
import de.hbt.salat.jira.persistence.JiraReplicationConfigRepository;

/**
 * Moves the secrets the replications kept in plain text into the secret store, at start (#1432,
 * → ADR-0038 §10): creates the secret, sets {@code secret_id}, clears {@code username} and
 * {@code password}. In Java because Liquibase does not know the key.
 *
 * <p>Repeatable: it picks only replications with a plain text password and without a secret, so a
 * second start finds nothing left to do. Without a key it does nothing and says so — the secrets
 * stay in plain text until a start with a key, and the replications do not run until then.
 *
 * <p>One replication per transaction: one that fails stays as it was and does not hold up the
 * others. The secret module is reached as the job user, like the hourly run reaches it (→ ADR-0006).
 */
@Slf4j
@Service
class JiraPlaintextSecretMigration {

  private final JiraReplicationConfigRepository configRepository;
  private final JiraCredentialStore credentialStore;
  private final ObjectProvider<AuthorizedUser> authorizedUserProvider;
  private final TransactionTemplate transaction;

  JiraPlaintextSecretMigration(JiraReplicationConfigRepository configRepository,
                               JiraCredentialStore credentialStore,
                               ObjectProvider<AuthorizedUser> authorizedUserProvider,
                               PlatformTransactionManager transactionManager) {
    this.configRepository = configRepository;
    this.credentialStore = credentialStore;
    this.authorizedUserProvider = authorizedUserProvider;
    this.transaction = new TransactionTemplate(transactionManager);
  }

  @EventListener(ApplicationReadyEvent.class)
  public void moveToSecretStore() {
    var ids = configRepository.findIdsWithPlaintextSecret();
    if (ids.isEmpty()) {
      return;
    }
    // No request at start, hence no SecurityContext: the request-scoped AuthorizedUser needs a scope
    // of its own and job mode, as in JiraReplicationScheduler.
    setRequestAttributes(new SchedulerRequestAttributes(), true);
    try {
      authorizedUserProvider.getObject().initForJob();
      if (!credentialStore.canStore()) {
        log.warn("{} JIRA replications keep their secret in plain text: no key is configured for the "
            + "secret store, they do not run until a start with a key moves it", ids.size());
        return;
      }
      int moved = 0;
      for (var id : ids) {
        try {
          transaction.executeWithoutResult(status -> moveToSecretStore(id));
          moved++;
        } catch (RuntimeException ex) {
          log.error("Moving the plain text secret of JIRA replication {} failed", id, ex);
        }
      }
      log.info("Moved the plain text secrets of {} of {} JIRA replications into the secret store", moved, ids.size());
    } finally {
      resetRequestAttributes();
    }
  }

  private void moveToSecretStore(long id) {
    var config = configRepository.findById(id).orElseThrow();
    if (config.getSecretId() != null || config.getLegacyPassword() == null) {
      return;
    }
    credentialStore.store(config, config.getAuthMethod(), config.getLegacyUsername(), config.getLegacyPassword());
    log.info("Moved the plain text secret of JIRA replication {} ({}) into secret {}", id, config.getName(),
        config.getSecretId());
  }
}
