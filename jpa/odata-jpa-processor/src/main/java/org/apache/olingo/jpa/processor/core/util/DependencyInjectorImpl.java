package org.apache.olingo.jpa.processor.core.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.jpa.cdi.Inject;
import org.apache.olingo.jpa.processor.ModifiableDependencyInjector;
import org.apache.olingo.jpa.processor.core.exception.ODataJPAProcessorException;
import org.apache.olingo.server.api.ODataApplicationException;

/**
 * Helper class to realize a limited support for dependency injection. Supported
 * are:
 * <ul>
 * <li>org.apache.olingo.jpa.cdi.Inject (javax.inject.Inject): for fields</li>
 * <li>org.apache.olingo.jpa.cdi.Inject: for method parameters</li>
 * </ul>
 *
 * @author Ralf Zozmann
 *
 */
@SuppressWarnings("unchecked")
public final class DependencyInjectorImpl implements ModifiableDependencyInjector {

  private static final String JAVAX_INJECT_INJECT_CLASSNAME = "javax.inject.Inject";
  private static final Collection<Class<?>> FORBIDDEN_TYPES = new LinkedList<>();
  private static Class<? extends Annotation> injectAnnotation;

  static {
    FORBIDDEN_TYPES.add(Boolean.class);
    FORBIDDEN_TYPES.add(Character.class);
    FORBIDDEN_TYPES.add(Byte.class);
    FORBIDDEN_TYPES.add(Short.class);
    FORBIDDEN_TYPES.add(Integer.class);
    FORBIDDEN_TYPES.add(Long.class);
    FORBIDDEN_TYPES.add(Float.class);
    FORBIDDEN_TYPES.add(Double.class);
    FORBIDDEN_TYPES.add(Void.class);
    try {
      injectAnnotation = (Class<? extends Annotation>) Class.forName(JAVAX_INJECT_INJECT_CLASSNAME);
    } catch (final ClassNotFoundException e) {
      injectAnnotation = null;
    }
  }

  private static class InjectionOccurrence {

    private final Field field;
    private final Object matchingObject;

    InjectionOccurrence(final Field field, final Object matchingObject) {
      super();
      this.field = field;
      this.matchingObject = matchingObject;
    }
  }

  private static class ValueReference<T> {
    private final T value;

    private ValueReference(final T value) {
      this.value = value;
    }

    public T getValueObject() {
      return value;
    }
  }

  private final Map<Class<?>, ValueReference<?>> valueMapping = new HashMap<>();
  private final DependencyInjectorImpl parent;

  /**
   * Create a new global injector
   */
  public DependencyInjectorImpl() {
    this(null);
  }

  /**
   *
   * @param parent The parent injector
   */
  public DependencyInjectorImpl(final DependencyInjectorImpl parent) {
    this.parent = parent;
  }


  @Override
  public <T> T getDependencyValue(final Class<T> type) {
    final ValueReference<T> vr = getValueReference(type, true);
    if (vr != null) {
      return vr.getValueObject();
    }
    return null;
  }

  protected <T> ValueReference<T> getValueReference(final Class<T> type, final boolean callParent) {
    final ValueReference<T> vr = (ValueReference<T>) valueMapping.get(type);
    if (vr != null) {
      return vr;
    }
    if (callParent && parent != null) {
      return parent.getValueReference(type, callParent);
    }
    return null;
  }

  /**
   *
   * @param type The key type to remove value for.
   */
  @Override
  public final void removeDependencyValue(final Class<?> type) {
    final ValueReference<?> entry = valueMapping.remove(type);
    if (parent != null && entry == null) {
      // if value reference could not be removed try to do the same on parent...
      parent.removeDependencyValue(type);
    }
  }

  /**
   * Register multiple dependency mappings consisting of type and value.
   *
   * @see #registerDependencyMapping(Class, Object)
   */
  @Override
  public void registerDependencyMappings(final TypedParameter... dependencies) {
    if (dependencies == null) {
      return;
    }
    for (final TypedParameter dm : dependencies) {
      registerDependencyMapping(dm.getType(), dm.getValue());
    }
  }

