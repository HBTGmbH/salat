package org.tb.restful.suborders;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class SuborderData {

    private final long id;
    private final String label;
    private final boolean commentRequired;

}
