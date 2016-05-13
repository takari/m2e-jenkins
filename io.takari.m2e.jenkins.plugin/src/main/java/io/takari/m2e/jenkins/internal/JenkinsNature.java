package io.takari.m2e.jenkins.internal;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IProjectNature;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.jboss.tools.maven.apt.MavenJdtAptPlugin;
import org.jboss.tools.maven.apt.preferences.AnnotationProcessingMode;
import org.jboss.tools.maven.apt.preferences.IPreferencesManager;
import org.osgi.framework.Bundle;
import org.osgi.framework.Version;

import io.takari.m2e.jenkins.JenkinsPlugin;

public class JenkinsNature implements IProjectNature {

  public static final String ID = JenkinsPlugin.ID + ".nature";

  private IProject project;

  @Override
  public void configure() throws CoreException {

    if (!checkTakariAPT(new NullProgressMonitor())) {

      // enable m2e-apt on hpi projects
      IPreferencesManager pmgr = MavenJdtAptPlugin.getDefault().getPreferencesManager();
      if (pmgr.getAnnotationProcessorMode(project) == AnnotationProcessingMode.disabled) {
        pmgr.setAnnotationProcessorMode(project, AnnotationProcessingMode.jdt_apt);
        JenkinsPlugin.info("Enabling m2e-apt for " + project.getName());
      } else {
        JenkinsPlugin.info("m2e-apt already enabled for " + project.getName());
      }

    } else {
      JenkinsPlugin.info("Not enabling m2e-apt for " + project.getName() + " due to takari-lifecycle apt presence");
    }

    // add jenkins builder
    JenkinsBuilder.add(project);
  }

  private boolean checkTakariAPT(NullProgressMonitor monitor) throws CoreException {

    // takari lifecycle manages annotation processing itself
    Bundle takariJdt = Platform.getBundle("io.takari.m2e.jdt.core");
    if (takariJdt != null && takariJdt.getVersion().compareTo(new Version("0.1.0.201507181630")) >= 0) {
      JenkinsPluginProject jp = JenkinsPluginProject.create(project, monitor);
      String proc = jp.getMojoParameter("io.takari.maven.plugins", "takari-lifecycle-plugin", "compile", "proc",
          String.class, monitor);

      if (proc != null && !proc.equals("none")) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void deconfigure() throws CoreException {
    IPreferencesManager pmgr = MavenJdtAptPlugin.getDefault().getPreferencesManager();
    pmgr.setAnnotationProcessorMode(project, AnnotationProcessingMode.disabled);

    // remove jenkins builder
    JenkinsBuilder.remove(project);
  }

  @Override
  public IProject getProject() {
    return project;
  }

  @Override
  public void setProject(IProject project) {
    this.project = project;
  }

  public static void enable(IProject project, IProgressMonitor monitor) throws CoreException {
    IProjectDescription desc = project.getDescription();
    Set<String> naturesSet = new LinkedHashSet<String>();
    Collections.addAll(naturesSet, desc.getNatureIds());
    if (!naturesSet.contains(ID)) {
      naturesSet.add(ID);
      desc.setNatureIds(naturesSet.toArray(new String[naturesSet.size()]));
      project.setDescription(desc, monitor);
    }
  }

  public static void disable(IProject project, IProgressMonitor monitor) throws CoreException {
    IProjectDescription desc = project.getDescription();
    Set<String> naturesSet = new LinkedHashSet<String>();
    Collections.addAll(naturesSet, desc.getNatureIds());
    if (naturesSet.contains(ID)) {
      naturesSet.remove(ID);
      desc.setNatureIds(naturesSet.toArray(new String[naturesSet.size()]));
      project.setDescription(desc, monitor);
    }
  }
}
