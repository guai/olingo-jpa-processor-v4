package org.apache.olingo.jpa.processor.core.query;

import org.apache.olingo.jpa.metadata.core.edm.mapper.api.JPADescribedElement;
import org.apache.olingo.jpa.metadata.core.edm.mapper.exception.ODataJPAModelException;
import org.apache.olingo.jpa.processor.core.exception.ODataJPAConversionException;

/**
 * Helper class to convert attribute values between OData and JPA without entity as context.
 *
 * @author Ralf Zozmann
 *
 */
public final class ValueConverter extends AbstractConverter {

  public ValueConverter() {
    super();
  }

  @Override
  public Object convertJPA2ODataPrimitiveValue(final JPADescribedElement attribute, final Object jpaValue)
      throws ODataJPAConversionException, ODataJPAModelException {
    return super.convertJPA2ODataPrimitiveValue(attribute, jpaValue);
  }

}
