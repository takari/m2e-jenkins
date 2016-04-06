package io.takari.m2e.jenkins;

import org.eclipse.core.runtime.Plugin;
import org.osgi.framework.BundleContext;

public class JenkinsPlugin extends Plugin {

  public static final String ID = "io.takari.m2e.jenkins";

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
}
