package moe.ono.service;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Resolves the service across QQ versions without caching an account's runtime. */
final class MobileQQServiceResolver {
    private MobileQQServiceResolver() {}

    static Object resolve(Object runtime, Class<?> serviceClass) {
        ReflectiveOperationException failure = null;
        // QQ 9.2.70+ (including 9.3.75) exposes the service through this accessor.
        // getMethods also covers an accessor inherited from AppInterface.
        for (Method method : runtime.getClass().getMethods()) {
            if (!method.getName().equals("getMobileQQService")
                    || method.getParameterTypes().length != 0
                    || Modifier.isStatic(method.getModifiers())
                    || !serviceClass.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            try {
                method.setAccessible(true);
                Object value = method.invoke(runtime);
                if (serviceClass.isInstance(value)) {
                    return value;
                }
            } catch (ReflectiveOperationException e) {
                failure = e;
            }
        }
        // Older QQ stores the service directly. A null field must not stop lookup.
        for (Class<?> type = runtime.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())
                        || !serviceClass.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(runtime);
                    if (serviceClass.isInstance(value)) {
                        return value;
                    }
                } catch (ReflectiveOperationException e) {
                    failure = e;
                }
            }
        }
        throw new IllegalStateException("初始化失败 -> mqqService ("
                + runtime.getClass().getName() + ")", failure);
    }
}
