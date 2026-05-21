package org.tb.common.thymeleaf.processor;

import java.util.LinkedHashMap;
import java.util.Map;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.model.AttributeValueQuotes;
import org.thymeleaf.model.IModel;
import org.thymeleaf.model.IModelFactory;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.processor.element.IElementTagStructureHandler;

public class CheckboxSwitchProcessor extends AbstractSalatProcessor {

    public CheckboxSwitchProcessor(String dialectPrefix) {
        super(dialectPrefix, "checkboxSwitch");
    }

    @Override
    protected void doProcess(ITemplateContext context, IProcessableElementTag tag, IElementTagStructureHandler structureHandler) {
        var field = tag.getAttributeValue("th:field");
        var label = tag.getAttributeValue("th:label");

        var mf = context.getModelFactory();
        var newModel = mf.createModel();

        newModel.add(mf.createOpenElementTag("div", "class", "mb-3"));
        newModel.add(mf.createOpenElementTag("label", "class", "form-check form-switch"));

        Map<String, String> inputAttrs = new LinkedHashMap<>();
        inputAttrs.put("class", "form-check-input");
        inputAttrs.put("type", "checkbox");
        inputAttrs.put("th:field", field);
        newModel.add(mf.createStandaloneElementTag("input", inputAttrs, AttributeValueQuotes.DOUBLE, false, true));

        var labelAttr = Map.of("class", "form-check-label", "th:text", label);
        newModel.add(mf.createOpenElementTag("span", labelAttr, AttributeValueQuotes.DOUBLE, false));
        newModel.add(mf.createCloseElementTag("span"));

        newModel.add(mf.createCloseElementTag("label"));

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
