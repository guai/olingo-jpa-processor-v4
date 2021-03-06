package org.apache.olingo.jpa.processor.core.action;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import javax.persistence.Id;

import org.apache.olingo.client.api.ODataClient;
import org.apache.olingo.client.api.ODataClientBuilder;
import org.apache.olingo.client.api.communication.request.cud.UpdateType;
import org.apache.olingo.client.api.domain.ClientEntity;
import org.apache.olingo.client.api.domain.ClientEntitySet;
import org.apache.olingo.client.api.domain.ClientInvokeResult;
import org.apache.olingo.client.api.domain.ClientValue;
import org.apache.olingo.client.api.uri.URIBuilder;
import org.apache.olingo.client.core.communication.request.cud.ODataEntityUpdateRequestImpl;
import org.apache.olingo.client.core.communication.request.invoke.ODataInvokeRequestImpl;
import org.apache.olingo.commons.api.Constants;
import org.apache.olingo.commons.api.ex.ODataException;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.http.HttpMethod;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.jpa.cdi.Inject;
import org.apache.olingo.jpa.metadata.core.edm.NamingStrategy;
import org.apache.olingo.jpa.metadata.core.edm.annotation.EdmAction;
import org.apache.olingo.jpa.metadata.core.edm.annotation.EdmActionParameter;
import org.apache.olingo.jpa.metadata.core.edm.dto.ODataDTO;
import org.apache.olingo.jpa.processor.core.mapping.JPAAdapter;
import org.apache.olingo.jpa.processor.core.testmodel.BusinessPartnerRole;
import org.apache.olingo.jpa.processor.core.testmodel.Organization;
import org.apache.olingo.jpa.processor.core.testmodel.Phone;
import org.apache.olingo.jpa.processor.core.testmodel.PostalAddressData;
import org.apache.olingo.jpa.processor.core.testmodel.dto.EnvironmentInfo;
import org.apache.olingo.jpa.processor.core.testmodel.dto.NestedStructureWithId;
import org.apache.olingo.jpa.processor.core.testmodel.dto.NestedStructureWithoutId;
import org.apache.olingo.jpa.processor.core.testmodel.dto.SystemRequirement;
import org.apache.olingo.jpa.processor.core.testmodel.otherpackage.TestEnum;
import org.apache.olingo.jpa.processor.core.util.ServerCallSimulator;
import org.apache.olingo.jpa.processor.core.util.TestBase;
import org.apache.olingo.jpa.test.util.AbstractTest.JPAProvider;
import org.apache.olingo.jpa.test.util.Constant;
import org.junit.Test;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class TestJPAActions extends TestBase {

  @ODataDTO(attributeNaming = NamingStrategy.AsIs)
  public static class ActionDTO {

    @Id
    private final long id = System.currentTimeMillis();

    /**
     * Unbound oData action.
     */
    @EdmAction
    public static int processDTOCollection(@EdmActionParameter(name = "params") final Collection<ActionDTO> params) {
      if (params == null || params.isEmpty()) {
        throw new IllegalStateException("Params not given");
      }
      return params.size();
    }

    /**
     * Unbound oData action.
     */
    @EdmAction
    public static Organization createOrganization(@EdmActionParameter(name = "demoId") final String id,
        @EdmActionParameter(name = "withElementCollection") final boolean withElementCollection,
        @EdmActionParameter(name = "withAssociation") final boolean withAssociation,
        @Inject final JPAAdapter adapter) {
      if (adapter == null) {
        throw new IllegalStateException("JPAAdapter not onjected");
      }
      if (id == null || id.isEmpty()) {
        throw new IllegalStateException("Id not given");
      }
      final Organization org = new Organization();
      org.setID(id);
      org.setName1("name 1");
      org.setCountry("DEU");
      org.setCustomString1("custom 1");
      org.setType("1");
      final PostalAddressData address = org.getAddress();
      address.setCityName("Berlin");
      address.setPOBox("1234567");
      org.setAddress(address);
      // leave 'communicationData' untouched to transfer empty complex type (but not
      // null)
      assert org.getCommunicationData() != null;
      if (withElementCollection) {
        final Phone phone = new Phone();
        phone.setInternationalAreaCode("+42");
        phone.setPhoneNumber("987654321");
        org.addPhone(phone);
        org.getPhoneNumbersAsString().add("67676767676");
        org.getPhoneNumbersAsString().add("123-567-999");
      }
      if (withAssociation) {
        final BusinessPartnerRole role = new BusinessPartnerRole();
        role.setBusinessPartnerID(org.getID());
        role.setRoleCategory("TEST");
        org.setRoles(Collections.singletonList(role));
      }
      return org;
    }

  }

  @Test
  public void testBoundPrimitiveActionWithEntityParameter() throws IOException, ODataException {
    assumeTrue("Hibernate does not build a proper columns selection without quoting of column name",
        getJPAProvider() != JPAProvider.Hibernate);

    final StringBuffer requestBody = new StringBuffer("{");
    requestBody.append("\"dummy\": " + Integer.toString(3)).append(", ");
    requestBody.append("\"country\": {");
    requestBody.append("\"Code\": \"DEU\"").append(", ");
    requestBody.append("\"Language\": \"de\"").append(", ");
    requestBody.append("\"Name\": \"Deutschland\"");
    requestBody.append("}");
    requestBody.append("}");

    final URIBuilder uriBuilder = newUriBuilder().appendEntitySetSegment("Persons").appendKeySegment("99")
        .appendActionCallSegment(Constant.PUNIT_NAME + ".extractCountryCode");
    final ServerCallSimulator helper = new ServerCallSimulator(persistenceAdapter, uriBuilder, requestBody.toString(),
        HttpMethod.POST);
    helper.setRequestedResponseContentType("application/json;odata.metadata=full");
    helper.execute(HttpStatusCode.OK.getStatusCode());

    final ObjectNode object = helper.getJsonObjectValue();
    assertNotNull(object);
    assertEquals("DEU", object.get("value").asText());
  }

  @Test
  public void testUnboundVoidAction() throws IOException, ODataException, NoSuchMethodException {

    persistenceAdapter.registerDTO(EnvironmentInfo.class);
    persistenceAdapter.registerDTO(SystemRequirement.class);

    final URIBuilder uriBuilder = newUriBuilder().appendActionCallSegment("unboundVoidAction");
    final ServerCallSimulator helper = new ServerCallSimulator(persistenceAdapter, uriBuilder, null,
        HttpMethod.POST);
    helper.setRequestedResponseContentType("application/json;odata.metadata=full");
    helper.execute(HttpStatusCode.NO_CONTENT.getStatusCode());
  }

  @Test
  public void testUnboundEntityActionWithoutComplexTypesAndAssociations()
      throws IOException, ODataException, NoSuchMethodException {

    persistenceAdapter.registerDTO(ActionDTO.class);

    final String testId = "3";
    final StringBuffer requestBody = new StringBuffer("{");
    requestBody.append("\"demoId\": \"" + testId + "\",");
    requestBody.append("\"withElementCollection\": false,");
    requestBody.append("\"withAssociation\": false");
    requestBody.append("}");

    final URIBuilder uriBuilder = newUriBuilder().appendActionCallSegment("createOrganization");
    final ServerCallSimulator helper = new ServerCallSimulator(persistenceAdapter, uriBuilder, requestBody.toString(),
        HttpMethod.POST);
    helper.execute(HttpStatusCode.OK.getStatusCode());
    final ObjectNode object = helper.getJsonObjectValue();
    assertNotNull(object);
    assertEquals(testId, object.get("ID").asText());
    assertTrue(object.get("CommunicationData").get("MobilePhoneNumber") instanceof NullNode);
    assertNotNull(object.get("PhoneNumbersAsString"));
    assertNotNull(object.get("PhoneNumbers"));
    assertTrue(((ArrayNode) object.get("PhoneNumbers")).size() == 0);
    assertTrue(((ArrayNode) object.get("PhoneNumbersAsString")).size() == 0);
  }

  @Test
  public void testUnboundEntityActionWithComplexTypesAndAssociations()
      throws IOException, ODataException, NoSuchMethodException {

    persistenceAdapter.registerDTO(ActionDTO.class);
    persistenceAdapter.registerDTO(SystemRequirement.class);

    final StringBuffer requestBody = new StringBuffer("{");
    final String testId = "5";
    requestBody.append("\"demoId\": \"" + testId + "\",");
    requestBody.append("\"withElementCollection\": true,");
    requestBody.append("\"withAssociation\": true");
    requestBody.append("}");

    final URIBuilder uriBuilder = newUriBuilder().appendActionCallSegment("createOrganization");
    final ServerCallSimulator helper = new ServerCallSimulator(persistenceAdapter, uriBuilder, requestBody.toString(),
        HttpMethod.POST);
    helper.setRequestedResponseContentType("application/json;odata.metadata=full");
    helper.execute(HttpStatusCode.OK.getStatusCode());

    // check json reponse
    final ObjectNode organization = helper.getJsonObjectValue();
    assertNotNull(organization);
    assertEquals(testId, organization.get("ID").asText());
    assertTrue(organization.get("CommunicationData").get("MobilePhoneNumber") instanceof NullNode);
    assertNotNull(organization.get("PhoneNumbersAsString"));
    assertNotNull(organization.get("PhoneNumbers"));
    assertTrue(((ArrayNode) organization.get("PhoneNumbers")).size() == 1);
    assertTrue(((ArrayNode) organization.get("PhoneNumbersAsString")).size() == 2);
    assertEquals(1, ((ArrayNode) organization.get("Roles")).size());
    // Olingo client response
    final ClientEntity olingoEntity = helper.getOlingoEntityValue();
    assertNotNull(olingoEntity);
    assertEquals("TEST", olingoEntity.getNavigationLink("Roles").asInlineEntitySet().getEntitySet().getEntities().get(0)
        .getProperty(
            "RoleCategory").getPrimitiveValue().toCastValue(String.class));
  }

  @Test
  public void testBoundPrimitiveActionWithEnumParameter() throws IOException, ODataException {
    assumeTrue("Hibernate does not build a proper columns selection without quoting of column name",
        getJPAProvider() != JPAProvider.Hibernate);

    final StringBuffer requestBody = new StringBuffer("{");
    final String testValue = TestEnum.Three.name();
    requestBody.append("\"value\": \"" + testValue + "\"");
    requestBody.append("}");

    final URIBuilder uriBuilder = newUriBuilder().appendEntitySetSegment("Persons").appendKeySegment("99")
        .appendActionCallSegment(Constant.PUNIT_NAME + ".sendBackEnumParameter");
    final ServerCallSimulator helper = new ServerCallSimulator(persistenceAdapter,
        uriBuilder, requestBody.toString(), HttpMethod.POST);
    helper.execute(HttpStatusCode.OK.getStatusCode());

    final ObjectNode object = helper.getJsonObjectValue();
    assertNotNull(object);
    assertEquals(testValue, object.get("value").asText());
  }

  @Test
  public void testBoundActionForEntityWithEmbeddedId() throws IOException, ODataException {
    final Map<String, Object> mapKeys = new HashMap<String, Object>();
    mapKeys.put("DivisionCode", "BE212");
    mapKeys.put("CodeID", "NUTS3");
    mapKeys.put("CodePublisher", "Eurostat");
    mapKeys.put("Language", "de");
    final URIBuilder uriBuilder = newUriBuilder().appendEntitySetSegment("AdministrativeDivisionDescriptions")
        .appendKeySegment(mapKeys)
        .appendActionCallSegment(Constant.PUNIT_NAME + ".boundActionCheckLoadingOfEmbeddedId");
    final ServerCallSimulator helper = new ServerCallSimulator(persistenceAdapter,
        uriBuilder, null, HttpMethod.POST);
    helper.execute(HttpStatusCode.NO_CONTENT.getStatusCode());
  }

  @Test
  public void testUnboundActionForEntityWithEmbeddedId() throws IOException, ODataException {

    final StringBuffer requestBody = new StringBuffer("{");
    requestBody.append("\"parameter\": {");
    requestBody.append("\"Name\": \"test\",");
    requestBody.append("\"CodePublisher\": \"Eurostat\",");
    requestBody.append("\"CodeID\": \"NUTS3\",");
    requestBody.append("\"DivisionCode\": \"BE212\",");
    requestBody.append("\"Language\": \"de\"");
    requestBody.append("}");
    requestBody.append("}");

    final URIBuilder uriBuilder = newUriBuilder().appendActionCallSegment("unboundActionCheckLoadingOfEmbeddedId");
    final ServerCallSimulator helper = new ServerCallSimulator(persistenceAdapter,
        uriBuilder, requestBody.toString(), HttpMethod.POST);
    helper.execute(HttpStatusCode.NO_CONTENT.getStatusCode());
  }

  @Test
  public void testActionThrowingCustomHttpStatusErrorCode()
      throws IOException, ODataException, NoSuchMethodException {

    persistenceAdapter.registerDTO(EnvironmentInfo.class);
    persistenceAdapter.registerDTO(SystemRequirement.class);

    final URIBuilder uriBuilder = newUriBuilder().appendActionCallSegment("throwODataApplicationException");
    final ServerCallSimulator helper = new ServerCallSimulator(persistenceAdapter,
        uriBuilder, null, HttpMethod.POST);
    helper.execute(911);
  }

  @Test
  public void testActionInAbstractEntity() throws IOException, ODataException, NoSuchMethodException {
    assumeTrue("Hibernate cannot handle an abstract entity class as resource",
        getJPAProvider() != JPAProvider.Hibernate);

    URIBuilder uriBuilder;

    // the action must be present in all concrete/abstract entity classes
    uriBuilder = newUriBuilder().appendEntitySetSegment("RelationshipEntities").appendKeySegment(Integer.valueOf(1))
        .appendActionCallSegment(Constant.PUNIT_NAME + ".actionInAbstractEntity");
    final ServerCallSimulator helper1 = new ServerCallSimulator(persistenceAdapter,
        uriBuilder, null, HttpMethod.POST);
    helper1.execute(HttpStatusCode.NO_CONTENT.getStatusCode());

    uriBuilder = newUriBuilder().appendEntitySetSegment("RelationshipSourceEntities").appendKeySegment(Integer.valueOf(
        1)).appendActionCallSegment(Constant.PUNIT_NAME + ".actionInAbstractEntity");
    final ServerCallSimulator helper2 = new ServerCallSimulator(persistenceAdapter,
        uriBuilder, null, HttpMethod.POST);
    helper2.execute(HttpStatusCode.NO_CONTENT.getStatusCode());

    uriBuilder = newUriBuilder().appendEntitySetSegment("RelationshipTargetEntities").appendKeySegment(Integer.valueOf(
        2)).appendActionCallSegment(Constant.PUNIT_NAME + ".actionInAbstractEntity");
    final ServerCallSimulator helper3 = new ServerCallSimulator(persistenceAdapter,
        uriBuilder, null, HttpMethod.POST);
    helper3.execute(HttpStatusCode.NO_CONTENT.getStatusCode());

    // must fail
    uriBuilder = newUriBuilder().appendEntitySetSegment("DatatypeConversionEntities").appendKeySegment(Integer.valueOf(
        1)).appendActionCallSegment(Constant.PUNIT_NAME + ".actionInAbstractEntity");
    final ServerCallSimulator helper4 = new ServerCallSimulator(persistenceAdapter,
        uriBuilder, null, HttpMethod.POST);
    helper4.execute(HttpStatusCode.BAD_REQUEST.getStatusCode());

  }

  @Test
  public void testBoundActionInMappedSuperclass() throws IOException, ODataException, NoSuchMethodException {
    assumeTrue("Hibernate cannot handle an abstract entity class as resource",
        getJPAProvider() != JPAProvider.Hibernate);

    // the action must be present in all concrete/abstract entity classes

    final URIBuilder uriBuilder1 = newUriBuilder().appendEntitySetSegment("RelationshipEntities").appendKeySegment(
        Integer.valueOf(1)).appendActionCallSegment(Constant.PUNIT_NAME + ".actionInMappedSuperclass");
    final ServerCallSimulator helper1 = new ServerCallSimulator(persistenceAdapter,
        uriBuilder1,
        null, HttpMethod.POST);
    helper1.execute(HttpStatusCode.NO_CONTENT.getStatusCode());

    final URIBuilder uriBuilder2 = newUriBuilder().appendEntitySetSegment("RelationshipSourceEntities")
        .appendKeySegment(Integer.valueOf(1)).appendActionCallSegment(Constant.PUNIT_NAME
            + ".actionInMappedSuperclass");
    final ServerCallSimulator helper2 = new ServerCallSimulator(persistenceAdapter,
        uriBuilder2, null,
        HttpMethod.POST);
    helper2.execute(HttpStatusCode.NO_CONTENT.getStatusCode());

    final URIBuilder uriBuilder3 = newUriBuilder().appendEntitySetSegment("RelationshipTargetEntities")
        .appendKeySegment(Integer.valueOf(2)).appendActionCallSegment(Constant.PUNIT_NAME
            + ".actionInMappedSuperclass");
    final ServerCallSimulator helper3 = new ServerCallSimulator(persistenceAdapter, uriBuilder3, null,
        HttpMethod.POST);
    helper3.execute(HttpStatusCode.NO_CONTENT.getStatusCode());

    final URIBuilder uriBuilder4 = newUriBuilder().appendEntitySetSegment("DatatypeConversionEntities")
        .appendKeySegment(Integer.valueOf(1)).appendActionCallSegment(Constant.PUNIT_NAME
            + ".actionInMappedSuperclass");
    final ServerCallSimulator helper4 = new ServerCallSimulator(persistenceAdapter,
        uriBuilder4, null,
        HttpMethod.POST);
    helper4.execute(HttpStatusCode.NO_CONTENT.getStatusCode());
  }

  @Test
  public void testUnboundActionWithCollectionResult() throws IOException, ODataException {
    final URIBuilder uriBuilder = newUriBuilder().appendActionCallSegment("unboundActionWithStringCollectionResult");
    final ServerCallSimulator helper = new ServerCallSimulator(persistenceAdapter,
        uriBuilder, null, HttpMethod.POST);
    helper.execute(HttpStatusCode.OK.getStatusCode());

    final ArrayNode objects = helper.getJsonObjectValues();
    assertTrue(objects.size() == 2);
  }

  @Test
  public void testBoundActionSavingToDatabase() throws IOException, ODataException {

    final URIBuilder uriBuilder = newUriBuilder().appendEntitySetSegment("Organizations").appendKeySegment("9")
        .appendActionCallSegment(Constant.PUNIT_NAME + ".addPhoneToOrganizationAndSave");
    final ServerCallSimulator helper = new ServerCallSimulator(persistenceAdapter,
        uriBuilder, null, HttpMethod.POST);
    helper.execute(HttpStatusCode.NO_CONTENT.getStatusCode());
  }

  @Test
  public void testBoundActionForNonExistingResource() throws IOException, ODataException {

    final URIBuilder uriBuilder = newUriBuilder().appendEntitySetSegment("DatatypeConversionEntities").appendKeySegment(
        Integer.valueOf(9999)).appendActionCallSegment(Constant.PUNIT_NAME + ".actionInMappedSuperclass");
    final ServerCallSimulator helper = new ServerCallSimulator(persistenceAdapter,
        uriBuilder, null, HttpMethod.POST);
    helper.execute(HttpStatusCode.NOT_FOUND.getStatusCode());
  }

  @Test
  public void testUnboundActionWithPrimitiveCollectionResult() throws IOException, ODataException {

    persistenceAdapter.registerDTO(EnvironmentInfo.class);
    persistenceAdapter.registerDTO(SystemRequirement.class);

    final URIBuilder uriBuilder = newUriBuilder().appendActionCallSegment("actionWithPrimitiveCollectionResult");
    final ServerCallSimulator helper = new ServerCallSimulator(persistenceAdapter, uriBuilder, null,
        HttpMethod.POST);
    helper.execute(HttpStatusCode.OK.getStatusCode());
    assertTrue(helper.getJsonObjectValues().size() == 2);
  }

  @Test
  public void testActionWithDTOResultCollection() throws IOException, ODataException {

    persistenceAdapter.registerDTO(EnvironmentInfo.class);
    persistenceAdapter.registerDTO(SystemRequirement.class);

    final URIBuilder uriBuilderFillAction = newUriBuilder().appendActionCallSegment("fillDTOWithNestedComplexType");
    final ServerCallSimulator helperFillAction = new ServerCallSimulator(persistenceAdapter, uriBuilderFillAction, null, HttpMethod.POST);

    helperFillAction.execute(HttpStatusCode.OK.getStatusCode());
    final ArrayNode results = helperFillAction.getJsonObjectValues();
    assertTrue(results.size() == 2);
    assertTrue(((ObjectNode) results.get(0)).get("SystemRequirements").size() == 3);

    final ObjectNode jsonMapNode = (ObjectNode) ((ObjectNode) results.get(0)).get("MapOfNumberCollections");
    assertTrue(jsonMapNode.size() == 2);

    // second part of test: use map as action parameter
    final StringBuffer requestBody = new StringBuffer("{");
    requestBody.append("  \"mapParameter\": " + jsonMapNode.toString());
    requestBody.append("}");

    final URIBuilder uriBuilderParameterAction = newUriBuilder().appendActionCallSegment("actionWithMapParameter");
    final ServerCallSimulator helper = new ServerCallSimulator(persistenceAdapter, uriBuilderParameterAction,
        requestBody.toString(), HttpMethod.POST);
    helper.execute(HttpStatusCode.NO_CONTENT.getStatusCode());
  }

  @Test
  public void testMultipartFormContentUpload() throws Exception {

    // build complete multipart body in pure java
    // https://www.codejava.net/java-se/networking/upload-files-by-sending-multipart-request-programmatically
    final String fileName = "file-" + Long.toString(System.currentTimeMillis()) + ".log";
    final String boundary = "boundary";
    final String binaryData = "sdfjkgsdfjgsdkjfg";
    final String NEW_LINE = "\r\n";

    final StringBuffer requestBody = new StringBuffer("");
    // part 1: the filename as separate argument
    requestBody.append("--").append(boundary).append(NEW_LINE);
    requestBody.append("Content-Disposition: form-data; name=\"filename\"").append(NEW_LINE);
    requestBody.append(NEW_LINE);
    requestBody.append(fileName).append(NEW_LINE);
    // part 2: ignored data
    requestBody.append("--").append(boundary).append(NEW_LINE);
    requestBody.append("Content-Disposition: form-data; name=\"document-data\"").append(NEW_LINE);
    requestBody.append(NEW_LINE);
    requestBody.append("useless content").append(NEW_LINE);
    // part 3: the file content
    requestBody.append("--").append(boundary).append(NEW_LINE);
    requestBody.append("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"").append(NEW_LINE);
    requestBody.append("Content-Type: application/octet-stream").append(NEW_LINE);// content type of multi part entry
    requestBody.append(NEW_LINE);
    // we fake the content (no real binary content), because we a IT testing the call again
    requestBody.append(binaryData).append(NEW_LINE);
    // finalize multipart
    requestBody.append("--").append(boundary).append("--").append(NEW_LINE);

    final URIBuilder uriBuilder = newUriBuilder().appendActionCallSegment("uploadFile");
    final ServerCallSimulator helper = new ServerCallSimulator(persistenceAdapter, uriBuilder, requestBody
        .toString(), HttpMethod.POST);
    helper.setRequestContentType("multipart/form-data; boundary=" + boundary);// global content type

    helper.execute(HttpStatusCode.OK.getStatusCode());
    assertTrue(((ArrayNode) helper.getJsonObjectValue().get("value")).get(0).asText().equals(fileName));
    assertTrue(((ArrayNode) helper.getJsonObjectValue().get("value")).get(1).asInt() == binaryData.getBytes().length);
  }

  @Test
  public void testMultipartFormContentUploadMultipleFiles() throws Exception {

    // build complete multipart body in pure java
    // https://www.codejava.net/java-se/networking/upload-files-by-sending-multipart-request-programmatically
    final String fileName = "file-" + Long.toString(System.currentTimeMillis()) + ".tst";
    final String boundary = "boundary";
    final String binaryData1 = "1. file content";
    final String binaryData2 = "2. file content";
    final String NEW_LINE = "\r\n";

    final StringBuffer requestBody = new StringBuffer("");
    // part 1: number of files
    requestBody.append("--").append(boundary).append(NEW_LINE);
    requestBody.append("Content-Disposition: form-data; name=\"noOfFile\"").append(NEW_LINE);
    requestBody.append(NEW_LINE);
    requestBody.append("2").append(NEW_LINE);
    // part 2: the 1. file content
    requestBody.append("--").append(boundary).append(NEW_LINE);
    requestBody.append("Content-Disposition: form-data; name=\"filelist\"; filename=\"" + "1." + fileName + "\"")
    .append(NEW_LINE);
    requestBody.append("Content-Type: application/octet-stream").append(NEW_LINE);// content type of multi part entry
    requestBody.append(NEW_LINE);
    requestBody.append(binaryData1).append(NEW_LINE);
    // part 3: the 2. file content
    requestBody.append("--").append(boundary).append(NEW_LINE);
    requestBody.append("Content-Disposition: form-data; name=\"filelist\"; filename=\"" + "2." + fileName + "\"")
    .append(NEW_LINE);
    requestBody.append("Content-Type: application/octet-stream").append(NEW_LINE);// content type of multi part entry
    requestBody.append(NEW_LINE);
    requestBody.append(binaryData2).append(NEW_LINE);
    // finalize multipart
    requestBody.append("--").append(boundary).append("--").append(NEW_LINE);

    final URIBuilder uriBuilder = newUriBuilder().appendActionCallSegment("uploadMultipleFiles");
    final ServerCallSimulator helper = new ServerCallSimulator(persistenceAdapter, uriBuilder, requestBody.toString(),
        HttpMethod.POST);
    helper.setRequestContentType("multipart/form-data; boundary=" + boundary);// global content type

    helper.execute(HttpStatusCode.NO_CONTENT.getStatusCode());
  }

  @Test
  public void testUnboundActionWithMissedEmbedded() throws IOException, ODataException {

    final StringBuffer requestBody = new StringBuffer("{");
    requestBody.append("\"pi\": {");
    requestBody.append("\"PID\": \"dummyId\", ");
    requestBody.append("\"AdministrativeInformation\": null");// empty to check handling
    requestBody.append("}");
    requestBody.append("}");

    final URIBuilder uriBuilder = newUriBuilder().appendActionCallSegment("checkPersonImageWithoutEmbeddedArgument");
    final ServerCallSimulator helper = new ServerCallSimulator(persistenceAdapter,
        uriBuilder, requestBody.toString(), HttpMethod.POST);

    helper.execute(HttpStatusCode.NO_CONTENT.getStatusCode());
  }

  @Test
  public void testNestedStructureTransferBackendToFrontend() throws IOException, ODataException, NoSuchMethodException {

    persistenceAdapter.registerDTO(NestedStructureWithoutId.class);

    StringBuffer requestBody = new StringBuffer("{");
    requestBody.append("\"numberOfLevels\": 4");
    requestBody.append("}");
    URIBuilder uriBuilder = newUriBuilder().appendActionCallSegment("createNestedStructure");
    ServerCallSimulator helper = new ServerCallSimulator(persistenceAdapter, uriBuilder, requestBody.toString(),
        HttpMethod.POST);
    helper.execute(HttpStatusCode.OK.getStatusCode());

    final String jsonRaw = helper.getRawResult();
    // Olingo client response as validation check
    final ClientEntity olingoEntity = helper.getOlingoEntityValue();
    assertNotNull(olingoEntity);

    // reuse created structure to as parameter for another action
    requestBody = new StringBuffer("{");
    requestBody.append("  \"structure\": " + jsonRaw);
    requestBody.append("}");
    uriBuilder = newUriBuilder().appendActionCallSegment("validateNestedStructure");
    helper = new ServerCallSimulator(persistenceAdapter, uriBuilder, requestBody.toString(), HttpMethod.POST);
    helper.execute(HttpStatusCode.NO_CONTENT.getStatusCode());
  }

  @Test
  public void testUnboundEntityActionWithDTOCollectionParameter()
      throws IOException, ODataException, NoSuchMethodException {

    persistenceAdapter.registerDTO(ActionDTO.class);

    final StringBuffer requestBody = new StringBuffer("{");
    requestBody.append("\"params\": [");
    requestBody.append("{\"id\": 1},");
    requestBody.append("{\"id\": 2},");
    requestBody.append("{\"id\": 3}");
    requestBody.append("]");
    requestBody.append("}");

    final URIBuilder uriBuilder = newUriBuilder().appendActionCallSegment("processDTOCollection");
    final ServerCallSimulator helper = new ServerCallSimulator(persistenceAdapter, uriBuilder, requestBody.toString(),
        HttpMethod.POST);
    helper.execute(HttpStatusCode.OK.getStatusCode());
    final ObjectNode result = helper.getJsonObjectValue();
    assertNotNull(result);
    assertEquals(3, result.get("value").asInt());
  }

  @Test
  public void testUnboundEntityActionWithEntityCollectionParameter()
      throws IOException, ODataException, NoSuchMethodException {

    persistenceAdapter.registerDTO(ActionDTO.class);

    final StringBuffer requestBody = new StringBuffer("{");
    requestBody.append("\"params\": [");
    requestBody.append("{\"ID\": 1},");
    requestBody.append("{\"ID\": 2},");
    requestBody.append("{\"ID\": 3}");
    requestBody.append("]");
    requestBody.append("}");

    final URIBuilder uriBuilder = newUriBuilder().appendActionCallSegment("processEntityCollection");
    final ServerCallSimulator helper = new ServerCallSimulator(persistenceAdapter, uriBuilder, requestBody.toString(),
        HttpMethod.POST);
    helper.execute(HttpStatusCode.OK.getStatusCode());
    final ObjectNode result = helper.getJsonObjectValue();
    assertNotNull(result);
    assertEquals(3, result.get("value").asInt());
  }

  private StringBuffer buildActionPayload(final Map<String, ClientValue> actionParameters) throws URISyntaxException,
  IOException {
    final ODataClient odataClient = ODataClientBuilder.createClient();
    // fake parameters as Entity properties
    final ODataInvokeRequestImpl<ClientInvokeResult> olingoRequest =
        (ODataInvokeRequestImpl<ClientInvokeResult>) odataClient.getInvokeRequestFactory().getActionInvokeRequest(
            new URI("dummy"), ClientInvokeResult.class, actionParameters);

    final InputStream is = olingoRequest.getPayload();
    @SuppressWarnings("resource")
    final Scanner s = new Scanner(is).useDelimiter("\\A");
    final String result = s.hasNext() ? s.next() : "";
    return new StringBuffer(result);
  }

  @Test
  public void testNestedStructureWithoutIdAndMetadataUsingOlingoSerialization() throws IOException, ODataException, NoSuchMethodException,
  URISyntaxException {

    persistenceAdapter.registerDTO(NestedStructureWithoutId.class);

    // produce server side content
    StringBuffer requestBody = new StringBuffer("{");
    requestBody.append("  \"numberOfLevels\": 2");
    requestBody.append("}");
    URIBuilder uriBuilder = newUriBuilder().appendActionCallSegment("createNestedStructure");
    ServerCallSimulator helper = new ServerCallSimulator(persistenceAdapter, uriBuilder, requestBody.toString(),
        HttpMethod.POST);
    // important: normal metadata to avoid presence of binding link annotations
    helper.setRequestedResponseContentType(ContentType.JSON.toContentTypeString());
    helper.execute(HttpStatusCode.OK.getStatusCode());
    final ClientEntity rootODataEntity = helper.getOlingoEntityValue();

    final Map<String, ClientValue> actionParameters = new HashMap<>();
    actionParameters.put("structure", convertToActionParameter(rootODataEntity, NestedStructureWithoutId.class
        .getName()));
    requestBody = buildActionPayload(actionParameters);

    uriBuilder = newUriBuilder().appendActionCallSegment("validateNestedStructure");
    helper = new ServerCallSimulator(persistenceAdapter, uriBuilder, requestBody.toString(), HttpMethod.POST);
    helper.execute(HttpStatusCode.NO_CONTENT.getStatusCode());
  }

  private StringBuffer buildEntityPayload(final ClientEntity entity) throws URISyntaxException,
  IOException {
    final ODataClient odataClient = ODataClientBuilder.createClient();
    final ODataEntityUpdateRequestImpl<ClientEntity> olingoRequest =
        (ODataEntityUpdateRequestImpl<ClientEntity>) odataClient.getCUDRequestFactory().getEntityUpdateRequest(new URI(
            "dummy"), UpdateType.REPLACE, entity);

    final InputStream is = olingoRequest.getPayload();
    @SuppressWarnings("resource")
    final Scanner s = new Scanner(is).useDelimiter("\\A");
    final String result = s.hasNext() ? s.next() : "";
    return new StringBuffer(result);
  }

  @Test
  public void testNestedStructureWithIdAndMetadataUsingOlingoSerialization() throws IOException, ODataException,
  NoSuchMethodException,
  URISyntaxException {

    persistenceAdapter.registerDTO(EnvironmentInfo.class);
    persistenceAdapter.registerDTO(SystemRequirement.class);

    // produce server side content
    URIBuilder uriBuilder = newUriBuilder().appendActionCallSegment("fillDTOWithNestedComplexType");
    ServerCallSimulator helper = new ServerCallSimulator(persistenceAdapter, uriBuilder, null,
        HttpMethod.POST);
    // important: full metadata to force presence of binding link annotations
    helper.setRequestedResponseContentType(ContentType.JSON_FULL_METADATA.toContentTypeString());

    helper.execute(HttpStatusCode.OK.getStatusCode());
    final ArrayNode jsonTree = helper.getJsonObjectValues();
    final ObjectNode jsonMapNode = (ObjectNode) jsonTree.get(0).get("MapOfNumberCollections");
    assertNotNull(jsonMapNode);
    final ArrayNode jsonArray = (ArrayNode) jsonMapNode.get("numberOfEnv");
    assertEquals(1, jsonArray.size());
    final ArrayNode jsonArray2 = (ArrayNode) jsonMapNode.get("NullTest");
    assertEquals(0, jsonArray2.size());

    final ClientEntitySet set = helper.getOlingoEntityCollectionValues();

    assertEquals(2, set.getEntities().size());

    final ClientEntity rootODataEntity = set.getEntities().get(1);

    final StringBuffer requestBody = buildEntityPayload(rootODataEntity);
    // this test is only useful if the transfered request data contains binding link information
    assertTrue(requestBody.toString().contains("AliasEnvironment" + Constants.JSON_BIND_LINK_SUFFIX));

    // try to update via handler to trigger parsing
    uriBuilder = newUriBuilder().appendEntitySetSegment("EnvironmentInfos").appendKeySegment(rootODataEntity
        .getProperty("Id").getPrimitiveValue().toCastValue(Long.class));
    helper = new ServerCallSimulator(persistenceAdapter, uriBuilder, requestBody.toString(), HttpMethod.PUT);
    helper.execute(HttpStatusCode.OK.getStatusCode());
  }

  @Test
  public void testActionReturningComplexEntityNetworkWithMetadata() throws IOException, ODataException,
  NoSuchMethodException {
    final URIBuilder uriBuilder = newUriBuilder().appendActionCallSegment("createEntities");

    final ServerCallSimulator simulatorFullMetadata = new ServerCallSimulator(persistenceAdapter,
        uriBuilder, null,
        HttpMethod.POST);
    simulatorFullMetadata.setRequestedResponseContentType(ContentType.JSON_FULL_METADATA.toContentTypeString());
    simulatorFullMetadata.execute(HttpStatusCode.OK.getStatusCode());
    final ObjectNode sourceFull = simulatorFullMetadata.getJsonObjectValue();
    assertNotNull(sourceFull);
    // 'targets' must be filled, so no 'reference' must be present
    assertTrue(sourceFull.get("targets@odata.navigationLink").isNull());
    final ArrayNode targetsFull = sourceFull.withArray("targets");
    assertEquals(1, targetsFull.size());
    final ObjectNode target = (ObjectNode) targetsFull.get(0);
    assertNotNull(target);
    // 'source' backlink must not be filled, but the 'reference' must be present
    assertNotNull(target.get("SOURCE@odata.navigationLink"));
    assertTrue(target.get("SOURCE").isNull());
  }

  @Test
  public void testActionHandlingComplexEntityNetworkWithoutMetadata() throws IOException, ODataException,
  NoSuchMethodException {
    final URIBuilder uriBuilderCreate = newUriBuilder().appendActionCallSegment("createEntities");
    final ServerCallSimulator simulatorCreate = new ServerCallSimulator(persistenceAdapter,
        uriBuilderCreate, null,
        HttpMethod.POST);
    simulatorCreate.setRequestedResponseContentType(ContentType.JSON_NO_METADATA.toContentTypeString());
    simulatorCreate.execute(HttpStatusCode.OK.getStatusCode());
    final ObjectNode sourceCreated = simulatorCreate.getJsonObjectValue();
    assertNotNull(sourceCreated);
    final ArrayNode targets = sourceCreated.withArray("targets");
    assertEquals(1, targets.size());
    final ObjectNode target = (ObjectNode) targets.get(0);
    assertNotNull(target);
    // 'source' backlink must not be filled
    assertTrue(target.get("SOURCE").isNull());

    final String sourceNameBefore = "client side modified source name";
    sourceCreated.replace("Name", sourceCreated.textNode(sourceNameBefore));
    final String saveBody = "{\"source\": " + sourceCreated.toString() + "}";
    final URIBuilder uriBuilderSave = newUriBuilder().appendActionCallSegment("saveEntities");
    final ServerCallSimulator simulatorSave = new ServerCallSimulator(persistenceAdapter,
        uriBuilderSave, saveBody,
        HttpMethod.POST);
    simulatorSave.setRequestedResponseContentType(ContentType.JSON_NO_METADATA.toContentTypeString());
    simulatorSave.execute(HttpStatusCode.OK.getStatusCode());
    final ObjectNode sourceSaved = simulatorSave.getJsonObjectValue();
    assertNotNull(sourceSaved);
    // source name must be modified on server side
    assertNotEquals(sourceNameBefore, sourceSaved.get("Name").asText());
  }

  @Test
  public void testActionHandlingEntityCollectionWithReusedEntityRelationshipWithMetadata() throws IOException,
  ODataException,
  NoSuchMethodException {
    final URIBuilder uriBuilderCreate = newUriBuilder().appendActionCallSegment("createEntityCollection");
    final ServerCallSimulator simulatorCreate = new ServerCallSimulator(persistenceAdapter,
        uriBuilderCreate, null,
        HttpMethod.POST);
    simulatorCreate.setRequestedResponseContentType(ContentType.JSON_FULL_METADATA.toContentTypeString());
    simulatorCreate.execute(HttpStatusCode.OK.getStatusCode());
    final ArrayNode sourcesCreated = simulatorCreate.getJsonObjectValues();
    assertEquals(2, sourcesCreated.size());

    final ObjectNode source1 = (ObjectNode) sourcesCreated.get(0);
    assertNotNull(source1);
    final ArrayNode targetsSource1 = source1.withArray("leftM2Ns");
    // 1 shared target
    assertEquals(1, targetsSource1.size());
    final Number targetId = targetsSource1.get(0).get("ID").numberValue();
    // backlink must be empty
    final ArrayNode backlingSourceTarget1 = targetsSource1.get(0).withArray("RightM2Ns");
    assertEquals("inverse relationship collection must be empty", 0, backlingSourceTarget1.size());

    final ObjectNode source2 = (ObjectNode) sourcesCreated.get(0);
    assertNotNull(source2);
    final ArrayNode targetsSource2 = source2.withArray("leftM2Ns");
    // 1 shared target
    assertEquals(1, targetsSource2.size());
    assertEquals(targetId, targetsSource2.get(0).get("ID").numberValue());
    // backlink must be empty
    final ArrayNode backlingSourceTarget2 = targetsSource2.get(0).withArray("RightM2Ns");
    assertEquals("inverse relationship collection must be empty", 0, backlingSourceTarget2.size());
  }

  @Test
  public void testActionHandlingEntityCollectionWithReusedEntityRelationshipWithoutMetadata() throws IOException,
  ODataException,
  NoSuchMethodException {
    final URIBuilder uriBuilderCreate = newUriBuilder().appendActionCallSegment("createEntityCollection");
    final ServerCallSimulator simulatorCreate = new ServerCallSimulator(persistenceAdapter,
        uriBuilderCreate, null,
        HttpMethod.POST);
    simulatorCreate.setRequestedResponseContentType(ContentType.JSON_NO_METADATA.toContentTypeString());
    simulatorCreate.execute(HttpStatusCode.OK.getStatusCode());
    final ArrayNode sourcesCreated = simulatorCreate.getJsonObjectValues();
    assertEquals(2, sourcesCreated.size());

    final ObjectNode source1 = (ObjectNode) sourcesCreated.get(0);
    assertNotNull(source1);
    final ArrayNode targetsSource1 = source1.withArray("leftM2Ns");
    // 1 shared target
    assertEquals(1, targetsSource1.size());
    final Number targetId = targetsSource1.get(0).get("ID").numberValue();
    // backlink must be empty
    final ArrayNode backlingSourceTarget1 = targetsSource1.get(0).withArray("RightM2Ns");
    assertEquals("inverse relationship collection must be empty", 0, backlingSourceTarget1.size());

    final ObjectNode source2 = (ObjectNode) sourcesCreated.get(0);
    assertNotNull(source2);
    final ArrayNode targetsSource2 = source2.withArray("leftM2Ns");
    // 1 shared target
    assertEquals(1, targetsSource2.size());
    assertEquals(targetId, targetsSource2.get(0).get("ID").numberValue());
    // backlink must be empty
    final ArrayNode backlingSourceTarget2 = targetsSource2.get(0).withArray("RightM2Ns");
    assertEquals("inverse relationship collection must be empty", 0, backlingSourceTarget2.size());

    // now send back the shared target entity (must be merged on backend side)
    final String sourceNameBefore = "client side modified source1 name";
    source1.replace("Name", source1.textNode(sourceNameBefore));
    final String saveBody = "{\"entities\": " + sourcesCreated.toString() + "}";
    final URIBuilder uriBuilderSave = newUriBuilder().appendActionCallSegment("validateEntityCollection");
    final ServerCallSimulator simulatorSave = new ServerCallSimulator(persistenceAdapter,
        uriBuilderSave, saveBody,
        HttpMethod.POST);
    simulatorSave.setRequestedResponseContentType(ContentType.JSON_NO_METADATA.toContentTypeString());
    simulatorSave.execute(HttpStatusCode.OK.getStatusCode());
    final ArrayNode sourcesSaved = simulatorSave.getJsonObjectValues();
    assertNotNull(sourcesSaved);
  }

  @Test
  public void testDetectionOfModifiedReusedEntityRelationship() throws IOException,
  ODataException,
  NoSuchMethodException {
    final URIBuilder uriBuilderCreate = newUriBuilder().appendActionCallSegment("createEntityCollection");
    final ServerCallSimulator simulatorCreate = new ServerCallSimulator(persistenceAdapter,
        uriBuilderCreate, null,
        HttpMethod.POST);
    simulatorCreate.setRequestedResponseContentType(ContentType.JSON_NO_METADATA.toContentTypeString());
    simulatorCreate.execute(HttpStatusCode.OK.getStatusCode());
    final ArrayNode sourcesCreated = simulatorCreate.getJsonObjectValues();
    assertEquals(2, sourcesCreated.size());

    // now modify shared target and send back the shared target entity (merge must be failing on backend side)
    final ObjectNode sharedTargetOnSource1 = (ObjectNode) sourcesCreated.get(0).withArray("leftM2Ns").get(0);
    sharedTargetOnSource1.replace("Name", sharedTargetOnSource1.textNode(
        "client side modified target name on source 1"));
    final String saveBody = "{\"entities\": " + sourcesCreated.toString() + "}";
    final URIBuilder uriBuilderSave = newUriBuilder().appendActionCallSegment("validateEntityCollection");
    final ServerCallSimulator simulatorSave = new ServerCallSimulator(persistenceAdapter,
        uriBuilderSave, saveBody,
        HttpMethod.POST);
    simulatorSave.setRequestedResponseContentType(ContentType.JSON_NO_METADATA.toContentTypeString());
    simulatorSave.execute(HttpStatusCode.CONFLICT.getStatusCode());
  }

  @Test
  public void testBoundActionModifyingBusinessPartner() throws IOException, ODataException {
    assumeTrue("Hibernate cannot handle an abstract entity class as resource",
        getJPAProvider() != JPAProvider.Hibernate);

    final StringBuffer requestBody = new StringBuffer("{");
    requestBody.append("  \"changedCityName\": \"MyCity\"");
    requestBody.append("}");

    // save changed BusinessPartner
    final URIBuilder uriBuilderAction = newUriBuilder().appendEntitySetSegment("BusinessPartners").appendKeySegment(
        "5").appendActionCallSegment(Constant.PUNIT_NAME
            + ".modifyBusinessPartner");
    final ServerCallSimulator helper = new ServerCallSimulator(persistenceAdapter,
        uriBuilderAction, requestBody.toString(), HttpMethod.POST);
    helper.execute(HttpStatusCode.OK.getStatusCode());
  }

  @Test
  public void testNestedStructureWithIdUsingOlingoSerialization() throws IOException, ODataException,
  NoSuchMethodException, URISyntaxException {

    persistenceAdapter.registerDTO(NestedStructureWithId.class);

    // produce server side content
    final URIBuilder uriBuilder = newUriBuilder().appendActionCallSegment("createNestedStructureWithShared");
    final ServerCallSimulator simulatorCreate = new ServerCallSimulator(persistenceAdapter, uriBuilder, null,
        HttpMethod.POST);
    // important: normal metadata to avoid presence of binding link annotations
    simulatorCreate.setRequestedResponseContentType(ContentType.JSON.toContentTypeString());
    simulatorCreate.execute(HttpStatusCode.OK.getStatusCode());
    final ObjectNode sourceCreated = simulatorCreate.getJsonObjectValue();
    assertNotNull(sourceCreated);
    final ArrayNode childrenRoot = sourceCreated.withArray("children");
    assertEquals(2, childrenRoot.size());
    // the only child 'shared leaf' must exist on both middle nodes
    assertEquals(1, childrenRoot.get(0).withArray("children").size());
    assertEquals(1, childrenRoot.get(1).withArray("children").size());
    assertEquals(childrenRoot.get(0).withArray("children").get(0).get("name"), childrenRoot.get(1).withArray("children")
        .get(0).get("name"));
  }

}
