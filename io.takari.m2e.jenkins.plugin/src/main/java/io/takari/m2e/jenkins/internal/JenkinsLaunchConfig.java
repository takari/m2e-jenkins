package io.takari.m2e.jenkins.internal;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.databinding.observable.Realm;
import org.eclipse.core.databinding.observable.set.IObservableSet;
import org.eclipse.core.databinding.observable.set.ISetChangeListener;
import org.eclipse.core.databinding.observable.set.SetChangeEvent;
import org.eclipse.core.databinding.observable.set.WritableSet;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;

public class JenkinsLaunchConfig implements Serializable {

  private static final long serialVersionUID = 1L;

  private static final String ID = "io.takari.m2e.jenkins.launch";
  private static final String HOST = ID + ".host";
  private static final String PORT = ID + ".port";
  private static final String CONTEXT = ID + ".context";
  private static final String DISABLECACHES = ID + ".disableCaches";

  private static final String PLUGINS = ID + ".plugins";
  private static final String MAINPLUGIN = ID + ".mainplugin";
  private static final String INCLUDETESTSCOPE = ID + ".includeTestScope";
  private static final String INCLUDEOPTIONAL = ID + ".includeOptional";
  private static final String LATESTVERSIONS = ID + ".latestVersions";

  private static final String DEF_HOST = "0.0.0.0";
  private static final int DEF_PORT = 8081;
  private static final String DEF_CONTEXT = "jenkins";

  private PropertyChangeSupport pchange = new PropertyChangeSupport(this);

  private String host;
  private int port;
  private String context;
  private boolean disableCaches;

  private String mainPlugin;
  private final Set<String> plugins;
  private boolean includeTestScope;
  private boolean includeOptional;
  private boolean latestVersions;
  
  @SuppressWarnings("unchecked")
  public JenkinsLaunchConfig() {
    if (Realm.getDefault() != null) {
      plugins = new WritableSet();
      getPluginsObservable().addSetChangeListener(new ISetChangeListener() {
        @Override
        public void handleSetChange(SetChangeEvent event) {
          pchange.firePropertyChange("plugins", null, plugins);
        }
      });
    } else {
      plugins = new HashSet<String>();
    }
  }

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    pchange.firePropertyChange("host", this.host, this.host = host);
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    pchange.firePropertyChange("port", this.port, this.port = port);
  }

  public String getContext() {
    return context;
  }

  public String getMainPlugin() {
    return mainPlugin;
  }

  public void setMainPlugin(String mainPlugin) {
    pchange.firePropertyChange("mainPlugin", this.mainPlugin, this.mainPlugin = mainPlugin);
  }

  public void setContext(String context) {
    pchange.firePropertyChange("context", this.context, this.context = context);
  }

  public boolean isDisableCaches() {
    return disableCaches;
  }

  public void setDisableCaches(boolean disableCaches) {
    pchange.firePropertyChange("disableCaches", this.disableCaches, this.disableCaches = disableCaches);
  }

  public Set<String> getPlugins() {
    return plugins;
  }

  public IObservableSet getPluginsObservable() {
    return plugins instanceof IObservableSet ? (IObservableSet) plugins : null;
  }

  public boolean isIncludeTestScope() {
    return includeTestScope;
  }

  public void setIncludeTestScope(boolean includeTestScope) {
    pchange.firePropertyChange("includeTestScope", this.includeTestScope, this.includeTestScope = includeTestScope);
  }

  public boolean isIncludeOptional() {
    return includeOptional;
  }

  public void setIncludeOptional(boolean includeOptional) {
    pchange.firePropertyChange("includeOptional", this.includeOptional, this.includeOptional = includeOptional);
  }

  public boolean isLatestVersions() {
    return latestVersions;
  }

  public void setLatestVersions(boolean latestVersions) {
    pchange.firePropertyChange("latestVersions", this.latestVersions, this.latestVersions = latestVersions);
  }

  public void setDefaults(ILaunchConfigurationWorkingCopy config) {
    config.setAttribute(HOST, DEF_HOST);
    config.setAttribute(PORT, DEF_PORT);
    config.setAttribute(CONTEXT, DEF_CONTEXT);
  }
  
  public void initializeFrom(ILaunchConfiguration config) {
    try {
      setHost(config.getAttribute(HOST, DEF_HOST));
      setPort(config.getAttribute(PORT, DEF_PORT));
      setContext(config.getAttribute(CONTEXT, DEF_CONTEXT));
      setDisableCaches(config.getAttribute(DISABLECACHES, false));
      setMainPlugin(config.getAttribute(MAINPLUGIN, ""));
      setIncludeTestScope(config.getAttribute(INCLUDETESTSCOPE, false));
      setIncludeOptional(config.getAttribute(INCLUDEOPTIONAL, false));
      setLatestVersions(config.getAttribute(LATESTVERSIONS, false));
      getPlugins().clear();
      getPlugins().addAll(config.getAttribute(PLUGINS, Collections.<String> emptyList()));
    } catch (CoreException e) {
      throw new IllegalStateException(e);
    }
  }
  
  public void performApply(ILaunchConfigurationWorkingCopy config) {
    config.setAttribute(HOST, getHost());
    config.setAttribute(PORT, getPort());
    config.setAttribute(CONTEXT, getContext());
    config.setAttribute(DISABLECACHES, isDisableCaches());

    config.setAttribute(MAINPLUGIN, getMainPlugin());
    setAttribute(config, PLUGINS, getPlugins());
    config.setAttribute(INCLUDETESTSCOPE, isIncludeTestScope());
    config.setAttribute(INCLUDEOPTIONAL, isIncludeOptional());
    config.setAttribute(LATESTVERSIONS, isLatestVersions());
  }

  private static void setAttribute(ILaunchConfigurationWorkingCopy config, String name, Set<String> set) {
    List<String> data = new ArrayList<>(set);
    Collections.sort(data);
    config.setAttribute(name, data);
  }

  public void addPropertyChangeListener(PropertyChangeListener listener) {
    pchange.addPropertyChangeListener(listener);
  }

  public void removePropertyChangeListener(PropertyChangeListener listener) {
    pchange.removePropertyChangeListener(listener);
  }

}
