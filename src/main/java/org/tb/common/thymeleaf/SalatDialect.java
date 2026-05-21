package org.tb.common.thymeleaf;

import java.util.LinkedHashSet;
import java.util.Set;
import org.tb.common.thymeleaf.processor.CheckboxSwitchProcessor;
import org.tb.common.thymeleaf.processor.FormButtonsProcessor;
import org.tb.common.thymeleaf.processor.TextInputProcessor;
import org.tb.common.thymeleaf.processor.TextareaProcessor;
import org.thymeleaf.dialect.AbstractProcessorDialect;
import org.thymeleaf.dialect.IPostProcessorDialect;
import org.thymeleaf.dialect.IPreProcessorDialect;
import org.thymeleaf.postprocessor.IPostProcessor;
import org.thymeleaf.preprocessor.IPreProcessor;
import org.thymeleaf.processor.IProcessor;
import org.thymeleaf.standard.StandardDialect;

public class SalatDialect extends AbstractProcessorDialect {

    public SalatDialect() {
        super("Salat Dialect", "salat", 900);
    }

    @Override
    public Set<IProcessor> getProcessors(String dialectPrefix) {
        Set<IProcessor> processors = new LinkedHashSet<>();
        processors.add(new TextInputProcessor(dialectPrefix));
        processors.add(new TextareaProcessor(dialectPrefix));
        processors.add(new CheckboxSwitchProcessor(dialectPrefix));
        processors.add(new FormButtonsProcessor(dialectPrefix));
        return processors;
    }

}
