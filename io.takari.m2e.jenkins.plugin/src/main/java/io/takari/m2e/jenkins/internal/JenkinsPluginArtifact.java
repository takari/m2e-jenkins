package io.takari.m2e.jenkins.internal;

import java.io.File;
import java.util.Collections;
import java.util.List;

import org.apache.maven.project.MavenProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

public class JenkinsPluginArtifact implements IJenkinsPlugin {
  private MavenProject mavenProject;
  private MavenProject containingProject;

  public JenkinsPluginArtifact(MavenProject mavenProject, MavenProject containingProject) {
    this.mavenProject = mavenProject;
    this.containingProject = containingProject;
  }

  @Override
  public String getGroupId() {
    return mavenProject.getGroupId();
  }

  @Override
  public String getArtifactId() {
    return mavenProject.getArtifactId();
  }

  @Override
  public String getVersion() {
    return mavenProject.getVersion();
  }

  @Override
  public MavenProject getMavenProject() {
    return mavenProject;
  }

  @Override
  public File getPluginFile(IProgressMonitor monitor) throws CoreException {
    return JenkinsPluginProject.resolveIfNeeded(getGroupId(), getArtifactId(), getVersion(), "hpi", containingProject,
        monitor);
  }

  @Override
  public List<String> getResources() {
    return Collections.emptyList();
  }

}
