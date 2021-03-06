package org.apache.olingo.jpa.processor.core.filter;

import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Path;

import org.apache.olingo.jpa.processor.core.api.JPAODataDatabaseProcessor;
import org.apache.olingo.jpa.processor.core.query.FilterContextQueryBuilderIfc;
import org.apache.olingo.server.api.ODataApplicationException;

class JPAAggregationOperationCountImp implements JPAAggregationOperation {

  private final FilterContextQueryBuilderIfc parent;
  private final JPAODataDatabaseProcessor converter;

  public JPAAggregationOperationCountImp(final FilterContextQueryBuilderIfc parent,
      final JPAODataDatabaseProcessor converter) {
    this.parent = parent;
    this.converter = converter;
  }

  @Override
  public Expression<Long> get() throws ODataApplicationException {
    return converter.convert(this);
  }

  @Override
  public JPAFilterAggregationType getAggregation() {
    return JPAFilterAggregationType.COUNT;
  }

  public FilterContextQueryBuilderIfc getParent() {
    return parent;
  }

  @Override
  public Path<?> getPath() {
    return parent.getQueryResultFrom();
  }

}
