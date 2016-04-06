package io.takari.m2e.jenkins.internal;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class JenkinsPluginArtifact implements IJenkinsPlugin {
  private String groupId;
  private String artifactId;
  private String version;
  private File file;

  public JenkinsPluginArtifact(String groupId, String artifactId, String version, File file) {
    this.groupId = groupId;
    this.artifactId = artifactId;
    this.version = version;
    this.file = file;
  }

  @Override
  public String getGroupId() {
    return groupId;
  }

  @Override
  public String getArtifactId() {
    return artifactId;
  }

  @Override
  public String getVersion() {
    return version;
  }

  @Override
  public File getFile() {
    return file;
  }

  @Override
  public File getLocation() {
    return getFile();
  }

  @Override
  public List<String> getResources() {
    return Collections.emptyList();
  }

}
