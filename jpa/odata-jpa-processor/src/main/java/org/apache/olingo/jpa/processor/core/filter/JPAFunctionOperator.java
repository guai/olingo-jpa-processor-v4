package org.apache.olingo.jpa.processor.core.filter;

import java.util.List;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Expression;

import org.apache.olingo.commons.api.data.ValueType;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.jpa.metadata.core.edm.mapper.api.JPAFunction;
import org.apache.olingo.jpa.metadata.core.edm.mapper.api.JPAOperationParameter;
import org.apache.olingo.jpa.metadata.core.edm.mapper.api.JPAOperationResultParameter;
import org.apache.olingo.jpa.processor.core.exception.ODataJPAFilterException;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.uri.UriParameter;
import org.apache.olingo.server.api.uri.queryoption.expression.ExpressionVisitException;
import org.apache.olingo.server.core.uri.queryoption.expression.LiteralImpl;

/**
 * Handle OData Functions that are implemented e.g. as user defined functions data base functions. This will be mapped
 * to JPA criteria builder function().
 *
 * @author Oliver Grande
 *
 */
public class JPAFunctionOperator implements JPAExpression<Object> {
  private final JPAFunction jpaFunction;
  private final JPAVisitor visitor;
  private final List<UriParameter> uriParams;

  public JPAFunctionOperator(final JPAVisitor jpaVisitor, final List<UriParameter> uriParams, final JPAFunction jpaFunction) {

    super();
    this.uriParams = uriParams;
    this.visitor = jpaVisitor;
    this.jpaFunction = jpaFunction;
  }

  @SuppressWarnings("unchecked")
  @Override
  public Expression<Object> get() throws ODataApplicationException {
    final CriteriaBuilder cb = visitor.getCriteriaBuilder();
    final List<JPAOperationParameter> parameters = jpaFunction.getParameter();
    final Expression<?>[] jpaParameter = new Expression<?>[parameters.size()];

    if (jpaFunction.getResultParameter().isCollection()) {
      throw new ODataJPAFilterException(ODataJPAFilterException.MessageKeys.NOT_SUPPORTED_FUNCTION_COLLECTION,
          HttpStatusCode.NOT_IMPLEMENTED);
    }

    final ValueType resultValueType = jpaFunction.getResultParameter().getResultValueType();
    if (resultValueType != ValueType.ENUM && resultValueType != ValueType.PRIMITIVE
        && resultValueType != ValueType.GEOSPATIAL) {
      throw new ODataJPAFilterException(ODataJPAFilterException.MessageKeys.NOT_SUPPORTED_FUNCTION_NOT_SCALAR,
          HttpStatusCode.NOT_IMPLEMENTED);
    }
    for (int i = 0; i < parameters.size(); i++) {
      final JPAOperationParameter parameter = parameters.get(i);
      // a. $it/Area b. Area c. 10000
      final UriParameter p = findUriParameter(parameter);

      if (p.getText() != null) {
        final JPALiteralOperand lOperand = new JPALiteralOperand(visitor.getOdata(),
            visitor.getCriteriaBuilder(), new LiteralImpl(p.getText(), null));
        jpaParameter[i] = lOperand.getLiteralExpression(parameter);
      } else {
        try {
          jpaParameter[i] = (Expression<?>) p.getExpression().accept(visitor).get();
        } catch (final ExpressionVisitException e) {
          throw new ODataJPAFilterException(e, HttpStatusCode.NOT_IMPLEMENTED);
        }
      }
    }
    return (Expression<Object>) cb.function(jpaFunction.getDBName(), jpaFunction.getResultParameter().getType(),
        jpaParameter);
  }

  private UriParameter findUriParameter(final JPAOperationParameter jpaFunctionParam) {
    for (final UriParameter uriParam : uriParams) {
      if (uriParam.getName().equals(jpaFunctionParam.getName())) {
        return uriParam;
      }
    }
    return null;
  }

  public JPAOperationResultParameter getReturnType() {
    return jpaFunction.getResultParameter();
  }

}
