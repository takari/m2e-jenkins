package io.takari.m2e.jenkins.runtime;

import java.net.MalformedURLException;
import java.net.URL;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class JenkinsRuntimePlugin implements BundleActivator {

  private static JenkinsRuntimePlugin instance;

  private BundleContext ctx;

  @Override
  public void start(BundleContext ctx) throws Exception {
    this.ctx = ctx;
    instance = this;
  }

  @Override
  public void stop(BundleContext ctx) throws Exception {
    ctx = null;
    instance = null;
  }

  public static JenkinsRuntimePlugin getInstance() {
    return instance;
  }

  public String getLauncherClass() {
    return "io.takari.m2e.jenkins.launcher.Main";
  }

  public Bundle getBundle() {
    return ctx.getBundle();
  }

  public URL getJrebelPlugin() {
    return getFileInPlugin("jrebel-plugin.jar");
  }

  private URL getFileInPlugin(String path) {
    try {
      return new URL(getBundle().getEntry("/"), path.toString());
    } catch (MalformedURLException e) {
      throw new IllegalStateException(e);
    }
  }
}
