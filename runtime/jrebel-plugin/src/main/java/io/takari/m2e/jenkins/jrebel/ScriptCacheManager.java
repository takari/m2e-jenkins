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

  private final Map<String, Object> keys = new ConcurrentHashMap<>();

  public synchronized void clearStale(LoadingCache<String, ?> cache, Object nameObj) {
    if (nameObj == null) {
      return;
    }
    String name = nameObj.toString();
    Object key = keys.get(name);
    if (key != null) {
      Set<String> modified = MonitoredResourceManager.modified(key);
      if (!modified.isEmpty()) {

        for (String mod : modified) {
          log.infoEcho("Reloading {}", mod);
        }

        cache.invalidate(name);
        keys.remove(name);
      }
    }
  }

  public void beginLoad(String name) {
    Object key;
    synchronized (this) {
      key = keys.get(name);
      if (key == null) {
        keys.put(name, key = new Object());
      }
    }
    MonitoredResourceManager.beginConf(key);
  }

  public void endLoad() {
    MonitoredResourceManager.endConf();
  }

  public void registerResource(URL url) {
    MonitoredResourceManager.registerConf(url);
  }

  public static ScriptCacheManager get(AbstractTearOff<?, ?, ?> t) {
    if (t instanceof IManageableScriptLoader) {
      return ((IManageableScriptLoader) t).__getCacheManager();
    }
    throw new IllegalStateException("Not manageable");
  }
}