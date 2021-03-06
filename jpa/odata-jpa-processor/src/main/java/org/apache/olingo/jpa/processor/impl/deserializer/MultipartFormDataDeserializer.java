package org.apache.olingo.jpa.processor.impl.deserializer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUpload;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.util.Streams;
import org.apache.commons.io.IOUtils;
import org.apache.olingo.commons.api.data.Parameter;
import org.apache.olingo.commons.api.data.ValueType;
import org.apache.olingo.commons.api.edm.EdmAction;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.edm.EdmParameter;
import org.apache.olingo.commons.api.edm.EdmProperty;
import org.apache.olingo.server.api.ODataRequest;
import org.apache.olingo.server.api.deserializer.DeserializerException;
import org.apache.olingo.server.api.deserializer.DeserializerResult;
import org.apache.olingo.server.api.deserializer.ODataDeserializer;
import org.apache.olingo.server.core.deserializer.DeserializerResultImpl;

public class MultipartFormDataDeserializer implements ODataDeserializer {

  private final Logger log = Logger.getLogger(ODataDeserializer.class.getName());
  private final ODataRequest request;

  public MultipartFormDataDeserializer(final ODataRequest request) {
    this.request = request;
  }

  @Override
  public DeserializerResult entity(final InputStream stream, final EdmEntityType edmEntityType) throws DeserializerException {
    throw new UnsupportedOperationException("not implented yet");
  }

  @Override
  public DeserializerResult entityCollection(final InputStream stream, final EdmEntityType edmEntityType) throws DeserializerException {
    throw new UnsupportedOperationException("not implented yet");
  }

  @Override
  public DeserializerResult actionParameters(final InputStream streamBody, final EdmAction edmAction) throws DeserializerException {
    final FileUpload upload = new FileUpload();
    final ODataRequestContext odataRequestContext = new ODataRequestContext(request, streamBody);
    try {
      final Map<String, Parameter> mapDataParameters = new HashMap<>();
      final FileItemIterator iter = upload.getItemIterator(odataRequestContext);
      while (iter.hasNext()) {
        final FileItemStream item = iter.next();
        final String parameterName = item.getFieldName();

        if (!checkActionParameter(edmAction, parameterName)) {
          continue;
        }

        final EdmParameter edmActionParameter = edmAction.getParameter(parameterName);
        Parameter dataParameter = mapDataParameters.get(parameterName);
        final ValueType vType = edmActionParameter.isCollection() ? ValueType.COLLECTION_PRIMITIVE
            : ValueType.PRIMITIVE;
        if (dataParameter == null) {
          dataParameter = new Parameter();
          dataParameter.setName(parameterName);
          dataParameter.setType(edmActionParameter.getType().getFullQualifiedName().getFullQualifiedNameAsString());
        }
        try (final InputStream stream = item.openStream();) {
          if (item.isFormField()) {
            // string value parameter
            assignParameterValue(vType, dataParameter, Streams.asString(stream));
          } else {
            // file == binary data == input stream
            // TODO avoid memory consuming buffering of complete input stream...
            // currently this loop here is auto-closing; also the FileItemIterator will close the previously opened item/stream
            // so we have no chance to forward the (unconsumed) input streams to the action... so we have to create a memory
            // loaded buffer :-(
            final byte[] data = IOUtils.toByteArray(stream);
            final ByteArrayInputStream bais = new ByteArrayInputStream(data);
            assignParameterValue(vType, dataParameter, bais);
          }
          mapDataParameters.put(parameterName, dataParameter);
        }
      }
      return DeserializerResultImpl.with().actionParameters(mapDataParameters).build();
    } catch (final IOException | FileUploadException e) {
      throw new DeserializerException("Couldn't parse action parameters from multi part/form-data body", e,
          DeserializerException.MessageKeys.IO_EXCEPTION);
    }
  }

  private void assignParameterValue(final ValueType valueType, final Parameter dataParameter, final Object value) {
    final boolean isCollection = valueType.getBaseType() != valueType;
    if (isCollection) {
      @SuppressWarnings("unchecked")
      Collection<Object> valueList = (Collection<Object>) dataParameter.getValue();
      if (valueList == null) {
        valueList = new LinkedList<>();
        dataParameter.setValue(valueType, valueList);
      }
      valueList.add(value);
    } else {
      dataParameter.setValue(valueType, value);
    }
  }

  private boolean checkActionParameter(final EdmAction edmAction, final String parameterName) throws DeserializerException {
    for (final String aN : edmAction.getParameterNames()) {
      if (!aN.equals(parameterName)) {
        continue;
      }
      final EdmParameter edmParameter = edmAction.getParameter(parameterName);
      switch (edmParameter.getType().getKind()) {
      case PRIMITIVE:
        // the only supported type(s)
        return true;
      case DEFINITION:
      case ENUM:
      case COMPLEX:
      case ENTITY:
      default:
        throw new DeserializerException(
            "Invalid type kind " + edmParameter.getType().getKind() + " for action parameter: " + parameterName,
            DeserializerException.MessageKeys.INVALID_ACTION_PARAMETER_TYPE, parameterName);
      }
    }
    log.log(Level.FINE,
        "The multi part/form-data entry with name '" + parameterName + "' is not an known action parameter name. Must be one of: "
            + String.join(", ", edmAction.getParameterNames()));
    return false;
  }

  @Override
  public DeserializerResult property(final InputStream stream, final EdmProperty edmProperty) throws DeserializerException {
    throw new UnsupportedOperationException("not implented yet");
  }

  @Override
  public DeserializerResult entityReferences(final InputStream stream) throws DeserializerException {
    throw new UnsupportedOperationException("not implented yet");
  }

}
