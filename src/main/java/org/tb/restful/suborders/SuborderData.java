package org.tb.restful.suborders;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class SuborderData {

    private long id;
    private String label;
    private boolean commentRequired;

}
