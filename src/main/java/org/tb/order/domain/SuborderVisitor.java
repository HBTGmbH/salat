package org.tb.order.domain;

import org.tb.order.domain.Suborder;

public interface SuborderVisitor {

    void visitSuborder(Suborder suborder);

}
