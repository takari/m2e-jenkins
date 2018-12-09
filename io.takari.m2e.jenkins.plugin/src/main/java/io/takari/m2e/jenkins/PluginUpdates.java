package io.takari.m2e.jenkins;

import io.takari.m2e.jenkins.runtime.PluginUpdateCenter;

public class PluginUpdates {
  private final PluginUpdateCenter updateCenter;
  private final String maxCoreVersion;

  public PluginUpdates(PluginUpdateCenter updateCenter, String maxCoreVersion) {
    this.updateCenter = updateCenter;
    this.maxCoreVersion = maxCoreVersion;
  }

  public String getVersion(String groupId, String artifactId) {
    return updateCenter.getVersion(groupId, artifactId, maxCoreVersion);
  }
}
