package com.minihttp.core.route.dependency;

import com.minihttp.convert.Types;
import lombok.NonNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link Dependency} 인스턴스 보관소. 타입마다 인스턴스 하나.
 */
public final class DependencyRegistry {
    private final Map<Class<?>, Dependency<?>> dependencies = new ConcurrentHashMap<>();

    private DependencyRegistry() {
    }

    public static DependencyRegistry create() {
        return new DependencyRegistry();
    }

    public void register(@NonNull Dependency<?> dependency) {
        Dependency<?> previous = this.dependencies.putIfAbsent(dependency.getClass(), dependency);

        if (previous != null && previous != dependency) {
            throw new IllegalStateException("Dependency already registered: " + dependency.getClass().getName());
        }
    }

    /**
     * 서버 시작 전에 부른다. 등록된 인스턴스가 없으면 기본 생성자로 만들어 두고,
     * 만들어 내는 값의 타입이 파라미터 타입에 들어갈 수 있는지 검사한다.
     */
    public void prepare(Class<? extends Dependency<?>> dependencyType, Class<?> parameterType, String parameterDescription) {
        this.dependencies.computeIfAbsent(dependencyType, DependencyRegistry::instantiate);

        Optional<Class<?>> maybeProvidedType = DependencyRegistry.providedType(dependencyType);
        if (maybeProvidedType.isEmpty()) {
            return;
        }

        Class<?> providedType = Types.wrap(maybeProvidedType.get());
        if (!Types.wrap(parameterType).isAssignableFrom(providedType)) {
            throw new IllegalStateException(
                    dependencyType.getName() + " provides " + providedType.getName()
                            + " but " + parameterDescription + " expects " + parameterType.getName());
        }
    }

    public Dependency<?> get(Class<?> dependencyType) {
        Dependency<?> dependency = this.dependencies.get(dependencyType);

        if (dependency == null) {
            throw new IllegalStateException("Dependency is not prepared: " + dependencyType.getName());
        }

        return dependency;
    }

    private static Dependency<?> instantiate(Class<?> dependencyType) {
        try {
            Constructor<?> constructor = dependencyType.getDeclaredConstructor();
            constructor.setAccessible(true);
            return (Dependency<?>) constructor.newInstance();

        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException(
                    dependencyType.getName() + " has no no-argument constructor. Register an instance with app.dependency(...)",
                    exception);

        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to create dependency " + dependencyType.getName(), exception);
        }
    }

    /** {@code class CurrentUserId implements Dependency<Long>}에서 Long을 찾는다. 알 수 없으면 empty. */
    private static Optional<Class<?>> providedType(Class<?> dependencyType) {
        for (Class<?> current = dependencyType; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Type interfaceType : current.getGenericInterfaces()) {
                if (interfaceType instanceof ParameterizedType parameterizedType
                        && parameterizedType.getRawType() == Dependency.class) {
                    Type typeArgument = parameterizedType.getActualTypeArguments()[0];
                    return typeArgument instanceof Class<?> || typeArgument instanceof ParameterizedType
                            ? Optional.of(Types.rawType(typeArgument))
                            : Optional.empty();
                }
            }
        }

        return Optional.empty();
    }
}
