package org.apache.olingo.jpa.metadata.core.edm.mapper.impl;

import java.lang.reflect.AnnotatedElement;
import java.util.HashMap;
import java.util.Map;

import javax.persistence.metamodel.EntityType;

import org.apache.olingo.jpa.metadata.core.edm.annotation.EdmFunction;
import org.apache.olingo.jpa.metadata.core.edm.annotation.EdmFunctions;
import org.apache.olingo.jpa.metadata.core.edm.mapper.exception.ODataJPAModelException;

class IntermediateFunctionFactory {

  Map<? extends String, ? extends IntermediateFunction> create(final JPAEdmNameBuilder nameBuilder,
      final EntityType<?> jpaEntityType, final IntermediateServiceDocument isd) throws ODataJPAModelException {
    final Map<String, IntermediateFunction> funcList = new HashMap<String, IntermediateFunction>();

    if (jpaEntityType.getJavaType() instanceof AnnotatedElement) {
      final EdmFunctions jpaStoredProcedureList = ((AnnotatedElement) jpaEntityType.getJavaType())
          .getAnnotation(EdmFunctions.class);
      if (jpaStoredProcedureList != null) {
        for (final EdmFunction jpaStoredProcedure : jpaStoredProcedureList.value()) {
          putFunction(nameBuilder, jpaEntityType, isd, funcList, jpaStoredProcedure);
        }
      } else {
        final EdmFunction jpaStoredProcedure = ((AnnotatedElement) jpaEntityType.getJavaType())
            .getAnnotation(EdmFunction.class);
        if (jpaStoredProcedure != null) {
          putFunction(nameBuilder, jpaEntityType, isd, funcList, jpaStoredProcedure);
        }
      }
    }
    return funcList;
  }

  private void putFunction(final JPAEdmNameBuilder nameBuilder, final EntityType<?> jpaEntityType,
      final IntermediateServiceDocument isd,
      final Map<String, IntermediateFunction> funcList, final EdmFunction jpaStoredProcedure)
          throws ODataJPAModelException {
    final IntermediateFunction func = new IntermediateFunction(nameBuilder, jpaStoredProcedure, jpaEntityType
        .getJavaType(), isd);
    funcList.put(func.getExternalName(), func);
  }

}
