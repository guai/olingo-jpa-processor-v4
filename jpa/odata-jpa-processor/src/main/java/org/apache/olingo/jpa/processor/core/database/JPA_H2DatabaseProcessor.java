package org.apache.olingo.jpa.processor.core.database;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Expression;

import org.apache.olingo.server.api.ODataApplicationException;

public class JPA_H2DatabaseProcessor extends AbstractJPADatabaseProcessor {

	@Override
	protected Expression<?> cast2Date(final Expression<?> value) throws ODataApplicationException {
		final CriteriaBuilder cb = getCriteriaBuilder();
		return cb.function("PARSEDATETIME", value.getJavaType(), value, cb.literal("YYYY-MM-DD"));
	}
}
