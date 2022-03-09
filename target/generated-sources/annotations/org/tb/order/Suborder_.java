package org.tb.order;

import java.time.Duration;
import java.time.LocalDate;
import javax.annotation.Generated;
import javax.persistence.metamodel.ListAttribute;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;
import org.tb.dailyreport.domain.Timereport;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Employeeorder;
import org.tb.order.domain.Suborder;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(Suborder.class)
public abstract class Suborder_ extends org.tb.common.AuditedEntity_ {

	public static volatile SingularAttribute<Suborder, Boolean> standard;
	public static volatile SingularAttribute<Suborder, String> shortdescription;
	public static volatile SingularAttribute<Suborder, Boolean> commentnecessary;
	public static volatile SingularAttribute<Suborder, Byte> debithoursunit;
	public static volatile SingularAttribute<Suborder, Suborder> parentorder;
	public static volatile SingularAttribute<Suborder, String> sign;
	public static volatile ListAttribute<Suborder, Employeeorder> employeeorders;
	public static volatile SingularAttribute<Suborder, String> description;
	public static volatile ListAttribute<Suborder, Timereport> timereports;
	public static volatile SingularAttribute<Suborder, Customerorder> customerorder;
	public static volatile SingularAttribute<Suborder, Boolean> trainingFlag;
	public static volatile ListAttribute<Suborder, Suborder> suborders;
	public static volatile SingularAttribute<Suborder, Boolean> fixedPrice;
	public static volatile SingularAttribute<Suborder, LocalDate> fromDate;
	public static volatile SingularAttribute<Suborder, Boolean> hide;
	public static volatile SingularAttribute<Suborder, Duration> debitMinutes;
	public static volatile SingularAttribute<Suborder, LocalDate> untilDate;
	public static volatile SingularAttribute<Suborder, String> suborder_customer;
	public static volatile SingularAttribute<Suborder, Character> invoice;

	public static final String STANDARD = "standard";
	public static final String SHORTDESCRIPTION = "shortdescription";
	public static final String COMMENTNECESSARY = "commentnecessary";
	public static final String DEBITHOURSUNIT = "debithoursunit";
	public static final String PARENTORDER = "parentorder";
	public static final String SIGN = "sign";
	public static final String EMPLOYEEORDERS = "employeeorders";
	public static final String DESCRIPTION = "description";
	public static final String TIMEREPORTS = "timereports";
	public static final String CUSTOMERORDER = "customerorder";
	public static final String TRAINING_FLAG = "trainingFlag";
	public static final String SUBORDERS = "suborders";
	public static final String FIXED_PRICE = "fixedPrice";
	public static final String FROM_DATE = "fromDate";
	public static final String HIDE = "hide";
	public static final String DEBIT_MINUTES = "debitMinutes";
	public static final String UNTIL_DATE = "untilDate";
	public static final String SUBORDER_CUSTOMER = "suborder_customer";
	public static final String INVOICE = "invoice";

}

