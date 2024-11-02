package org.tb.common.csv;

import com.google.common.base.Supplier;
import com.opencsv.bean.ColumnPositionMappingStrategy;
import com.opencsv.exceptions.CsvBeanIntrospectionException;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;

import java.util.Map;

public class PositionAwareColumnMappingStrategy<T> extends ColumnPositionMappingStrategy<T> {

    private final Supplier<T> supplier;

    public PositionAwareColumnMappingStrategy(Class<T> clazz, Supplier<T> supplier){
        super();
        this.supplier = supplier;
        this.setType(clazz);
    }

    @Override
    public String[] generateHeader(T bean) throws CsvRequiredFieldEmptyException {
        super.generateHeader(bean);
        return super.getColumnMapping();
    }

    @Override
    protected Map<Class<?>, Object> createBean() throws CsvBeanIntrospectionException, IllegalStateException {
        return Map.of(getType(), supplier.get());
    }
}
