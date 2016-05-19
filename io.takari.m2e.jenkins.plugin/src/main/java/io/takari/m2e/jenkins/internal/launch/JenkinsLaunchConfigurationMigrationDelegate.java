package io.takari.m2e.jenkins.internal.launch;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationMigrationDelegate;

public class JenkinsLaunchConfigurationMigrationDelegate implements ILaunchConfigurationMigrationDelegate {

  @Override
  public boolean isCandidate(ILaunchConfiguration candidate) throws CoreException {
    return JenkinsLaunchConfig.needsMigration(candidate);
  }

  @Override
  public void migrate(ILaunchConfiguration candidate) throws CoreException {
    JenkinsLaunchConfig.migrate(candidate);
  }

}
