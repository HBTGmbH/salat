package org.tb.common.viewhelper.fragment;

import org.springframework.stereotype.Component;

@Component("masterTableParams")
public class MasterTableParamsFactory {

    /** Add button with default icon (ti-plus). */
    public MasterTableParams withAdd(String addLabel) {
        return MasterTableParams.builder().addLabel(addLabel).build();
    }

    /** Add button with explicit icon. */
    public MasterTableParams withAdd(String addLabel, String addIcon) {
        return MasterTableParams.builder().addLabel(addLabel).addIcon(addIcon).build();
    }
}
