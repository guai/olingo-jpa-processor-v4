package org.apache.olingo.jpa.processor.core.api;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

import javax.persistence.EntityManager;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.olingo.commons.api.ex.ODataError;
import org.apache.olingo.commons.api.ex.ODataException;
import org.apache.olingo.commons.api.http.HttpMethod;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.jpa.exception.ODataErrorException;
import org.apache.olingo.jpa.processor.core.mapping.JPAAdapter;
import org.apache.olingo.jpa.processor.core.security.SecurityInceptor;
import org.apache.olingo.jpa.processor.core.util.ExtensibleContentTypeSupport;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.ODataHttpHandler;
import org.apache.olingo.server.api.ODataLibraryException;
import org.apache.olingo.server.api.ODataRequest;
import org.apache.olingo.server.api.ODataResponse;
import org.apache.olingo.server.api.ODataServerError;
import org.apache.olingo.server.api.OlingoExtension;
import org.apache.olingo.server.api.debug.DebugSupport;
import org.apache.olingo.server.api.deserializer.DeserializerException;
import org.apache.olingo.server.api.etag.CustomETagSupport;
import org.apache.olingo.server.api.processor.Processor;
import org.apache.olingo.server.api.serializer.CustomContentTypeSupport;
import org.apache.olingo.server.api.uri.UriInfo;
import org.apache.olingo.server.core.ODataExceptionHelper;
import org.apache.olingo.server.core.ODataHandlerException;
import org.apache.olingo.server.core.ODataHandlerImpl;
import org.apache.olingo.server.core.ODataHttpHandlerImplAccessor;
import org.apache.olingo.server.core.debug.ServerCoreDebugger;
import org.apache.olingo.server.core.uri.parser.Parser;
import org.apache.olingo.server.core.uri.parser.UriParserException;
import org.apache.olingo.server.core.uri.parser.UriParserSemanticException;
import org.apache.olingo.server.core.uri.parser.UriParserSyntaxException;
import org.apache.olingo.server.core.uri.validator.UriValidationException;

/**
 * @see org.apache.olingo.server.core.ODataHttpHandlerImpl
 */
class JPAODataHttpHandlerImpl extends ODataHandlerImpl implements ODataHttpHandler {

  private final ExtensibleContentTypeSupport contentSupport = new ExtensibleContentTypeSupport();
  private final JPAODataServletHandler servletHandler;
  private final JPAODataGlobalContextImpl globalContext;
  private final EntityManager em;
  private final ServerCoreDebugger debugger;
  private final JPAODataRequestContextImpl requestContext;
  private int split = 0;

  public JPAODataHttpHandlerImpl(final JPAODataServletHandler servletHandler,
      final JPAODataGlobalContextImpl globalContext, final HttpServletRequest request,
      final HttpServletResponse response) throws ODataException {
    super(servletHandler.getJPAODataContext().getOdata(),
        globalContext.getServiceMetaData(), globalContext.getServerDebugger());
    this.servletHandler = servletHandler;
    this.globalContext = globalContext;
    this.em = globalContext.refreshMappingAdapter().createEntityManager();
    this.debugger = globalContext.getServerDebugger();
    // call super to avoid 'forbidden' exception
    super.register(contentSupport);// at least for file uploads (but also for more...)
    requestContext = new JPAODataRequestContextImpl(em, globalContext, request, response);
    servletHandler.prepareRequestContext(requestContext);
  }

  JPAODataRequestContextImpl getRequestContext() {
    return requestContext;
  }

  ExtensibleContentTypeSupport getContentSupport() {
    return contentSupport;
  }

  protected ODataResponse processTransactional(final ODataRequest request) {

    try {
      checkSecurity(request);
    } catch (final ODataException e) {
      JPAODataServletHandler.LOG.log(Level.FINE, "Failed to preprocess request for security checks");
      return handleException(request, e);
    }

    final boolean isReadingRequest = request.getMethod() == HttpMethod.GET;

    final JPAAdapter mappingAdapter = requestContext.refreshMappingAdapter();
    ODataResponse odataResponse;
    try {
      mappingAdapter.beginTransaction(em);

      // call super.... to avoid DPI overlay
      odataResponse = super.process(request);

    } catch (final RuntimeException ex) {
      // do not commit on exceptions
      mappingAdapter.cancelTransaction(em);
      throw ex;
    }

    // finally processing
    if (odataResponse.getStatusCode() >= 200 && odataResponse.getStatusCode() < 300) {
      if (isReadingRequest) {
        // reading requests (per definition without data modification) are not committed
        JPAODataServletHandler.LOG.log(Level.FINER, "Do not commit request transaction, because is read only");
        mappingAdapter.cancelTransaction(em);
      } else {
        mappingAdapter.commitTransaction(em);
      }
    } else {
      JPAODataServletHandler.LOG.log(Level.WARNING, "Do not commit request transaction, because response is not 2xx");
      mappingAdapter.cancelTransaction(em);
    }
    // give implementors the chance to modify the response (set cache control etc.)
    servletHandler.modifyResponse(odataResponse);
    return odataResponse;
  }

