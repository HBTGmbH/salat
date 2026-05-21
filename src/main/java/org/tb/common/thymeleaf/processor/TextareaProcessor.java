package org.tb.common.thymeleaf.processor;

import java.util.LinkedHashMap;
import java.util.Map;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.model.AttributeValueQuotes;
import org.thymeleaf.model.IModel;
import org.thymeleaf.model.IModelFactory;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.processor.element.IElementTagStructureHandler;

public class TextareaProcessor extends AbstractSalatProcessor {

    public TextareaProcessor(String dialectPrefix) {
        super(dialectPrefix, "textarea");
    }

    @Override
    protected void doProcess(ITemplateContext context, IProcessableElementTag tag, IElementTagStructureHandler structureHandler) {
        var field = tag.getAttributeValue("th:field");
        var label = tag.getAttributeValue("th:label");
        var required = Boolean.parseBoolean(tag.getAttributeValue("required"));
        var rowsRaw = tag.getAttributeValue("rows");
        var rows = (rowsRaw != null && !rowsRaw.isBlank()) ? rowsRaw : "3";
        var monospace = Boolean.parseBoolean(tag.getAttributeValue("monospace"));
        var helpText = tag.getAttributeValue("th:helpText");

        var mf = context.getModelFactory();
        var newModel = mf.createModel();

        newModel.add(mf.createOpenElementTag("div", "class", "mb-3"));

        Map<String, String> labelAttrs = new LinkedHashMap<>();
        labelAttrs.put("class", required ? "form-label required" : "form-label");
        labelAttrs.put("for", extractFieldName(field));
        labelAttrs.put("th:text", label);
        newModel.add(mf.createOpenElementTag("label", labelAttrs, AttributeValueQuotes.DOUBLE, false));
        newModel.add(mf.createCloseElementTag("label"));

        Map<String, String> textareaAttrs = new LinkedHashMap<>();
        textareaAttrs.put("class", "form-control");
        textareaAttrs.put("th:field", field);
        textareaAttrs.put("th:errorclass", "is-invalid");
        textareaAttrs.put("rows", rows);
        if (required) {
            textareaAttrs.put("required", "required");
        }
        if (monospace) {
            textareaAttrs.put("style", "font-family: monospace;");
        }
        newModel.add(mf.createOpenElementTag("textarea", textareaAttrs, AttributeValueQuotes.DOUBLE, false));
        newModel.add(mf.createCloseElementTag("textarea"));

        if (helpText != null && !helpText.isBlank()) {
            var attr = Map.of("class", "form-text", "th:text", helpText);
            newModel.add(mf.createOpenElementTag("div", attr, AttributeValueQuotes.DOUBLE, false));
            newModel.add(mf.createCloseElementTag("div"));
        }

        addErrorDiv(mf, newModel, field);

        newModel.add(mf.createCloseElementTag("div"));

        structureHandler.replaceWith(newModel, true);
    }

    private void addErrorDiv(IModelFactory mf, IModel model, String field) {
        Map<String, String> errorAttrs = new LinkedHashMap<>();
        errorAttrs.put("class", "invalid-feedback");
        errorAttrs.put("th:if", "${#fields.hasErrors('" + extractFieldName(field) + "')}");
        errorAttrs.put("th:errors", field);
        model.add(mf.createOpenElementTag("div", errorAttrs, AttributeValueQuotes.DOUBLE, false));
        model.add(mf.createCloseElementTag("div"));
    }
}
