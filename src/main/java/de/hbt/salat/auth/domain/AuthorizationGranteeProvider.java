package de.hbt.salat.auth.domain;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Who may be picked as the grantee of a rule (#1074).
 *
 * <p>A grantee is a login, stored by the id of its {@link SalatUser} (#1204): a login name can be changed or anonymized,
 * and whoever got the old one next would inherit the rights. The auth module knows its logins — but not who is hidden: {@code hide} sits on the
 * employee and the auth module may only import {@code common}. So the same way round as with
 * {@link AuthorizationObjectProvider}: the owning module answers, and Spring hands the editor the implementations.
 */
public interface AuthorizationGranteeProvider {

  /**
   * The logins offered in the editor, each with what to show for it. Hidden people are left out — that is what hiding is for. What a rule already
   * carries is added back by the editor, so hiding somebody never makes an existing rule uneditable.
   */
  List<AuthorizationObject> granteeCandidates();

  /**
   * What the editor shows for stored grantee ids, hidden people included (#1204). An id nothing answers to any more is
   * left out of the answer.
   */
  Map<String, AuthorizationObject> describe(Collection<String> granteeIds);

}
