package io.takari.m2e.jenkins.internal;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IProjectNature;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.jboss.tools.maven.apt.MavenJdtAptPlugin;
import org.jboss.tools.maven.apt.preferences.AnnotationProcessingMode;
import org.jboss.tools.maven.apt.preferences.IPreferencesManager;

import io.takari.m2e.jenkins.JenkinsPlugin;

public class JenkinsNature implements IProjectNature {

  public static final String ID = JenkinsPlugin.ID + ".nature";

  private IProject project;

  @Override
  public void configure() throws CoreException {
    // enable m2e-apt on hpi projects
    IPreferencesManager pmgr = MavenJdtAptPlugin.getDefault().getPreferencesManager();
    if (pmgr.getAnnotationProcessorMode(project) == AnnotationProcessingMode.disabled) {
      pmgr.setAnnotationProcessorMode(project, AnnotationProcessingMode.jdt_apt);
    }

    // add jenkins builder
    JenkinsBuilder.add(project);
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
