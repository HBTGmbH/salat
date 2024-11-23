package org.tb.dailyreport.viewhelper;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class WelcomeViewHelper implements Serializable {

  private final boolean displayEmployeeInfo;

}
