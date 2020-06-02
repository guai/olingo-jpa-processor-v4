package org.apache.olingo.jpa.client.example;

import java.net.URL;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.apache.olingo.client.api.ODataClient;
import org.apache.olingo.client.api.domain.ClientPrimitiveValue;
import org.apache.olingo.commons.api.ex.ODataException;
import org.apache.olingo.jpa.client.example.util.AccessTestBase;
import org.apache.olingo.jpa.client.example.util.LocalTestODataClient;
import org.apache.olingo.jpa.processor.core.mapping.converter.UtilDate2UtilCalendarODataAttributeConverter;
import org.apache.olingo.jpa.processor.core.testmodel.AbstractDatatypeConversionEntityAccess;
import org.apache.olingo.jpa.processor.core.testmodel.DatatypeConversionEntityDto;
import org.apache.olingo.jpa.processor.core.testmodel.converter.odata.EdmUrlConverter;
import org.junit.Assert;
import org.junit.Test;

public class DatatypeConversionEntityAccessTest extends AccessTestBase {

  private class DatatypeConversionEntityAccess extends AbstractDatatypeConversionEntityAccess {

    @Override
    protected java.net.URI getServiceRootUrl() {
      return URI;
    }

    @Override
    protected String determineAuthorizationHeaderValue() {
      // no authorization required for test
      return null;
    }

    @Override
    protected ODataClient createClient() {
      return new LocalTestODataClient(persistenceAdapter);
    }

    @Override
    protected URL convertODataAUrlViaOrgApacheOlingoJpaProcessorCoreTestmodelConverterOdataEdmUrlConverter(
        final String propertyName, final ClientPrimitiveValue propertyValue, final Integer maxLength,
        final Integer precision, final Integer scale)
            throws ODataException {
      return new EdmUrlConverter().convertToJPA(propertyValue.toCastValue(String.class));
    }
  }

  @Test
  public void testLoadDatatypeConversionEntity() throws Exception {
    final DatatypeConversionEntityAccess endpoint = new DatatypeConversionEntityAccess();
    final DatatypeConversionEntityDto dto = endpoint.retrieve(Integer.valueOf(2));
    Assert.assertNotNull(dto);
    Assert.assertEquals(Integer.valueOf(2000), dto.getAIntegerYear());
    // check unexpected time zone/type conversion
    Assert.assertEquals(LocalDateTime.of(2016, 01, 20, 9, 21, 23), dto.getATimestamp2());
    Assert.assertEquals(Timestamp.valueOf(LocalDateTime.of(2010, 01, 01, 23, 00, 59)), dto
        .getATimestamp1SqlTimestamp());
    Assert.assertEquals(Timestamp.valueOf(LocalDateTime.of(2010, 01, 01, 23, 00, 59)), dto.getATimestamp1UtilDate());
    Assert.assertEquals(java.sql.Date.valueOf(LocalDate.of(2090, 12, 01)), dto.getADate1());
    Assert.assertEquals(new UtilDate2UtilCalendarODataAttributeConverter().convertToOData(java.sql.Date.valueOf(
        LocalDate.of(1000, 10, 12))), dto.getADate3());
    Assert.assertEquals(UUID.fromString("7f905a0b-bb6e-11e3-9e8f-000000000001"), dto.getUuid());
  }

}
