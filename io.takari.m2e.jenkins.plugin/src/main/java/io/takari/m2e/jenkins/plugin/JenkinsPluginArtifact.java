package io.takari.m2e.jenkins.plugin;

import java.io.File;
import java.util.Collections;
import java.util.List;

import org.apache.maven.project.MavenProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

public class JenkinsPluginArtifact implements IJenkinsPlugin {
  private MavenProject mavenProject;
  private JenkinsPluginProject rootProject;

  public JenkinsPluginArtifact(MavenProject mavenProject, JenkinsPluginProject rootProject) {
    this.mavenProject = mavenProject;
    this.rootProject = rootProject;
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
  public MavenProject getMavenProject(IProgressMonitor monitor) {
    return mavenProject;
  }

  @Override
  public File getPluginFile(IProgressMonitor monitor, boolean regenerate) throws CoreException {
    return rootProject.resolveIfNeeded(getGroupId(), getArtifactId(), getVersion(), "hpi", mavenProject, monitor);
  }

  @Override
  public List<String> getResources(IProgressMonitor monitor) {
    return Collections.emptyList();
  }

}
