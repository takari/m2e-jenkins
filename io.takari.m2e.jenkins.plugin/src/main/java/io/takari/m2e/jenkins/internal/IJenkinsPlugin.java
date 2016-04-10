package io.takari.m2e.jenkins.internal;

import java.io.File;
import java.util.List;

import org.apache.maven.project.MavenProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

public interface IJenkinsPlugin {
  String getGroupId();

  String getArtifactId();

  String getVersion();

  MavenProject getMavenProject();

  File getPluginFile(IProgressMonitor monitor) throws CoreException;

  List<String> getResources();

}
