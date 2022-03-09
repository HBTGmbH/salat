package org.tb.dailyreport;

import java.time.LocalDateTime;
import javax.annotation.Generated;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;
import org.tb.dailyreport.domain.Referenceday;
import org.tb.dailyreport.domain.Timereport;
import org.tb.employee.domain.Employeecontract;
import org.tb.order.domain.Employeeorder;
import org.tb.order.domain.Suborder;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(Timereport.class)
public abstract class Timereport_ extends org.tb.common.AuditedEntity_ {

	public static volatile SingularAttribute<Timereport, Suborder> suborder;
	public static volatile SingularAttribute<Timereport, Integer> durationhours;
	public static volatile SingularAttribute<Timereport, String> releasedby;
	public static volatile SingularAttribute<Timereport, Integer> durationminutes;
	public static volatile SingularAttribute<Timereport, LocalDateTime> accepted;
	public static volatile SingularAttribute<Timereport, Boolean> training;
	public static volatile SingularAttribute<Timereport, Integer> sequencenumber;
	public static volatile SingularAttribute<Timereport, String> taskdescription;
	public static volatile SingularAttribute<Timereport, Employeecontract> employeecontract;
	public static volatile SingularAttribute<Timereport, Employeeorder> employeeorder;
	public static volatile SingularAttribute<Timereport, Referenceday> referenceday;
	public static volatile SingularAttribute<Timereport, String> acceptedby;
	public static volatile SingularAttribute<Timereport, LocalDateTime> released;
	public static volatile SingularAttribute<Timereport, String> status;

	public static final String SUBORDER = "suborder";
	public static final String DURATIONHOURS = "durationhours";
	public static final String RELEASEDBY = "releasedby";
	public static final String DURATIONMINUTES = "durationminutes";
	public static final String ACCEPTED = "accepted";
	public static final String TRAINING = "training";
	public static final String SEQUENCENUMBER = "sequencenumber";
	public static final String TASKDESCRIPTION = "taskdescription";
	public static final String EMPLOYEECONTRACT = "employeecontract";
	public static final String EMPLOYEEORDER = "employeeorder";
	public static final String REFERENCEDAY = "referenceday";
	public static final String ACCEPTEDBY = "acceptedby";
	public static final String RELEASED = "released";
	public static final String STATUS = "status";

}

