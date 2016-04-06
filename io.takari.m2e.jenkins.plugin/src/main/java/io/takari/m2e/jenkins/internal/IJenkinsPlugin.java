package io.takari.m2e.jenkins.internal;

import java.io.File;
import java.util.List;

public interface IJenkinsPlugin {
  String getGroupId();

  String getArtifactId();

  String getVersion();

  File getFile();

  File getLocation();

  List<String> getResources();
}
