package org.tb.common.thymeleaf.processor;

import java.util.LinkedHashMap;
import java.util.Map;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.model.AttributeValueQuotes;
import org.thymeleaf.model.IModel;
import org.thymeleaf.model.IModelFactory;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.processor.element.IElementTagStructureHandler;

public class FormButtonsProcessor extends AbstractSalatProcessor {

    public FormButtonsProcessor(String dialectPrefix) {
        super(dialectPrefix, "formButtons");
    }

    @Override
    protected void doProcess(ITemplateContext context, IProcessableElementTag tag, IElementTagStructureHandler structureHandler) {
        var saveLabel = tag.getAttributeValue("th:saveLabel");
        var cancelHref = tag.getAttributeValue("th:cancelHref");

        var mf = context.getModelFactory();
        var newModel = buildModel(mf, saveLabel, cancelHref);

        structureHandler.replaceWith(newModel, true);
    }

    private IModel buildModel(IModelFactory mf, String saveLabel, String cancelHref) {
        var model = mf.createModel();

        model.add(mf.createOpenElementTag("div", "class", "d-flex gap-2"));

        Map<String, String> buttonAttrs = new LinkedHashMap<>();
        buttonAttrs.put("type", "submit");
        buttonAttrs.put("class", "btn btn-primary");
        model.add(mf.createOpenElementTag("button", buttonAttrs, AttributeValueQuotes.DOUBLE, false));

        var iconAttrs = Map.of("class", "icon ti ti-device-floppy");
        model.add(mf.createOpenElementTag("i", iconAttrs, AttributeValueQuotes.DOUBLE, false));
        model.add(mf.createCloseElementTag("i"));

        model.add(mf.createOpenElementTag("span", Map.of("th:text", saveLabel), AttributeValueQuotes.DOUBLE, false));
        model.add(mf.createCloseElementTag("span"));

        model.add(mf.createCloseElementTag("button"));

        Map<String, String> cancelAttrs = new LinkedHashMap<>();
        cancelAttrs.put("th:href", cancelHref != null ? cancelHref : "#");
        cancelAttrs.put("class", "btn btn-secondary");
        model.add(mf.createOpenElementTag("a", cancelAttrs, AttributeValueQuotes.DOUBLE, false));
        model.add(mf.createText("Cancel"));
        model.add(mf.createCloseElementTag("a"));

        model.add(mf.createCloseElementTag("div"));

        return model;
    }
}
