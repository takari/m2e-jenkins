package io.takari.m2e.jenkins.jrebel;

import org.zeroturnaround.javarebel.ClassEventListener;
import org.zeroturnaround.javarebel.ClassResourceSource;
import org.zeroturnaround.javarebel.Integration;
import org.zeroturnaround.javarebel.IntegrationFactory;
import org.zeroturnaround.javarebel.Plugin;
import org.zeroturnaround.javarebel.Reloader;
import org.zeroturnaround.javarebel.ReloaderFactory;

import io.takari.m2e.jenkins.jrebel.cbp.AbstractTearOffCBP;
import io.takari.m2e.jenkins.jrebel.cbp.CachingScriptLoaderCBP;
import io.takari.m2e.jenkins.jrebel.cbp.IntrospectorBaseCBP;
import io.takari.m2e.jenkins.jrebel.cbp.MethodHandleFactoryCBP;
import io.takari.m2e.jenkins.jrebel.cbp.StaplerCBP;
import io.takari.m2e.jenkins.jrebel.cbp.UberspectImplCBP;
import io.takari.m2e.jenkins.jrebel.cbp.WebAppCBP;

public class JenkinsPlugin implements Plugin {

  @Override
  public void preinit() {
    Integration integration = IntegrationFactory.getInstance();
    Reloader reloader = ReloaderFactory.getInstance();
    ClassLoader cl = getClass().getClassLoader();

    integration.addIntegrationProcessor(cl,
        "org.kohsuke.stapler.Stapler",
        new StaplerCBP());

    integration.addIntegrationProcessor(cl,
        "org.kohsuke.stapler.WebApp",
        new WebAppCBP());

    integration.addIntegrationProcessor(cl,
        "org.kohsuke.stapler.CachingScriptLoader",
        new CachingScriptLoaderCBP());

    integration.addIntegrationProcessor(cl,
        "org.kohsuke.stapler.AbstractTearOff",
        new AbstractTearOffCBP());

    integration.addIntegrationProcessor(cl,
        "org.apache.commons.jexl.util.introspection.UberspectImpl",
        new UberspectImplCBP());

    integration.addIntegrationProcessor(cl,
        "org.apache.commons.jexl.util.introspection.IntrospectorBase",
        new IntrospectorBaseCBP());

    integration.addIntegrationProcessor(cl,
        "org.kohsuke.stapler.MethodHandleFactory",
        new MethodHandleFactoryCBP());

    reloader.addClassReloadListener(new ClassEventListener() {

      @Override
      public void onClassEvent(int eventType, Class<?> klass) throws Exception {
        MetaClassReloader.get().reloaded(klass);

        IntrospectorExt introspector = IntrospectorHelper.getIntrospector();
        if (introspector != null) {
          introspector.clearClass(klass.getName());
        }
      }

      @Override
      public int priority() {
        return PRIORITY_DEFAULT;
      }
    });

  }

  @Override
  public boolean checkDependencies(ClassLoader cl, ClassResourceSource crs) {
    return crs.getClassResource("org.kohsuke.stapler.Stapler") != null;
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
