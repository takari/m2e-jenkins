package io.takari.m2e.jenkins.jrebel;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

public class MethodHandleFactory {
  public static MethodHandle get(Method method) {
    try {
        method.setAccessible(true);
        return MethodHandles.lookup().unreflect(method);
    } catch (IllegalAccessException e) {
        throw (Error)new IllegalAccessError("Protected method: "+method).initCause(e);
    }
}
}
