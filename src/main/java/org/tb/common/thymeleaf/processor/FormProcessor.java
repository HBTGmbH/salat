package org.tb.common.thymeleaf.processor;

import java.util.LinkedHashMap;
import java.util.Map;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.model.AttributeValueQuotes;
import org.thymeleaf.model.ICloseElementTag;
import org.thymeleaf.model.IModel;
import org.thymeleaf.model.IModelFactory;
import org.thymeleaf.model.IOpenElementTag;
import org.thymeleaf.processor.element.AbstractElementModelProcessor;
import org.thymeleaf.processor.element.IElementModelStructureHandler;
import org.thymeleaf.templatemode.TemplateMode;

public class FormProcessor extends AbstractElementModelProcessor {

    public FormProcessor(String dialectPrefix) {
        super(TemplateMode.HTML, dialectPrefix, "form", true, null, false, 999);
    }

    @Override
    protected void doProcess(ITemplateContext context, IModel model, IElementModelStructureHandler structureHandler) {
        var formTag = (IOpenElementTag) model.get(0);
        var action = formTag.getAttributeValue("th:action");
        var object = formTag.getAttributeValue("th:object");
        var idProperty = formTag.getAttributeValue("th:id-property");

        var prefix = getDialectPrefix();
        var mf = context.getModelFactory();
        var inputsBody = extractSection(mf, model, prefix + ":inputs");
        var buttonsBody = extractSection(mf, model, prefix + ":buttons");

        var newModel = mf.createModel();

        Map<String, String> formAttrs = new LinkedHashMap<>();
        if (action != null) formAttrs.put("th:action", action);
        if (object != null) formAttrs.put("th:object", object);
        formAttrs.put("method", "post");
        formAttrs.put("class", "card");
        newModel.add(mf.createOpenElementTag("form", formAttrs, AttributeValueQuotes.DOUBLE, false));

        newModel.add(mf.createOpenElementTag("div", "class", "card-body"));
        if (idProperty != null && !idProperty.isBlank()) {
            Map<String, String> hiddenAttrs = new LinkedHashMap<>();
            hiddenAttrs.put("type", "hidden");
            hiddenAttrs.put("th:field", idProperty);
            newModel.add(mf.createStandaloneElementTag("input", hiddenAttrs, AttributeValueQuotes.DOUBLE, false, true));
        }
        newModel.addModel(inputsBody);
        newModel.add(mf.createCloseElementTag("div"));

        newModel.add(mf.createOpenElementTag("div", "class", "card-footer"));
        newModel.addModel(buttonsBody);
        newModel.add(mf.createCloseElementTag("div"));

        newModel.add(mf.createCloseElementTag("form"));

        model.reset();
        model.addModel(newModel);
    }

    private static IModel extractSection(IModelFactory mf, IModel model, String qualifiedName) {
        var result = mf.createModel();
        var inSection = false;
        var depth = 0;

        for (int i = 1; i < model.size() - 1; i++) {
            var event = model.get(i);
            if (event instanceof IOpenElementTag tag) {
                if (!inSection && qualifiedName.equals(tag.getElementCompleteName())) {
                    inSection = true;
                    depth = 1;
                } else if (inSection) {
                    if (qualifiedName.equals(tag.getElementCompleteName())) {
                        depth++;
                    }
                    result.add(event);
                }
            } else if (event instanceof ICloseElementTag tag) {
                if (inSection) {
                    if (qualifiedName.equals(tag.getElementCompleteName())) {
                        depth--;
                        if (depth == 0) {
                            inSection = false;
                        } else {
                            result.add(event);
                        }
                    } else {
                        result.add(event);
                    }
                }
            } else if (inSection) {
                result.add(event);
            }
        }
        return result;
    }
}
