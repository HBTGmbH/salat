package org.tb.common.thymeleaf;

import java.util.Collection;
import org.springframework.stereotype.Component;

@Component("formField")
public class FormFieldParamsFactory {

    // ── TextInput ──────────────────────────────────────────────────────────────

    public TextInputParams text(String field, int maxlength) {
        return TextInputParams.builder().field(field).maxlength(maxlength).build();
    }

    public TextInputParams text(String field, int maxlength, String helpText) {
        return TextInputParams.builder().field(field).maxlength(maxlength).helpText(helpText).build();
    }

    public TextInputParams required(String field, int maxlength) {
        return TextInputParams.builder().field(field).required(true).maxlength(maxlength).build();
    }

    public TextInputParams required(String field, int maxlength, String helpText) {
        return TextInputParams.builder().field(field).required(true).maxlength(maxlength).helpText(helpText).build();
    }

    // ── Textarea ───────────────────────────────────────────────────────────────

    public TextareaParams textarea(String field, int rows) {
        return TextareaParams.builder().field(field).rows(rows).build();
    }

    public TextareaParams requiredTextarea(String field, int rows) {
        return TextareaParams.builder().field(field).required(true).rows(rows).build();
    }

    public TextareaParams textareaWithHelp(String field, int rows, String helpText) {
        return TextareaParams.builder().field(field).rows(rows).helpText(helpText).build();
    }

    public TextareaParams requiredTextareaWithHelp(String field, int rows, String helpText) {
        return TextareaParams.builder().field(field).required(true).rows(rows).helpText(helpText).build();
    }

    /** Monospace (code) textarea, no required, no help. */
    public TextareaParams code(String field, int rows) {
        return TextareaParams.builder().field(field).rows(rows).monospace(true).build();
    }

    public TextareaParams requiredCode(String field, int rows) {
        return TextareaParams.builder().field(field).required(true).rows(rows).monospace(true).build();
    }

    public TextareaParams codeWithHelp(String field, int rows, String helpText) {
        return TextareaParams.builder().field(field).rows(rows).monospace(true).helpText(helpText).build();
    }

    public TextareaParams requiredCodeWithHelp(String field, int rows, String helpText) {
        return TextareaParams.builder().field(field).required(true).rows(rows).monospace(true).helpText(helpText).build();
    }

    // ── SelectInput ────────────────────────────────────────────────────────────

    public SelectInputParams select(String field, Collection<?> options, String optionValue, String optionLabel) {
        return SelectInputParams.builder()
            .field(field).options(options).optionValue(optionValue).optionLabel(optionLabel).build();
    }

    public SelectInputParams requiredSelect(String field, Collection<?> options, String optionValue, String optionLabel) {
        return SelectInputParams.builder()
            .field(field).required(true).options(options).optionValue(optionValue).optionLabel(optionLabel).build();
    }
}
