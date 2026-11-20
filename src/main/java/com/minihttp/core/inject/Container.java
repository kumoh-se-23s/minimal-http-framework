package com.minihttp.core.inject;

import com.minihttp.convert.Types;
import com.minihttp.json.JsonWriter;
import com.minihttp.log.MiniHttpLogging;
import lombok.NonNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class Container implements AutoCloseable {
    private static final System.Logger LOGGER = MiniHttpLogging.logger(MiniHttpLogging.SERVER);
    private static final String ARROW = " → ";

    private final Map<Class<?>, Object> providedInstances = new HashMap<>();
    private final Map<Class<?>, Class<?>> bindings = new HashMap<>();
    private final Map<Class<?>, Object> createdInstances = new HashMap<>();
    private final List<Object> creationOrder = new ArrayList<>();
    private final Set<Class<?>> creatingTypes = new LinkedHashSet<>();
    private boolean closed = false;

    private Container() {
    }

    public static Container create() {
        return new Container();
    }

    public synchronized <T> void bind(@NonNull Class<T> type, @NonNull Class<? extends T> implementation) {
        if (!type.isAssignableFrom(implementation)) {
            throw new IllegalArgumentException(implementation.getName() + " is not a " + type.getName());
        }

        if (Container.isAbstract(implementation)) {
            throw new IllegalArgumentException(
                    "Cannot bind " + type.getName() + " to " + implementation.getName() + " because it is not a concrete class");
        }

        this.validateNotRegistered(type);
        this.bindings.put(type, implementation);
    }

    public synchronized <T> void provide(@NonNull Class<T> type, @NonNull T instance) {
        Class<?> key = Types.wrap(type);

        if (!key.isInstance(instance)) {
            throw new IllegalArgumentException(instance.getClass().getName() + " is not a " + type.getName());
        }

        this.validateNotRegistered(key);
        this.providedInstances.put(key, instance);
    }

    public synchronized <T> T get(@NonNull Class<T> type) {
        if (this.closed) {
            throw new IllegalStateException("Container is closed");
        }

        @SuppressWarnings("unchecked")
        T instance = (T) this.resolve(type);
        return instance;
    }

    @Override
    public synchronized void close() {
        if (this.closed) {
            return;
        }

        this.closed = true;

        for (int instanceIndex = this.creationOrder.size() - 1; instanceIndex >= 0; instanceIndex--) {
            if (!(this.creationOrder.get(instanceIndex) instanceof AutoCloseable closeable)) {
                continue;
            }

            try {
                closeable.close();

            } catch (Exception exception) {
                LOGGER.log(System.Logger.Level.WARNING, "Failed to close " + closeable.getClass().getName(), exception);
            }
        }
    }

    private Object resolve(Class<?> type) {
        Class<?> key = Types.wrap(type);
        Object providedInstance = this.providedInstances.get(key);

        if (providedInstance != null) {
            return providedInstance;
        }

        Class<?> implementation = this.bindings.getOrDefault(key, key);
        Object createdInstance = this.createdInstances.get(implementation);

        if (createdInstance != null) {
            return createdInstance;
        }

        return this.instantiate(implementation);
    }

    private Object instantiate(Class<?> type) {
        if (this.creatingTypes.contains(type)) {
            throw new IllegalStateException("Circular dependency: " + this.chainTo(type));
        }

        this.validateCreatable(type);
        Constructor<?> constructor = Container.selectConstructor(type);

        this.creatingTypes.add(type);
        try {
            Object[] arguments = Arrays.stream(constructor.getParameterTypes())
                                       .map(this::resolve)
                                       .toArray();
            Object instance = Container.invoke(constructor, arguments);

            this.createdInstances.put(type, instance);
            this.creationOrder.add(instance);
            return instance;

        } finally {
            this.creatingTypes.remove(type);
        }
    }

    private void validateNotRegistered(Class<?> type) {
        if (this.providedInstances.containsKey(type) || this.bindings.containsKey(type)) {
            throw new IllegalStateException("Already registered: " + type.getName());
        }
    }

    private void validateCreatable(Class<?> type) {
        if (type.isInterface() || Container.isAbstract(type)) {
            throw new IllegalStateException(
                    "No implementation for " + type.getName() + this.requiredBy()
                            + ". Register one with bind(" + type.getSimpleName() + ".class, ...) or provide(...)");
        }

        if (type.isPrimitive() || type.isArray() || type.isEnum() || JsonWriter.isJdkType(type)) {
            throw new IllegalStateException(
                    "Cannot create " + type.getName() + this.requiredBy() + ". Register a value with provide(...)");
        }

        if (type.isMemberClass() && !Modifier.isStatic(type.getModifiers())) {
            throw new IllegalStateException("Cannot create inner class " + type.getName() + this.requiredBy() + ". Make it static");
        }
    }

    private String requiredBy() {
        if (this.creatingTypes.isEmpty()) {
            return "";
        }

        return " (required by " + this.creatingTypes.stream()
                                                   .map(Class::getName)
                                                   .collect(Collectors.joining(ARROW)) + ")";
    }

    private String chainTo(Class<?> type) {
        List<Class<?>> chain = new ArrayList<>(this.creatingTypes);
        return Stream.concat(chain.subList(chain.indexOf(type), chain.size()).stream(), Stream.of(type))
                     .map(Class::getName)
                     .collect(Collectors.joining(ARROW));
    }

    private static Constructor<?> selectConstructor(Class<?> type) {
        Constructor<?>[] declaredConstructors = type.getDeclaredConstructors();

        if (declaredConstructors.length == 1) {
            declaredConstructors[0].setAccessible(true);
            return declaredConstructors[0];
        }

        List<Constructor<?>> publicConstructors = Arrays.stream(declaredConstructors)
                                                        .filter(constructor -> Modifier.isPublic(constructor.getModifiers()))
                                                        .toList();

        if (publicConstructors.size() == 1) {
            publicConstructors.getFirst().setAccessible(true);
            return publicConstructors.getFirst();
        }

        String candidates = Arrays.stream(declaredConstructors)
                                  .map(Constructor::toGenericString)
                                  .collect(Collectors.joining(", "));
        throw new IllegalStateException(
                "Cannot choose a constructor of " + type.getName() + ": keep a single constructor or exactly one public constructor. "
                        + "Candidates: " + candidates);
    }

    private static Object invoke(Constructor<?> constructor, Object[] arguments) {
        try {
            return constructor.newInstance(arguments);

        } catch (InvocationTargetException exception) {
            throw new IllegalStateException(
                    "Failed to create " + constructor.getDeclaringClass().getName(), exception.getCause());

        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to create " + constructor.getDeclaringClass().getName(), exception);
        }
    }

    private static boolean isAbstract(Class<?> type) {
        return type.isInterface() || Modifier.isAbstract(type.getModifiers()) && !type.isPrimitive() && !type.isArray();
    }
}
