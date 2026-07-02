package org.tb.common.thymeleaf.processor;

import java.util.LinkedHashMap;
import java.util.Map;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.model.AttributeValueQuotes;
import org.thymeleaf.model.IModel;
import org.thymeleaf.model.IModelFactory;
import org.thymeleaf.model.IOpenElementTag;
import org.thymeleaf.processor.element.AbstractElementModelProcessor;
import org.thymeleaf.processor.element.IElementModelStructureHandler;
import org.thymeleaf.templatemode.TemplateMode;

public class SelectProcessor extends AbstractElementModelProcessor {

    public SelectProcessor(String dialectPrefix) {
        super(TemplateMode.HTML, dialectPrefix, "select", true, null, false, 1000);
    }

    @Override
    protected void doProcess(ITemplateContext context, IModel model, IElementModelStructureHandler structureHandler) {
        var openTag = (IOpenElementTag) model.get(0);
        var field = openTag.getAttributeValue("th:field");
        var label = openTag.getAttributeValue("th:label");
        var required = Boolean.parseBoolean(openTag.getAttributeValue("required"));
        var fieldName = extractFieldName(field);

        var mf = context.getModelFactory();
        var newModel = mf.createModel();

        newModel.add(mf.createOpenElementTag("div", "class", "mb-3"));

        Map<String, String> labelAttrs = new LinkedHashMap<>();
        labelAttrs.put("class", required ? "form-label required" : "form-label");
        labelAttrs.put("for", fieldName);
        labelAttrs.put("th:text", label);
        newModel.add(mf.createOpenElementTag("label", labelAttrs, AttributeValueQuotes.DOUBLE, false));
        newModel.add(mf.createCloseElementTag("label"));

        Map<String, String> selectAttrs = new LinkedHashMap<>();
        selectAttrs.put("class", "form-select tomselect");
        selectAttrs.put("th:field", field);
        selectAttrs.put("th:errorclass", "is-invalid");
        newModel.add(mf.createOpenElementTag("select", selectAttrs, AttributeValueQuotes.DOUBLE, false));

        for (int i = 1; i < model.size() - 1; i++) {
            newModel.add(model.get(i));
        }

        newModel.add(mf.createCloseElementTag("select"));

        // auto subtext element picked up by salat.js TomSelect onChange
        Map<String, String> subtextAttrs = new LinkedHashMap<>();
        subtextAttrs.put("id", fieldName + "-subtext");
        subtextAttrs.put("class", "form-text text-muted");
        newModel.add(mf.createOpenElementTag("small", subtextAttrs, AttributeValueQuotes.DOUBLE, false));
        newModel.add(mf.createCloseElementTag("small"));

        Map<String, String> errorAttrs = new LinkedHashMap<>();
        errorAttrs.put("class", "invalid-feedback");
        errorAttrs.put("th:if", "${#fields.hasErrors('" + fieldName + "')}");
        errorAttrs.put("th:errors", field);
        newModel.add(mf.createOpenElementTag("div", errorAttrs, AttributeValueQuotes.DOUBLE, false));
        newModel.add(mf.createCloseElementTag("div"));

        newModel.add(mf.createCloseElementTag("div"));

        model.reset();
        model.addModel(newModel);
    }

    private static String extractFieldName(String field) {
        return field.substring(2, field.length() - 1);
    }
}
