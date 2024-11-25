package org.tb.common.util;

import static org.springframework.transaction.interceptor.TransactionAspectSupport.currentTransactionStatus;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class TransactionUtils {

  public static void markForRollback() {
    try {
      currentTransactionStatus().setRollbackOnly();
    } catch(Exception e) {
      log.warn("Could not roll back transaction", e);
    }
  }

}