  /**
   * Register a value to inject into {@link #injectDependencyValues(Object) targets}.
   *
   * @param type
   * The type object used to register. The type must match the (field)
   * type of injection.
   * @param value
   * The value to inject.
   */
  @Override
  public void registerDependencyMapping(final Class<?> type, final Object value) {
    // check for already registered in this instance
    final ValueReference<?> vR = getValueReference(type, false);
    if (vR != null) {
      // already register... same value is allowed
      if (vR.getValueObject() == value) {
        return;
      }
      throw new IllegalArgumentException("Type already registered: " + type.getName());
    }
    if (value != null && !type.isInstance(value)) {
      throw new IllegalArgumentException("Value doesn't match type");
    }
    if (Collection.class.isInstance(value)) {
      throw new IllegalArgumentException("Collection's are not supported for injection");
    }
    if (type.isPrimitive()) {
      throw new IllegalArgumentException("Primitive types are not supported for injection");
    }
    if (FORBIDDEN_TYPES.contains(type)) {
      throw new IllegalArgumentException("Type is not allowed for injection");
    }
    valueMapping.put(type, new ValueReference<Object>(value));
  }

  /**
   * Cleanup dependency injector
   */
  public void dispose() {
    valueMapping.clear();
  }

  @Override
  public void injectDependencyValues(final Object target) throws ODataApplicationException {
    if (target == null) {
      return;
    }
    internalInjectDependencyValues(target, new HashSet<>());
  }

  void internalInjectDependencyValues(final Object target, final Set<Field> alreadyHandledFields)
      throws ODataApplicationException {
    // own values
    final Collection<InjectionOccurrence> occurrences = findAnnotatedFields(target.getClass());
    for (final InjectionOccurrence o : occurrences) {
      if (alreadyHandledFields.contains(o.field)) {
        continue;
      }
      if (o.matchingObject != null) {
        // if no DI has an value then the field is affected multiple times from 'null' setting
        alreadyHandledFields.add(o.field);
      }
      final boolean accessible = o.field.isAccessible();
      if (!accessible) {
        o.field.setAccessible(true);
      }
      try {
        o.field.set(target, o.matchingObject);
      } catch (IllegalArgumentException | IllegalAccessException e) {
        throw new ODataJPAProcessorException(ODataJPAProcessorException.MessageKeys.QUERY_PREPARATION_ERROR,
            HttpStatusCode.INTERNAL_SERVER_ERROR, e);
      } finally {
        // reset
        if (!accessible) {
          o.field.setAccessible(false);
        }
      }
    }
    // parent values
    if (parent != null) {
      parent.internalInjectDependencyValues(target, alreadyHandledFields);
    }
  }

  /**
   *
   * @param field
   *            The field to check
   * @return TRUE if given field has a annotation assumed to be a injection marker
   */
  private static boolean isAnnotatedForInjection(final Field field) {
    // olingo-jpa-processor specific 'Inject' annotation
    if (field.isAnnotationPresent(Inject.class)) {
      return true;
    }
    // support for javax.inject.Inject, avoiding direct dependencies
    if (injectAnnotation != null && field.isAnnotationPresent(injectAnnotation)) {
      return true;
    }
    return false;
  }

  private Collection<InjectionOccurrence> findAnnotatedFields(final Class<?> clazz) {
    if (Object.class.equals(clazz)) {
      // don't inspect Object class
      return Collections.emptyList();
    }
    final Field[] clazzFields = clazz.getDeclaredFields();
    final Collection<InjectionOccurrence> occurrences = new LinkedList<>();
    Object value;
    for (final Field field : clazzFields) {
      if (isAnnotatedForInjection(field)) {
        value = findMatchingValue(field);
        occurrences.add(new InjectionOccurrence(field, value));
      }
    }
    final Class<?> clazzSuper = clazz.getSuperclass();
    if (clazzSuper != null) {
      final Collection<InjectionOccurrence> superOccurrences = findAnnotatedFields(clazzSuper);
      occurrences.addAll(superOccurrences);
    }
    return occurrences;
  }

  private Object findMatchingValue(final Field field) {
    for (final Entry<Class<?>, ValueReference<?>> entry : valueMapping.entrySet()) {
      if (isMatchingType(entry.getKey(), field.getType())) {
        return entry.getValue().getValueObject();
      }
    }
    return null;
  }

  /**
   *
   * @param requestedType
   *            The type from {@link #registerDependencyMapping(Class, Object)}
   * @param actualType
   *            The type of {@link Field#getType()} or
   *            {@link java.lang.reflect.Parameter#getType()}
   * @return TRUE if types are matching
   */
  private boolean isMatchingType(final Class<?> requestedType, final Class<?> actualType) {
    return actualType.isAssignableFrom(requestedType);
  }
}
