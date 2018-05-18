package io.takari.m2e.jenkins.jrebel;

import java.util.Iterator;
import java.util.Map;

import org.kohsuke.stapler.MetaClass;
import org.kohsuke.stapler.WebApp;
import org.kohsuke.stapler.lang.Klass;
import org.zeroturnaround.javarebel.Logger;
import org.zeroturnaround.javarebel.LoggerFactory;

/**
 * Invalidates cached MetaClass instances
 */
public class MetaClassReloader {

  private static final MetaClassReloader instance = new MetaClassReloader();

  private static final Logger log = LoggerFactory.getLogger(MetaClassReloader.class.getName());

  private volatile IReloadableWebapp webapp = null;

  public void init(WebApp wa) {
    if (webapp == null) {
      synchronized (this) {
        if (webapp == null) {
          webapp = (IReloadableWebapp) wa;
        }
      }
    }
  }

  public void reloaded(Class<?> clz) {
    if (webapp == null) {
      return;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    Map<Klass<?>, MetaClass> classMap = (Map) webapp.getClassMap();
    synchronized (classMap) {
      Iterator<Klass<?>> it = classMap.keySet().iterator();
      while (it.hasNext()) {
        if (it.next().clazz == clz) {
          log.infoEcho("Reloading MetaClass {}", clz.getName());
          it.remove();
          break;
        }
      }
    }
  }

  public static MetaClassReloader get() {
    return instance;
  }
}
