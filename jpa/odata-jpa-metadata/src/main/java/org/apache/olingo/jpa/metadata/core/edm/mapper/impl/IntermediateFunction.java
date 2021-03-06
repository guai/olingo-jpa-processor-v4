package org.apache.olingo.jpa.metadata.core.edm.mapper.impl;

import java.lang.reflect.AnnotatedElement;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.metamodel.PluralAttribute.CollectionType;

import org.apache.olingo.commons.api.data.ValueType;
import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.apache.olingo.commons.api.edm.geo.SRID;
import org.apache.olingo.commons.api.edm.provider.CsdlFunction;
import org.apache.olingo.commons.api.edm.provider.CsdlParameter;
import org.apache.olingo.commons.api.edm.provider.CsdlReturnType;
import org.apache.olingo.commons.api.ex.ODataRuntimeException;
import org.apache.olingo.jpa.metadata.core.edm.annotation.EdmFunction;
import org.apache.olingo.jpa.metadata.core.edm.annotation.EdmFunction.ReturnType;
import org.apache.olingo.jpa.metadata.core.edm.annotation.EdmFunctionParameter;
import org.apache.olingo.jpa.metadata.core.edm.mapper.api.JPAFunction;
import org.apache.olingo.jpa.metadata.core.edm.mapper.api.JPAOperationParameter;
import org.apache.olingo.jpa.metadata.core.edm.mapper.api.JPAOperationResultParameter;
import org.apache.olingo.jpa.metadata.core.edm.mapper.api.JPAStructuredType;
import org.apache.olingo.jpa.metadata.core.edm.mapper.exception.ODataJPAModelException;

/**
 * Mapper, that is able to convert different metadata resources into a edm function metadata. It is important to know
 * that:
 * <cite>Functions MUST NOT have observable side effects and MUST return a single instance or a collection of instances
 * of any type.</cite>
 * <p>For details about Function metadata see:
 * <a href=
 * "https://docs.oasis-open.org/odata/odata/v4.0/errata02/os/complete/part3-csdl/odata-v4.0-errata02-os-part3-csdl-complete.html#_Toc406398010"
 * >OData Version 4.0 Part 3 - 12.2 Element edm:Function</a>
 * @author Oliver Grande
 *
 */

class IntermediateFunction extends IntermediateModelElement<CsdlFunction> implements JPAFunction {
  private CsdlFunction edmFunction;
  private final EdmFunction jpaUserDefinedFunction;
  private final IntermediateServiceDocument isd;
  private final Class<?> jpaDefiningPOJO;

  IntermediateFunction(final JPAEdmNameBuilder nameBuilder, final EdmFunction jpaFunction,
      final Class<?> definingPOJO, final IntermediateServiceDocument isd)
          throws ODataJPAModelException {
    super(nameBuilder, jpaFunction.name());
    this.setExternalName(jpaFunction.name());
    this.jpaUserDefinedFunction = jpaFunction;
    this.jpaDefiningPOJO = definingPOJO;
    this.isd = isd;
  }

  @Override
  public String getDBName() {
    return jpaUserDefinedFunction.functionName();
  }

  @Override
  public List<JPAOperationParameter> getParameter() {
    final List<JPAOperationParameter> parameterList = new ArrayList<JPAOperationParameter>();
    for (final EdmFunctionParameter jpaParameter : jpaUserDefinedFunction.parameter()) {
      parameterList.add(new IntermediatFunctionParameter(jpaParameter));
    }
    return parameterList;
  }

  @Override
  public JPAOperationResultParameter getResultParameter() {
    return new IntermediatResultFunctionParameter(jpaUserDefinedFunction.returnType());
  }

  @Override
  protected void lazyBuildEdmItem() throws ODataJPAModelException {
    if (edmFunction == null) {
      edmFunction = new CsdlFunction();
      edmFunction.setName(getExternalName());
      edmFunction.setParameters(returnNullIfEmpty(determineEdmInputParameter()));
      edmFunction.setReturnType(determineEdmResultType(jpaUserDefinedFunction.returnType()));
      edmFunction.setBound(jpaUserDefinedFunction.isBound());
      // TODO edmFunction.setComposable(isComposable)
      edmFunction.setComposable(false);
      // TODO edmFunction.setEntitySetPath(entitySetPath) for bound functions

    }
  }

  @Override
  CsdlFunction getEdmItem() throws ODataRuntimeException {
    try {
      lazyBuildEdmItem();
    } catch (final ODataJPAModelException e) {
      throw new ODataRuntimeException(e);
    }
    return edmFunction;
  }

  String getUserDefinedFunction() {
    return jpaUserDefinedFunction.functionName();
  }

  boolean requiresFunctionImport() {
    return !isBound() && jpaUserDefinedFunction.hasFunctionImport();
  }

  @Override
  public boolean isBound() {
    return jpaUserDefinedFunction.isBound();
  }