  @Override
  public ODataResponse process(final ODataRequest request) {
    // this method is also called for every part of an batch request... so we have prepare a fresh request context
    try {
      requestContext.startDependencyInjectorOverlay();
      return super.process(request);
    } finally {
      requestContext.stopDependencyInjectorOverlay();
    }
  }

  @Override
  public void process(final HttpServletRequest request, final HttpServletResponse response) {
    final ODataRequest odRequest = new ODataRequest();
    Exception exception = null;
    ODataResponse odResponse;
    debugger.resolveDebugMode(request);

    final int processMethodHandle = debugger.startRuntimeMeasurement("ODataHttpHandlerImpl", "process");
    try {
      fillODataRequest(odRequest, request, split);

      odResponse = processTransactional(odRequest);
      // ALL future methods after process must not throw exceptions!
    } catch (final Exception e) {
      exception = e;
      odResponse = handleException(odRequest, e);
    }
    debugger.stopRuntimeMeasurement(processMethodHandle);

    if (debugger.isDebugMode()) {
      final Map<String, String> serverEnvironmentVariables = createEnvironmentVariablesMap(request);
      if (exception == null) {
        // This is to ensure that we have access to the thrown OData Exception
        exception = getLastThrownException();
      }
      odResponse = debugger.createDebugResponse(odRequest, odResponse, exception, getUriInfo(),
          serverEnvironmentVariables);
    }

    ODataHttpHandlerImplAccessor.convertToHttp(response, odResponse);
  }

  private ODataResponse handleException(final ODataRequest odRequest, final Exception e) {
    final ODataResponse resp = new ODataResponse();
    ODataServerError serverError;
    if (e instanceof ODataHandlerException) {
      serverError = ODataExceptionHelper.createServerErrorObject((ODataHandlerException) e, null);
    } else if (e instanceof ODataApplicationException) {
      serverError = ODataExceptionHelper.createServerErrorObject((ODataApplicationException) e);
    } else if (e instanceof UriValidationException) {
      serverError = ODataExceptionHelper.createServerErrorObject((UriValidationException) e, null);
    } else if (e instanceof UriParserSemanticException) {
      serverError = ODataExceptionHelper.createServerErrorObject((UriParserSemanticException) e, null);
    } else if (e instanceof UriParserSyntaxException) {
      serverError = ODataExceptionHelper.createServerErrorObject((UriParserSyntaxException) e, null);
    } else if (e instanceof UriParserException) {
      serverError = ODataExceptionHelper.createServerErrorObject((UriParserException) e, null);
    } else if (e instanceof ODataLibraryException) {
      serverError = ODataExceptionHelper.createServerErrorObject((ODataLibraryException) e, null);
    } else {
      serverError = ODataExceptionHelper.createServerErrorObject(e);
    }
    handleException(odRequest, resp, serverError, e);
    return resp;
  }

