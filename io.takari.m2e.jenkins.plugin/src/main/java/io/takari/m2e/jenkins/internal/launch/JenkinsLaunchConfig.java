package io.takari.m2e.jenkins.internal.launch;

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
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;

public class JenkinsLaunchConfig implements Serializable {

  private static final long serialVersionUID = 1L;

  private static final String ID = "io.takari.m2e.jenkins.launch";
  private static final String WORKDIR = ID + ".workDir";
  private static final String PORT = ID + ".port";
  private static final String CONTEXT = ID + ".context";
  private static final String DISABLECACHES = ID + ".disableCaches";

  private static final String PLUGINS = ID + ".plugins";
  @Deprecated
  private static final String MAINPLUGIN = ID + ".mainplugin";
  private static final String INCLUDETESTSCOPE = ID + ".includeTestScope";
  private static final String INCLUDEOPTIONAL = ID + ".includeOptional";
  private static final String LATESTVERSIONS = ID + ".latestVersions";
  private static final String SKIPUPDATEWIZARD = ID + ".skipUpdateWizard";

  private static final int DEF_PORT = 8080;
  private static final String DEF_CONTEXT = "jenkins";

  private PropertyChangeSupport pchange = new PropertyChangeSupport(this);

  private String workDir;

  private int port;
  private String context;
  private boolean disableCaches;
  private boolean skipUpdateWizard;

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

  public String getWorkDir() {
    return workDir;
  }

  public void setWorkDir(String workDir) {
    pchange.firePropertyChange("workDir", this.workDir, this.workDir = workDir);
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

  public void setContext(String context) {
    pchange.firePropertyChange("context", this.context, this.context = context);
  }

  public boolean isDisableCaches() {
    return disableCaches;
  }

  public void setDisableCaches(boolean disableCaches) {
    pchange.firePropertyChange("disableCaches", this.disableCaches, this.disableCaches = disableCaches);
  }

  public boolean isSkipUpdateWizard() {
    return skipUpdateWizard;
  }

  public void setSkipUpdateWizard(boolean skipUpdateWizard) {
    pchange.firePropertyChange("skipUpdateWizard", this.skipUpdateWizard, this.skipUpdateWizard = skipUpdateWizard);
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
    config.setAttribute(PORT, DEF_PORT);
    config.setAttribute(CONTEXT, DEF_CONTEXT);
    config.setAttribute(INCLUDETESTSCOPE, true);
    config.setAttribute(INCLUDEOPTIONAL, true);
    config.setAttribute(LATESTVERSIONS, true);
    config.setAttribute(SKIPUPDATEWIZARD, true);
  }
  
  public void initializeFrom(ILaunchConfiguration config) {
    try {
      setWorkDir(config.getAttribute(WORKDIR, ""));
      setPort(config.getAttribute(PORT, DEF_PORT));
      setContext(config.getAttribute(CONTEXT, DEF_CONTEXT));
      setDisableCaches(config.getAttribute(DISABLECACHES, false));
      setIncludeTestScope(config.getAttribute(INCLUDETESTSCOPE, true));
      setIncludeOptional(config.getAttribute(INCLUDEOPTIONAL, true));
      setLatestVersions(config.getAttribute(LATESTVERSIONS, true));
      setSkipUpdateWizard(config.getAttribute(SKIPUPDATEWIZARD, true));
      getPlugins().clear();
      getPlugins().addAll(config.getAttribute(PLUGINS, Collections.<String> emptyList()));
    } catch (CoreException e) {
      throw new IllegalStateException(e);
    }
  }
  
  public void performApply(ILaunchConfigurationWorkingCopy config) {
    config.setAttribute(WORKDIR, getWorkDir());
    config.setAttribute(PORT, getPort());
    config.setAttribute(CONTEXT, getContext());
    config.setAttribute(DISABLECACHES, isDisableCaches());

    setAttribute(config, PLUGINS, getPlugins());
    config.setAttribute(INCLUDETESTSCOPE, isIncludeTestScope());
    config.setAttribute(INCLUDEOPTIONAL, isIncludeOptional());
    config.setAttribute(LATESTVERSIONS, isLatestVersions());
    config.setAttribute(SKIPUPDATEWIZARD, isSkipUpdateWizard());
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

  public static boolean needsMigration(ILaunchConfiguration candidate) throws CoreException {
    return candidate.getAttribute(MAINPLUGIN, (String) null) != null;
  }

  public static String getWorkDirFor(IProject project) {
    return LaunchingUtils.generateWorkspaceLocationVariableExpression(project.getFullPath());
  }

  public static void migrate(ILaunchConfiguration candidate) throws CoreException {
    ILaunchConfigurationWorkingCopy wc = candidate.getWorkingCopy();
    String mp = wc.getAttribute(MAINPLUGIN, (String) null);

    if (mp != null) {
      IWorkspaceRoot ws = ResourcesPlugin.getWorkspace().getRoot();
      if (ws.getFullPath().isValidSegment(mp)) {
        IProject project = ws.getProject(mp);
        wc.setAttribute(WORKDIR, getWorkDirFor(project));
      } else {
        wc.setAttribute(WORKDIR, "");
      }
      wc.setAttribute(MAINPLUGIN, (String) null);
    }

    wc.doSave();
  }

}
