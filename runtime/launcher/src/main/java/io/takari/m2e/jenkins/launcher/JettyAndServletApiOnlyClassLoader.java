package io.takari.m2e.jenkins.launcher;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class JettyAndServletApiOnlyClassLoader extends ClassLoader {
  private static final List<String> packPrefixes = Collections.unmodifiableList(
      Arrays.asList("javax.", "org.eclipse.jetty.", "org.apache.log4j.", "org.apache.commons.logging.", "org.slf4j."));

  private final ClassLoader jettyClassLoader;

  public JettyAndServletApiOnlyClassLoader(ClassLoader parent, ClassLoader jettyClassLoader) {
    super(parent);
    this.jettyClassLoader = jettyClassLoader;
  }

  @Override
  protected Enumeration<URL> findResources(String name) throws IOException {
    if (name.equals("jndi.properties")) {
      return jettyClassLoader.getResources(name);
    }
    for (String prefix : packPrefixes) {
      if (name.startsWith(prefix.replace('.', '/'))) {
        return jettyClassLoader.getResources(name);
      }
    }
    return CollectionUtils.emptyEnumeration();
  }

  @Override
  protected URL findResource(String name) {
    if (name.equals("jndi.properties")) {
      return jettyClassLoader.getResource(name);
    }
    for (String prefix : packPrefixes) {
      if (name.startsWith(prefix.replace('.', '/'))) {
        return jettyClassLoader.getResource(name);
      }
    }
    return null;
  }

  @Override
  protected Class<?> findClass(String name) throws ClassNotFoundException {
    for (String prefix : packPrefixes) {
      if (name.startsWith(prefix)) {
        return jettyClassLoader.loadClass(name);
      }
    }
    throw new ClassNotFoundException(name);
  }
}
