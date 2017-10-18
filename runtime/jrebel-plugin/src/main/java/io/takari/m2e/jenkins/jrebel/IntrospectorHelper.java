package io.takari.m2e.jenkins.jrebel;

import java.lang.reflect.Method;

public class IntrospectorHelper {

  public static IntrospectorExt getIntrospector() {

    Object uberspectObj = null;
    try {
      Class<?> introspector = Class.forName("org.apache.commons.jexl.util.Introspector");
      Method getUberspect = introspector.getDeclaredMethod("getUberspect");
      uberspectObj = getUberspect.invoke(null);
    } catch (Throwable t) {
    }

    if (uberspectObj != null) {
      UberspectExt uberspect = null;
      if (uberspectObj instanceof UberspectExt) {
        uberspect = (UberspectExt) uberspectObj;
      }
      if (uberspect != null) {
        Object introspectorObj = uberspect.getIntrospector();
        if (introspectorObj instanceof IntrospectorExt) {
          return (IntrospectorExt) introspectorObj;
        }
      }
    }
    return null;
  }

}
