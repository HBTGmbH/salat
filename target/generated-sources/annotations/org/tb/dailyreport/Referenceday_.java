package org.tb.dailyreport;

import java.time.LocalDate;
import javax.annotation.Generated;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;
import org.tb.dailyreport.domain.Referenceday;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(Referenceday.class)
public abstract class Referenceday_ extends org.tb.common.AuditedEntity_ {

	public static volatile SingularAttribute<Referenceday, Boolean> workingday;
	public static volatile SingularAttribute<Referenceday, String> name;
	public static volatile SingularAttribute<Referenceday, LocalDate> refdate;
	public static volatile SingularAttribute<Referenceday, String> dow;
	public static volatile SingularAttribute<Referenceday, Boolean> holiday;

	public static final String WORKINGDAY = "workingday";
	public static final String NAME = "name";
	public static final String REFDATE = "refdate";
	public static final String DOW = "dow";
	public static final String HOLIDAY = "holiday";

}

