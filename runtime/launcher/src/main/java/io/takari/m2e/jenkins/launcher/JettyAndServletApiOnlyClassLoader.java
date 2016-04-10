package io.takari.m2e.jenkins.launcher;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;

public class JettyAndServletApiOnlyClassLoader extends ClassLoader {
  private final ClassLoader jettyClassLoader;

  public JettyAndServletApiOnlyClassLoader(ClassLoader parent, ClassLoader jettyClassLoader) {
    super(parent);
    this.jettyClassLoader = jettyClassLoader;
  }

  @Override
  public Enumeration<URL> getResources(String name) throws IOException {
    if (name.equals("jndi.properties")) {
      return jettyClassLoader.getResources(name);
    }
    return CollectionUtils.emptyEnumeration();
  }

  @Override
  protected Class<?> findClass(String name) throws ClassNotFoundException {
    if (name.startsWith("javax.") || name.startsWith("org.eclipse.jetty.") //
        || name.startsWith("org.apache.log4j") //
        || name.startsWith("org.apache.commons.logging") //
        || name.startsWith("org.slf4j")) //
      return jettyClassLoader.loadClass(name);
    else
      throw new ClassNotFoundException(name);
  }
}
