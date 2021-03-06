package org.apache.olingo.jpa.processor.core.dto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.sql.SQLException;

import org.apache.olingo.client.api.uri.URIBuilder;
import org.apache.olingo.commons.api.ex.ODataException;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.http.HttpMethod;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.jpa.metadata.core.edm.dto.ODataDTO;
import org.apache.olingo.jpa.metadata.core.edm.mapper.exception.ODataJPAModelException;
import org.apache.olingo.jpa.processor.core.testmodel.dto.EnvironmentInfo;
import org.apache.olingo.jpa.processor.core.testmodel.dto.SystemRequirement;
import org.apache.olingo.jpa.processor.core.util.ServerCallSimulator;
import org.apache.olingo.jpa.processor.core.util.TestBase;
import org.apache.olingo.jpa.processor.core.util.TestGenericJPAPersistenceAdapter;
import org.apache.olingo.jpa.test.util.Constant;
import org.apache.olingo.jpa.test.util.DataSourceHelper;
import org.junit.Before;
import org.junit.Test;

public class TestDTOs extends TestBase {

  @ODataDTO
  public static class EnumDto {
    // use an enum not yet registered... and the DTO of custom schema will trigger enum type creation in another custom
    // schema...
    @SuppressWarnings("unused")
    private StandardProtocolFamily family;
  }

  @Before
  public void setup() throws ODataJPAModelException {
    persistenceAdapter.registerDTO(EnvironmentInfo.class);
    persistenceAdapter.registerDTO(SystemRequirement.class);
  }

  @Test(expected = ODataJPAModelException.class)
  public void testNonDTOThrowsError() throws IOException, ODataException, SQLException {
    // create own instance to avoid pollution of other tests
    final TestGenericJPAPersistenceAdapter myPersistenceAdapter = new TestGenericJPAPersistenceAdapter(
        Constant.PUNIT_NAME,
        DataSourceHelper.DatabaseType.HSQLDB);
    myPersistenceAdapter.registerDTO(TestDTOs.class);
    // must throw an exception on further processing
    final URIBuilder uriBuilder = newUriBuilder().appendMetadataSegment();
    final ServerCallSimulator helper = new ServerCallSimulator(myPersistenceAdapter, uriBuilder);
    helper.execute(HttpStatusCode.OK.getStatusCode());
  }

  @Test
  public void testDTOMetadata() throws IOException, ODataException, SQLException {

    final URIBuilder uriBuilder = newUriBuilder().appendMetadataSegment();
    final ServerCallSimulator helper = new ServerCallSimulator(persistenceAdapter, uriBuilder);
    helper.execute(HttpStatusCode.OK.getStatusCode());
    final String json = helper.getRawResult();
    assertTrue(!json.isEmpty());
    assertTrue(json.contains(EnvironmentInfo.class.getSimpleName()));
    assertTrue(json.contains(EnvironmentInfo.class.getSimpleName().concat("s")));// + entity set
  }

  @Test
  public void testGetDTO() throws IOException, ODataException, SQLException {

    final URIBuilder uriBuilder = newUriBuilder().appendEntitySetSegment("EnvironmentInfos");
    final ServerCallSimulator helper = new ServerCallSimulator(persistenceAdapter, uriBuilder);
    helper.execute(HttpStatusCode.OK.getStatusCode());
    assertTrue(helper.getJsonObjectValues().size() > 0);
    assertEquals(System.getProperty("java.version"), helper.getJsonObjectValues().get(0).get("JavaVersion").asText());
  }

  @Test
  public void testGetSpecificDTO() throws IOException, ODataException, SQLException {
    // our example DTO handler will not support loading of a DTO with a specific ID
    final URIBuilder uriBuilder = newUriBuilder().appendEntitySetSegment("EnvironmentInfos").appendKeySegment(Integer
        .valueOf(1));
    final ServerCallSimulator helper = new ServerCallSimulator(persistenceAdapter, uriBuilder);
    helper.execute(HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  public void testWriteDTO() throws IOException, ODataException, SQLException {
    final int iId = (int) System.currentTimeMillis();
    final String sId = Integer.toString(iId);
    final StringBuffer requestBody = new StringBuffer("{");
    requestBody.append("\"EnvNames\": [\"envA\"], ");
    requestBody.append("\"JavaVersion\": \"0.0.ex\", ");
    requestBody.append("\"MapOfNumberCollections\": {\"NullTest\": null, \"numberOfEnv\": [0, 1]},");
    requestBody.append("\"Id\": " + sId);
    requestBody.append("}");

    final URIBuilder uriBuilder = newUriBuilder().appendEntitySetSegment("EnvironmentInfos").appendKeySegment(Integer
        .valueOf(iId));
    final ServerCallSimulator helper = new ServerCallSimulator(persistenceAdapter, uriBuilder,
        requestBody.toString(), HttpMethod.PUT);
    helper.execute(HttpStatusCode.OK.getStatusCode());
    assertEquals(sId, helper.getJsonObjectValue().get("Id").asText());
  }

  @Test
  public void testDTOWithEnumAttribute() throws IOException, ODataException, SQLException {
    persistenceAdapter.registerDTO(EnumDto.class);

    final URIBuilder uriBuilder = newUriBuilder().appendMetadataSegment();
    final ServerCallSimulator helper = new ServerCallSimulator(persistenceAdapter, uriBuilder,
        null, HttpMethod.GET);
    helper.setRequestedResponseContentType(ContentType.APPLICATION_XML.toContentTypeString());
    helper.execute(HttpStatusCode.OK.getStatusCode());
  }
}
