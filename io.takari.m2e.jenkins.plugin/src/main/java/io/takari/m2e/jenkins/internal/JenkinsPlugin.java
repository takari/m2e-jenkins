package io.takari.m2e.jenkins.internal;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.Status;
import org.osgi.framework.BundleContext;

public class JenkinsPlugin extends Plugin {

  public static final String ID = "io.takari.m2e.jenkins.plugin";

  private static JenkinsPlugin instance;

  @Override
  public void start(BundleContext context) throws Exception {
    super.start(context);
    instance = this;
  }

  public void stop(BundleContext context) throws Exception {
    instance = null;
    super.stop(context);
  }

  public static JenkinsPlugin getInstance() {
    return instance;
  }

  public static void error(String message, Throwable t) {
    getInstance().getLog().log(new Status(IStatus.ERROR, ID, message, t));
  }

  public static void error(String message) {
    getInstance().getLog().log(new Status(IStatus.ERROR, ID, message));
  }

  public static void warning(String message, Throwable t) {
    getInstance().getLog().log(new Status(IStatus.WARNING, ID, message, t));
  }

  public static void warning(String message) {
    getInstance().getLog().log(new Status(IStatus.WARNING, ID, message));
  }

  public static void info(String message) {
    getInstance().getLog().log(new Status(IStatus.INFO, ID, message));
  }

}
