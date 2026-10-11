package de.hbt.salat.jira.configuration;

import static de.hbt.salat.common.exception.ErrorCode.JI_DISABLED;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import de.hbt.salat.common.SalatProperties;
import de.hbt.salat.common.exception.BusinessRuleException;

/**
 * Whether this environment talks to JIRA at all ({@code salat.jira.enabled}, #1458).
 *
 * <p>An environment with a copy of the production data, a QA stage say, must not reach a real JIRA:
 * the worklog sync <b>writes</b> there, and with Jira Cloud every renewal rotates the refresh token —
 * a run from the copy would invalidate the connection of the source. Switched off, there is no
 * replication, no worklog sync, no field catalogue and no OAuth flow or token renewal.
 *
 * <p>Asked by the services that reach JIRA, not only by the controllers, so that a future way in is
 * covered as well. The scheduled run asks beforehand and drops out quietly. The cleanup of the run
 * history does not ask: it reads no foreign system and hardly loads the database.
 */
@Slf4j
@Component
public class JiraSwitch {

  private final SalatProperties properties;

  public JiraSwitch(SalatProperties properties) {
    this.properties = properties;
    if (!isEnabled()) {
      log.info("The JIRA connection is switched off in this environment (salat.jira.enabled=false)");
    }
  }

  public boolean isEnabled() {
    return properties.getJira().isEnabled();
  }

  /** @throws BusinessRuleException {@code JI-0054} when the JIRA connection is switched off here */
  public void requireEnabled() {
    if (!isEnabled()) {
      throw new BusinessRuleException(JI_DISABLED);
    }
  }

}
