package org.tb.mobile;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SuborderEntry {

    private long id;
    private String label;
    private boolean commentRequired;

}
