package org.tb.persistence;

import org.tb.exception.ApplicationException;

public class EmployeeAlreadyExistsException extends ApplicationException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L; // -6263681703403125232L;

	EmployeeAlreadyExistsException(String message) {
		super(message);
	}

}
