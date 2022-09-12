package io.takari.m2e.jenkins.internal.launch;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.ILaunchShortcut;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IEditorPart;

import io.takari.m2e.jenkins.internal.JenkinsPlugin;
import io.takari.m2e.jenkins.plugin.JenkinsPluginProject;

public class LaunchShortcutAction implements ILaunchShortcut {

  @Override
  public void launch(IEditorPart editor, String mode) {
  }

  @Override
  public void launch(ISelection selection, String mode) {

    IProgressMonitor monitor = new NullProgressMonitor();

    List<JenkinsPluginProject> projects = new ArrayList<>();

    if (selection instanceof IStructuredSelection) {
      IStructuredSelection ss = (IStructuredSelection) selection;
      Iterator<Object> it = ss.iterator();
      while (it.hasNext()) {
        Object o = it.next();
        if (o instanceof IAdaptable) {
          IAdaptable a = (IAdaptable) o;
          IProject p = (IProject) a.getAdapter(IProject.class);
          if (p != null) {
            JenkinsPluginProject jp = JenkinsPluginProject.create(p, monitor);
            if (jp != null) {
              projects.add(jp);
            }
          }
        }
      }
    }

    if (!projects.isEmpty()) {
      launch(projects, mode);
    }
  }

  private void launch(List<JenkinsPluginProject> projects, String mode) {

    ILaunchConfiguration launchConfiguration = getLaunchConfiguration(projects, mode);
    if (launchConfiguration == null) {
      return;
    }

    DebugUITools.launch(launchConfiguration, mode);
  }

  private ILaunchConfiguration getLaunchConfiguration(List<JenkinsPluginProject> projects, String mode) {
    ILaunchManager launchManager = DebugPlugin.getDefault().getLaunchManager();
    ILaunchConfigurationType jenkinsType = launchManager
        .getLaunchConfigurationType(JenkinsLaunchConfigurationDelegate.ID);

    Set<String> projectNames = new HashSet<>();
    for (JenkinsPluginProject jp : projects) {
      projectNames.add(jp.getProject().getName());
    }

    try {
      // find existing launches
      JenkinsLaunchConfig conf = new JenkinsLaunchConfig();
      for (ILaunchConfiguration lc : launchManager.getLaunchConfigurations()) {
        if (lc.getType().equals(jenkinsType)) {
          // find first launch that contains all of the selected projects
          conf.initializeFrom(lc);
          if (conf.getPlugins().containsAll(projectNames)) {
            return lc;
          }
        }
      }
    } catch (CoreException e) {
      JenkinsPlugin.error("Error getting existing launch config", e);
      return null;
    }

    // create new
    JenkinsPluginProject firstProject = projects.get(0);
    String newName = launchManager.generateLaunchConfigurationName(firstProject.getProject().getName());
    try {
      ILaunchConfigurationWorkingCopy wc = jenkinsType.newInstance(null, newName);
      JenkinsLaunchConfig config = new JenkinsLaunchConfig();
      config.initializeFrom(wc);

      config.setWorkDir(JenkinsLaunchConfig.getWorkDirFor(firstProject.getProject()));
      config.getPlugins().addAll(projectNames);

      config.setIncludeOptional(true);
      config.setIncludeTestScope(true);
      config.setLatestVersions(true);

      config.performApply(wc);

      return wc.doSave();
    } catch (CoreException e) {
      JenkinsPlugin.error("Error creating launch config", e);
      return null;
    }
  }

}
