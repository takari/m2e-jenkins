package io.takari.m2e.jenkins.jrebel;

import java.net.URL;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.kohsuke.stapler.AbstractTearOff;
import org.zeroturnaround.javarebel.Logger;
import org.zeroturnaround.javarebel.LoggerFactory;
import org.zeroturnaround.javarebel.integration.monitor.MonitoredResourceManager;

import com.google.common.cache.LoadingCache;

public class ScriptCacheManager {

  private static final Logger log = LoggerFactory.getLogger(ScriptCacheManager.class.getName());

  private static final ThreadLocal<SCMChain> current = new ThreadLocal<>();

  private final Map<String, Object> keys = new ConcurrentHashMap<>();

  private IManageableScriptLoader loader;

  public ScriptCacheManager(IManageableScriptLoader loader) {
    this.loader = loader;
  }

  public synchronized void clearStale(LoadingCache<String, ?> cache, Object nameObj) {
    if (nameObj == null) {
      return;
    }
    String name = nameObj.toString();
    Object key = keys.get(name);
    if (key != null) {
      Set<String> modified = MonitoredResourceManager.modified(key);
      if (!modified.isEmpty()) {
        cache.invalidate(name);
        keys.remove(name);
      }
    }
  }

  private Object getKey(String name) {
    Object key;
    synchronized (this) {
      key = keys.get(name);
      if (key == null) {
        keys.put(name, key = new Object());
      }
    }
    return key;
  }

  public static void beginLoad(AbstractTearOff<?, ?, ?> t, String name) {
    current.set(new SCMChain(current.get(), ScriptCacheManager.get(t), name));
  }

  public static void endLoad() {
    current.set(current.get().parent);
  }

  public static void registerResource(URL url) {
    if (url == null) {
      return;
    }

    SCMChain scm = current.get();
    while (scm != null) {
      IManageableScriptLoader loader = scm.mgr.loader;
      Class<?> tearOffClass = loader.getClass();
      Class<?> itemClass = loader.getOwner().klass.toJavaClass();

      String path = url.getPath().replace('\\', '/');
      int bang = path.indexOf('!');
      if (bang != -1) {
        path = path.substring(bang + 1);
      }

      String shortPath;
      String searchString;
      if (scm.name.startsWith("/")) {
        // absolute name path, use fully qualified path
        searchString = scm.name;
        shortPath = searchString;
      } else {
        searchString = itemClass.getName().replace('.', '/').replace('$', '/');
        shortPath = itemClass.getSimpleName();
      }
      int idx = path.indexOf(searchString);
      String shortUrl = idx == -1 ? path : shortPath + path.substring(idx + searchString.length());
      log.infoEcho("[{}] {}:{} -> {}", tearOffClass.getSimpleName(), itemClass.getName(), scm.name, shortUrl);

      MonitoredResourceManager.beginConf(scm.mgr.getKey(scm.name));
      MonitoredResourceManager.registerConf(url);
      MonitoredResourceManager.endConf();
      scm = scm.parent;
    }
  }

  private static ScriptCacheManager get(AbstractTearOff<?, ?, ?> t) {
    if (t instanceof IManageableScriptLoader) {
      return ((IManageableScriptLoader) t).__getCacheManager();
    }
    throw new IllegalStateException("Not manageable");
  }

  private static final class SCMChain {
    final SCMChain parent;
    final ScriptCacheManager mgr;
    final String name;

    public SCMChain(SCMChain parent, ScriptCacheManager mgr, String name) {
      this.parent = parent;
      this.mgr = mgr;
      this.name = name;
    }
  }
}
