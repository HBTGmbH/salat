package org.tb.persistence;

import org.tb.exception.ApplicationException;

public class EmployeeAlreadyExistsException extends ApplicationException {

	EmployeeAlreadyExistsException(String message) {
		super(message);
	}

}