  private List<CsdlParameter> determineEdmInputParameter() throws ODataJPAModelException {
    final List<CsdlParameter> edmInputParameterList = new ArrayList<CsdlParameter>();
    for (final EdmFunctionParameter jpaParameter : jpaUserDefinedFunction.parameter()) {

      final CsdlParameter edmInputParameter = new CsdlParameter();
      edmInputParameter.setName(jpaParameter.name());
      edmInputParameter.setType(TypeMapping.convertToEdmSimpleType(jpaParameter.type()).getFullQualifiedName());

      edmInputParameter.setNullable(false);
      edmInputParameter.setCollection(jpaParameter.isCollection());
      if (jpaParameter.maxLength() >= 0) {
        edmInputParameter.setMaxLength(Integer.valueOf(jpaParameter.maxLength()));
      }
      if (jpaParameter.precision() >= 0) {
        edmInputParameter.setPrecision(Integer.valueOf(jpaParameter.precision()));
      }
      if (jpaParameter.scale() >= 0) {
        edmInputParameter.setScale(Integer.valueOf(jpaParameter.scale()));
      }
      if (jpaParameter.srid() != null && !jpaParameter.srid().srid().isEmpty()) {
        final SRID srid = SRID.valueOf(jpaParameter.srid().srid());
        srid.setDimension(jpaParameter.srid().dimension());
        edmInputParameter.setSrid(srid);
      }
      edmInputParameterList.add(edmInputParameter);
    }
    return edmInputParameterList;
  }

  private CsdlReturnType determineEdmResultType(final ReturnType returnType) throws ODataJPAModelException {
    final CsdlReturnType edmResultType = new CsdlReturnType();
    FullQualifiedName fqn;
    if (returnType.type() == Object.class) {
      final JPAStructuredType et = isd.getStructuredType(jpaDefiningPOJO);
      fqn = getNameBuilder().buildFQN(et.getExternalName());
      this.setIgnore(et.ignore()); // If the result type shall be ignored, ignore also a function that returns it
    } else {
      final JPAStructuredType et = isd.getStructuredType(returnType.type());
      if (et != null) {
        fqn = getNameBuilder().buildFQN(et.getExternalName());
        this.setIgnore(et.ignore()); // If the result type shall be ignored, ignore also a function that returns it
      } else {
        fqn = TypeMapping.convertToEdmSimpleType(returnType.type()).getFullQualifiedName();
      }
    }
    edmResultType.setType(fqn);
    edmResultType.setCollection(returnType.isCollection());
    edmResultType.setNullable(returnType.isNullable());
    if (returnType.maxLength() >= 0) {
      edmResultType.setMaxLength(Integer.valueOf(returnType.maxLength()));
    }
    if (returnType.precision() >= 0) {
      edmResultType.setPrecision(Integer.valueOf(returnType.precision()));
    }
    if (returnType.scale() >= 0) {
      edmResultType.setScale(Integer.valueOf(returnType.scale()));
    }
    if (returnType.srid() != null && !returnType.srid().srid().isEmpty()) {
      final SRID srid = SRID.valueOf(returnType.srid().srid());
      srid.setDimension(returnType.srid().dimension());
      edmResultType.setSrid(srid);
    }
    return edmResultType;
  }

  private class IntermediatFunctionParameter implements JPAOperationParameter {
    private final EdmFunctionParameter jpaParameter;

    IntermediatFunctionParameter(final EdmFunctionParameter jpaParameter) {
      this.jpaParameter = jpaParameter;
    }

    @Override
    public AnnotatedElement getAnnotatedElement() {
      // currently not supported
      return null;
    }

    @SuppressWarnings("unused")
    public String getDBName() {
      return jpaParameter.parameterName();
    }

    @Override
    public String getName() {
      return jpaParameter.name();
    }

    @Override
    public Class<?> getType() {
      return jpaParameter.type();
    }

    @Override
    public CollectionType getCollectionType() {
      if (isCollection()) {
        return CollectionType.COLLECTION;
      }
      return null;
    }

    @Override
    public Integer getMaxLength() {
      return Integer.valueOf(jpaParameter.maxLength());
    }

    @Override
    public Integer getPrecision() {
      return Integer.valueOf(jpaParameter.precision());
    }

    @Override
    public Integer getScale() {
      return Integer.valueOf(jpaParameter.scale());
    }

    @Override
    public boolean isNullable() {
      return jpaParameter.isNullable();
    }

    @Override
    public FullQualifiedName getTypeFQN() throws ODataJPAModelException {
      return TypeMapping.convertToEdmSimpleType(jpaParameter.type()).getFullQualifiedName();
    }

    @Override
    public boolean isCollection() {
      return jpaParameter.isCollection();
    }

    @Override
    public ParameterKind getParameterKind() {
      // TODO support @Inject also for functions
      return ParameterKind.OData;
    }

  }

  private class IntermediatResultFunctionParameter implements JPAOperationResultParameter {
    private final ReturnType jpaReturnType;

    public IntermediatResultFunctionParameter(final ReturnType jpaReturnType) {
      this.jpaReturnType = jpaReturnType;
    }

    @Override
    public AnnotatedElement getAnnotatedElement() {
      // currently not supported
      return null;
    }

    @Override
    public Class<?> getType() {
      return jpaReturnType.type();
    }

    @Override
    public CollectionType getCollectionType() {
      if (isCollection()) {
        return CollectionType.COLLECTION;
      }
      return null;
    }

    @Override
    public Integer getMaxLength() {
      return Integer.valueOf(jpaReturnType.maxLength());
    }

    @Override
    public Integer getPrecision() {
      return Integer.valueOf(jpaReturnType.precision());
    }

    @Override
    public Integer getScale() {
      return Integer.valueOf(jpaReturnType.scale());
    }

    @Override
    public boolean isNullable() {
      return jpaReturnType.isNullable();
    }

    @Override
    public FullQualifiedName getTypeFQN() {
      return edmFunction.getReturnType().getTypeFQN();
    }

    @Override
    public boolean isCollection() {
      return jpaReturnType.isCollection();
    }

    @Override
    public ValueType getResultValueType() {
      final Class<?> type = getType();
      if (type == Object.class) {
        return null;
      }
      return IntermediateFunction.this.determineValueType(isd, type, isCollection());
    }
  }
}
