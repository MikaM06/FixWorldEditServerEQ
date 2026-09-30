package fr.earthquest.fixworldedit;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

final class Reflect {
    private Reflect() {
    }

    static Method findStatic(Class<?> owner, Class<?> returnType, Class<?>... parameters) {
        for (Method method : owner.getMethods()) {
            if (!Modifier.isStatic(method.getModifiers()) || !returns(method, returnType)) {
                continue;
            }
            if (matches(method.getParameterTypes(), parameters)) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    static Method find(Class<?> owner, Class<?> returnType, Class<?>... parameters) {
        for (Method method : owner.getMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || !returns(method, returnType)) {
                continue;
            }
            if (matches(method.getParameterTypes(), parameters)) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    static Method named(Class<?> owner, String[] names, int parameters) {
        for (String name : names) {
            for (Method method : owner.getMethods()) {
                if (method.getName().equals(name) && method.getParameterTypes().length == parameters) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        return null;
    }

    private static boolean returns(Method method, Class<?> returnType) {
        return returnType.isPrimitive()
                ? returnType.equals(method.getReturnType())
                : returnType.isAssignableFrom(method.getReturnType());
    }

    private static boolean matches(Class<?>[] actual, Class<?>[] expected) {
        if (actual.length != expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (!actual[i].equals(expected[i])) {
                return false;
            }
        }
        return true;
    }
}
