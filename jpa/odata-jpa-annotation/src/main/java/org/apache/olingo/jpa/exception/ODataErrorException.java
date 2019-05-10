package org.apache.olingo.jpa.exception;

import org.apache.olingo.commons.api.ex.ODataError;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.ODataApplicationException;

/**
 * 'Helper' exception to transport more details about internal state of business logic to clients. Mostly used for OData actions.
 *
 */
public class ODataErrorException extends ODataApplicationException {

	private static final long serialVersionUID = -2759540990572142254L;

	private final ODataError error;

	public ODataErrorException(final ODataError error) {
		super(error.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), null);
		this.error = error;
	}

	public ODataError getError() {
		return error;
	}

}
