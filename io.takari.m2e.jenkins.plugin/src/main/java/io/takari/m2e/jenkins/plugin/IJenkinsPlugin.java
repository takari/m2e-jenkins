package io.takari.m2e.jenkins.plugin;

import java.io.File;
import java.util.List;

import org.apache.maven.project.MavenProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

public interface IJenkinsPlugin {
  String getGroupId();

  String getArtifactId();

  String getVersion();

  MavenProject getMavenProject(IProgressMonitor monitor) throws CoreException;

  File getPluginFile(IProgressMonitor monitor, boolean regenerate) throws CoreException;

  List<String> getResources(IProgressMonitor monitor) throws CoreException;

}
