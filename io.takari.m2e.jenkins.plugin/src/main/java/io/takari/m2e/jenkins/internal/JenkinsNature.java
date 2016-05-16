package io.takari.m2e.jenkins.internal;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectNature;
import org.eclipse.core.runtime.CoreException;

@Deprecated
public class JenkinsNature implements IProjectNature {

  private IProject project;

  @Override
  public void configure() throws CoreException {
  }

  @Override
  public void deconfigure() throws CoreException {
  }

  @Override
  public IProject getProject() {
    return project;
  }

  @Override
  public void setProject(IProject project) {
    this.project = project;
  }
}
