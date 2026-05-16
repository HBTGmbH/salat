package org.tb.common.thymeleaf;

import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.dialect.AbstractDialect;
import org.thymeleaf.dialect.IExpressionObjectDialect;
import org.thymeleaf.expression.IExpressionObjectFactory;

import java.util.Set;

public class FragmentFactoriesDialect extends AbstractDialect implements IExpressionObjectDialect {

    private final IExpressionObjectFactory factory;
    private final DangerZoneParamsFactory dangerZoneParamsFactory;
    private final FilterCardParamsFactory filterCardParamsFactory;
    private final MasterTableParamsFactory masterTableParamsFactory;
    private final FormFieldParamsFactory formFieldParamsFactory;

    public FragmentFactoriesDialect(
            DangerZoneParamsFactory dangerZoneParamsFactory,
            FilterCardParamsFactory filterCardParamsFactory,
            MasterTableParamsFactory masterTableParamsFactory,
            FormFieldParamsFactory formFieldParamsFactory) {
        super("FragmentFactoriesDialect");
        this.dangerZoneParamsFactory = dangerZoneParamsFactory;
        this.filterCardParamsFactory = filterCardParamsFactory;
        this.masterTableParamsFactory = masterTableParamsFactory;
        this.formFieldParamsFactory = formFieldParamsFactory;
        this.factory = new ExpressionObjectFactory();
    }

    @Override
    public IExpressionObjectFactory getExpressionObjectFactory() {
        return factory;
    }

    class ExpressionObjectFactory implements IExpressionObjectFactory {

        private static final Set<String> NAMES = Set.of("dzone", "fcard", "ffield", "mtable");

        @Override
        public Set<String> getAllExpressionObjectNames() {
            return NAMES;
        }

        @Override
        public Object buildObject(IExpressionContext context, String expressionObjectName) {
            return switch(expressionObjectName) {
                case "dzone" -> dangerZoneParamsFactory;
                case "fcard" -> filterCardParamsFactory;
                case "ffield" -> formFieldParamsFactory;
                case "mtable" -> masterTableParamsFactory;
                default -> null;
            };
        }

        @Override
        public boolean isCacheable(String expressionObjectName) {
            return true;
        }
    }

}
