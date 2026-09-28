package de.hbt.salat.common.thymeleaf;

import java.util.LinkedHashSet;
import java.util.Set;
import de.hbt.salat.common.thymeleaf.processor.CheckboxSwitchProcessor;
import de.hbt.salat.common.thymeleaf.processor.FormButtonsProcessor;
import de.hbt.salat.common.thymeleaf.processor.FormProcessor;
import de.hbt.salat.common.thymeleaf.processor.SelectProcessor;
import de.hbt.salat.common.thymeleaf.processor.TextInputProcessor;
import de.hbt.salat.common.thymeleaf.processor.TextareaProcessor;
import org.thymeleaf.dialect.AbstractProcessorDialect;
import org.thymeleaf.processor.IProcessor;

public class SalatDialect extends AbstractProcessorDialect {

    public SalatDialect() {
        super("Salat Dialect", "salat", 900);
    }

    @Override
    public Set<IProcessor> getProcessors(String dialectPrefix) {
        Set<IProcessor> processors = new LinkedHashSet<>();
        processors.add(new FormProcessor(dialectPrefix));
        processors.add(new TextInputProcessor(dialectPrefix));
        processors.add(new TextareaProcessor(dialectPrefix));
        processors.add(new CheckboxSwitchProcessor(dialectPrefix));
        processors.add(new FormButtonsProcessor(dialectPrefix));
        processors.add(new SelectProcessor(dialectPrefix));
        return processors;
    }

}
