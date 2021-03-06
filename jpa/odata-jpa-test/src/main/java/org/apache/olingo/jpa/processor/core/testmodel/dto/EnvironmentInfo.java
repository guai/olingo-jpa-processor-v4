package org.apache.olingo.jpa.processor.core.testmodel.dto;

import java.io.Serializable;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.persistence.Id;

import org.apache.olingo.jpa.cdi.Inject;
import org.apache.olingo.jpa.metadata.core.edm.annotation.EdmAction;
import org.apache.olingo.jpa.metadata.core.edm.annotation.EdmActionParameter;
import org.apache.olingo.jpa.metadata.core.edm.annotation.EdmIgnore;
import org.apache.olingo.jpa.metadata.core.edm.dto.ODataDTO;
import org.apache.olingo.jpa.security.ODataOperationAccess;
import org.apache.olingo.server.api.ODataApplicationException;

/**
 * Test POJO to realize a OData entity without JPA persistence.
 *
 * @author Ralf Zozmann
 *
 */
@ODataDTO(handler = EnvironmentInfoHandler.class)
public class EnvironmentInfo {

  @EdmIgnore
  private final Object ignoredSerializableField = new Serializable() {

    private static final long serialVersionUID = 1L;
  };

  private String javaVersion = null;

  @Id
  private long id = System.currentTimeMillis() + hashCode();

  private final Collection<String> envNames = new ArrayList<>();

  private final Collection<SystemRequirement> systemRequirements = new ArrayList<>();

  private final Map<String, Collection<Integer>> mapOfNumberCollections = new HashMap<>();

  private EnvironmentInfo aliasEnvironment = null;

  public EnvironmentInfo() {
    // default constructor for JPA
  }

  EnvironmentInfo(final String javaVersion, final Collection<String> envNames) {
    this.javaVersion = javaVersion;
    this.envNames.addAll(envNames);
    mapOfNumberCollections.put("numberOfEnv", Collections.singletonList(Integer.valueOf(envNames.size())));
    mapOfNumberCollections.put("NullTest", null);
  }

  public void setJavaVersion(final String jv) {
    this.javaVersion = jv;
  }

  public void setId(final long id) {
    this.id = id;
  }

  public long getId() {
    return id;
  }

  public String getJavaVersion() {
    return javaVersion;
  }

  public Collection<String> getEnvNames() {
    return envNames;
  }

  public EnvironmentInfo getAliasEnvironment() {
    return aliasEnvironment;
  }

  public Map<String, Collection<Integer>> getMapOfNumberCollections() {
    return mapOfNumberCollections;
  }

  /**
   * Unbound oData action without specific name.
   */
  @EdmAction
  public static void unboundVoidAction(@Inject final EntityManager em) {
    if (em == null) {
      throw new IllegalStateException("Entitymanager was not injected");
    }
  }

  @EdmAction
  @ODataOperationAccess(authenticationRequired = false)
  public static int actionWithNoSecurity() {
    return 42;
  }

  @EdmAction
  @ODataOperationAccess
  public static String actionWithOnlyAuthentication(@Inject final Principal user) {
    return user.getName();
  }

  @EdmAction
  @ODataOperationAccess(rolesAllowed = { "access" })
  public static void actionWithOnlyRole(@Inject final Principal user) {
    if (user == null) {
      throw new IllegalStateException("User was not injected or not authenticated");
    }
  }

  @EdmAction
  public static void throwODataApplicationException() throws ODataApplicationException {
    throw new ODataApplicationException("Proprietary status code 911 thrown", 911, Locale.getDefault());
  }

  @EdmAction
  public static Collection<EnvironmentInfo> fillDTOWithNestedComplexType() {
    final Collection<String> propNames = System.getProperties().keySet().stream().map(Object::toString)
        .collect(Collectors.toList());
    final EnvironmentInfo info1 = new EnvironmentInfo("java1", propNames);
    info1.systemRequirements.add(new SystemRequirement("re1", "description 1"));
    info1.systemRequirements.add(new SystemRequirement("re2", "description 2"));
    info1.systemRequirements.add(new SystemRequirement("re3", "description 3"));
    final EnvironmentInfo info2 = new EnvironmentInfo("java2", Collections.singletonList("none"));
    info2.aliasEnvironment = new EnvironmentInfo("aliasJava", Collections.emptyList());
    return Arrays.asList(info1, info2);
  }

  @EdmAction
  public static Collection<Integer> actionWithPrimitiveCollectionResult() {
    return Arrays.asList(Integer.valueOf(1), Integer.valueOf(2));
  }

  @EdmAction
  public static void actionWithMapParameter(
      @EdmActionParameter(name = "mapParameter") final Map<String, Collection<Integer>> mapParameter) {
    assert mapParameter != null;
    assert !mapParameter.isEmpty();
  }

}
