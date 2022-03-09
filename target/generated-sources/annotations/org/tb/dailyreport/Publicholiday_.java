package org.tb.dailyreport;

import java.time.LocalDate;
import javax.annotation.Generated;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;
import org.tb.dailyreport.domain.Publicholiday;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(Publicholiday.class)
public abstract class Publicholiday_ extends org.tb.common.AuditedEntity_ {

	public static volatile SingularAttribute<Publicholiday, String> name;
	public static volatile SingularAttribute<Publicholiday, LocalDate> refdate;

	public static final String NAME = "name";
	public static final String REFDATE = "refdate";

}

