package org.tb.common.thymeleaf.processor;

import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.processor.element.AbstractElementTagProcessor;
import org.thymeleaf.processor.element.IElementTagStructureHandler;
import org.thymeleaf.standard.expression.IStandardExpression;
import org.thymeleaf.standard.expression.IStandardExpressionParser;
import org.thymeleaf.standard.expression.StandardExpressions;
import org.thymeleaf.templatemode.TemplateMode;
import org.unbescape.html.HtmlEscape;

public abstract class AbstractSalatProcessor extends AbstractElementTagProcessor {

    protected AbstractSalatProcessor(String dialectPrefix, String elementName) {
        super(TemplateMode.HTML, dialectPrefix, elementName, true, null, false, 1000);
    }

    @Override
    protected abstract void doProcess(ITemplateContext context, IProcessableElementTag tag, IElementTagStructureHandler structureHandler);

    protected String resolveExpression(ITemplateContext context, String expr) {
        if (expr == null || expr.isBlank()) {
            return null;
        }
        String trimmed = expr.trim();
        if (trimmed.contains("#{") || trimmed.contains("${") || trimmed.contains("@{") || trimmed.contains("~{")) {
            IStandardExpressionParser parser = StandardExpressions.getExpressionParser(context.getConfiguration());
            IStandardExpression expression = parser.parseExpression(context, trimmed);
            Object result = expression.execute(context);
            return result != null ? result.toString() : "";
        }
        return trimmed;
    }

    protected String extractFieldName(String field) {
        // extract fieldName from *{fieldName}
        return field.substring(2, field.length() - 1);
    }
}
