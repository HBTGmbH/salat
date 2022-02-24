package org.tb.util;

import java.io.Serializable;
import lombok.Data;

/**
 * Util class to build up a list collection to be used with html:options collection=...
 * in a JSP
 */
@Data
public class OptionItem implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String value;
    private final String label;

    public OptionItem(String v, String l) {
        value = v;
        label = l;
    }

    public OptionItem(int i, String l) {
        value = Integer.toString(i);
        label = l;
    }

}
