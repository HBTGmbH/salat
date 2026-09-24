package org.tb.auth.domain;

import java.util.List;

/**
 * Who may be picked as the grantee of a rule (#1074).
 *
 * <p>A grantee is a login, and the auth module knows its logins — but not who is hidden: {@code hide} sits on the
 * employee and the auth module may only import {@code common}. So the same way round as with
 * {@link AuthorizationObjectProvider}: the owning module answers, and Spring hands the editor the implementations.
 */
public interface AuthorizationGranteeProvider {

  /**
   * The logins offered in the editor. Hidden people are left out — that is what hiding is for. What a rule already
   * carries is added back by the editor, so hiding somebody never makes an existing rule uneditable.
   */
  List<String> granteeCandidates();

}
