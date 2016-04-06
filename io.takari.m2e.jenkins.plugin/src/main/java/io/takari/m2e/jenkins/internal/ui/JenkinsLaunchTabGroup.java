package io.takari.m2e.jenkins.internal.ui;

import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.ui.AbstractLaunchConfigurationTabGroup;
import org.eclipse.debug.ui.CommonTab;
import org.eclipse.debug.ui.EnvironmentTab;
import org.eclipse.debug.ui.ILaunchConfigurationDialog;
import org.eclipse.debug.ui.ILaunchConfigurationTab;
import org.eclipse.debug.ui.sourcelookup.SourceLookupTab;
import org.eclipse.jdt.debug.ui.launchConfigurations.JavaArgumentsTab;
import org.eclipse.jdt.debug.ui.launchConfigurations.JavaClasspathTab;
import org.eclipse.jdt.debug.ui.launchConfigurations.JavaJRETab;

public class JenkinsLaunchTabGroup extends AbstractLaunchConfigurationTabGroup {

  @Override
  public void createTabs(ILaunchConfigurationDialog dialog, String mode) {
    ILaunchConfigurationTab[] tabs = new ILaunchConfigurationTab[] { //
        new JenkinsMainTab(), //
        new JavaArgumentsTab(), //
        new JavaJRETab(), //
        new JavaClasspathTab(), //
        new SourceLookupTab(), //
        new EnvironmentTab(), //
        new CommonTab() { //
          public void setDefaults(ILaunchConfigurationWorkingCopy config) { //
            super.setDefaults(config); //
            config.setAttribute(DebugPlugin.ATTR_CONSOLE_ENCODING, "UTF-8"); //
          }; //
        } //
    };
    setTabs(tabs);
  }

}