  @Override
  public void handleException(final ODataRequest request, final ODataResponse response,
      ODataServerError serverError, final Exception exception) {
    if (ODataErrorException.class.isInstance(exception)) {
      // special handling for already provided error embedded in exception
      final ODataError error = ODataErrorException.class.cast(exception).getError();
      if (ODataServerError.class.isInstance(error)) {
        serverError = ODataServerError.class.cast(error);
      } else {
        serverError = new ODataServerError();
        serverError.setStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode());
        serverError.setLocale(Locale.ENGLISH);
        serverError.setException(exception);
        serverError.setTarget(error.getTarget());
        serverError.setMessage(error.getMessage());
        serverError.setCode(error.getCode());
        serverError.setInnerError(error.getInnerError());
        serverError.setDetails(error.getDetails());
      }
    }
    super.handleException(request, response, serverError, exception);
  }

  /**
   * @see org.apache.olingo.server.core.ODataHttpHandlerImpl#fillODataRequest(ODataRequest, HttpServletRequest, int)
   */
  private ODataRequest fillODataRequest(final ODataRequest odRequest, final HttpServletRequest httpRequest,
      final int split) throws ODataLibraryException {
    final int requestHandle = debugger.startRuntimeMeasurement("ODataHttpHandlerImpl", "fillODataRequest");
    try {
      odRequest.setBody(httpRequest.getInputStream());
      odRequest.setProtocol(httpRequest.getProtocol());
      odRequest.setMethod(ODataHttpHandlerImplAccessor.extractMethod(httpRequest));
      int innerHandle = debugger.startRuntimeMeasurement("ODataHttpHandlerImpl", "copyHeaders");
      ODataHttpHandlerImplAccessor.copyHeaders(odRequest, httpRequest);
      debugger.stopRuntimeMeasurement(innerHandle);
      innerHandle = debugger.startRuntimeMeasurement("ODataHttpHandlerImpl", "fillUriInformation");
      ODataHttpHandlerImplAccessor.fillUriInformation(odRequest, httpRequest, split);
      debugger.stopRuntimeMeasurement(innerHandle);

      return odRequest;
    } catch (final IOException e) {
      throw new DeserializerException("An I/O exception occurred.", e,
          DeserializerException.MessageKeys.IO_EXCEPTION);
    } finally {
      debugger.stopRuntimeMeasurement(requestHandle);
    }
  }

  /**
   * @see org.apache.olingo.server.core.ODataHttpHandlerImpl#createEnvironmentVariablesMap(ODataRequest)
   */
  private Map<String, String> createEnvironmentVariablesMap(final HttpServletRequest request) {
    final Map<String, String> environment = new LinkedHashMap<String, String>();
    environment.put("authType", request.getAuthType());
    environment.put("localAddr", request.getLocalAddr());
    environment.put("localName", request.getLocalName());
    environment.put("localPort", getIntAsString(request.getLocalPort()));
    environment.put("pathInfo", request.getPathInfo());
    environment.put("pathTranslated", request.getPathTranslated());
    environment.put("remoteAddr", request.getRemoteAddr());
    environment.put("remoteHost", request.getRemoteHost());
    environment.put("remotePort", getIntAsString(request.getRemotePort()));
    environment.put("remoteUser", request.getRemoteUser());
    environment.put("scheme", request.getScheme());
    environment.put("serverName", request.getServerName());
    environment.put("serverPort", getIntAsString(request.getServerPort()));
    environment.put("servletPath", request.getServletPath());
    return environment;
  }

  private String getIntAsString(final int number) {
    return number == 0 ? "unknown" : Integer.toString(number);
  }

  @Override
  public void setSplit(final int split) {
    this.split = split;
  }

  @Override
  public void register(final DebugSupport debugSupport) {
    debugger.setDebugSupportProcessor(debugSupport);
  }

  @Override
  public void register(final Processor processor) {
    super.register(processor);
  }

  @Override
  public void register(final OlingoExtension extension) {
    if (CustomContentTypeSupport.class.isInstance(extension)) {
      register(CustomContentTypeSupport.class.cast(extension));
    }
    super.register(extension);
  }

  @Override
  public void register(final CustomContentTypeSupport customContentTypeSupport) {
    throw new IllegalStateException("Own implementations of " + CustomContentTypeSupport.class.getSimpleName()
        + " are not possible");
  }

  @Override
  public void register(final CustomETagSupport customConcurrencyControlSupport) {
    super.register(customConcurrencyControlSupport);
  }

  private void checkSecurity(final ODataRequest request) throws ODataLibraryException, ODataApplicationException {
    final SecurityInceptor securityInceptor = servletHandler.getSecurityInceptor();
    if (securityInceptor == null) {
      return;
    }
    requestContext.getDependencyInjector().injectDependencyValues(securityInceptor);
    final UriInfo uriInfo = new Parser(globalContext.getServiceMetaData().getEdm(), globalContext.getOdata())
        .parseUri(request.getRawODataPath(),
            request.getRawQueryPath(), null, request.getRawBaseUri());
    securityInceptor.authorize(request, uriInfo);
    // prepare the principal for DPI in case of a happened authentication
    final HttpServletRequest httpRequest = requestContext.getDependencyInjector().getDependencyValue(
        HttpServletRequest.class);
    requestContext.getDependencyInjector().registerDependencyMapping(java.security.Principal.class, httpRequest
        .getUserPrincipal());
  }
}