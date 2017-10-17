package io.takari.m2e.jenkins.jrebel;

import org.zeroturnaround.javarebel.ClassResourceSource;
import org.zeroturnaround.javarebel.Integration;
import org.zeroturnaround.javarebel.IntegrationFactory;
import org.zeroturnaround.javarebel.Plugin;

import io.takari.m2e.jenkins.jrebel.cbp.AbstractTearOffCBP;
import io.takari.m2e.jenkins.jrebel.cbp.CachingScriptLoaderCBP;

public class JenkinsPlugin implements Plugin {

  @Override
  public void preinit() {
    Integration integration = IntegrationFactory.getInstance();
    ClassLoader cl = getClass().getClassLoader();

    integration.addIntegrationProcessor(cl,
        "org.kohsuke.stapler.CachingScriptLoader",
        new CachingScriptLoaderCBP());

    integration.addIntegrationProcessor(cl, 
        "org.kohsuke.stapler.AbstractTearOff",
        new AbstractTearOffCBP());
    
    // add more processors here
  }

  @Override
  public boolean checkDependencies(ClassLoader cl, ClassResourceSource crs) {
    return crs.getClassResource("org.kohsuke.stapler.CachingScriptLoader") != null;
  }

  @Override
  public String getId() {
    return "jenkins-plugin";
  }

  @Override
  public String getName() {
    return "Jenkins plugin";
  }

  @Override
  public String getDescription() {
    return getName();
  }

  @Override
  public String getAuthor() {
    return "atanasenko";
  }

  @Override
  public String getWebsite() {
    return null;
  }

  @Override
  public String getSupportedVersions() {
    return null;
  }

  @Override
  public String getTestedVersions() {
    return null;
  }

}
