package io.takari.m2e.jenkins.internal.launch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.Launch;
import org.eclipse.debug.core.model.IPersistableSourceLocator;

public class SourceLookupLaunchUtil {

  public static ILaunch createLaunch(ILaunchConfiguration config, String mode) throws CoreException {

    try {
      Class.forName("com.ifedorenko.m2e.sourcelookup.internal.SourceLookupActivator");
    } catch (ClassNotFoundException e) {
      return new Launch(config, mode, null);
    }
    return Provider.newLaunch(config, mode);
  }

  public static String[] configureVMArgs(String[] args) throws CoreException {

    try {
      Class.forName("com.ifedorenko.m2e.sourcelookup.internal.SourceLookupActivator");
    } catch (ClassNotFoundException e) {
      return args;
    }
    List<String> arglist = new ArrayList<>();
    Collections.addAll(arglist, args);

    // add sourcelookup agent
    arglist.add(Provider.getJavaagentString());

    return arglist.toArray(new String[arglist.size()]);
  }

  private static class Provider {
    @SuppressWarnings("restriction")
    static String getJavaagentString() throws CoreException {
      return com.ifedorenko.m2e.sourcelookup.internal.SourceLookupActivator.getDefault().getJavaagentString();
    }

    static ILaunch newLaunch(ILaunchConfiguration config, String mode) throws CoreException {
      IPersistableSourceLocator locator = DebugPlugin.getDefault().getLaunchManager()
          .newSourceLocator("com.ifedorenko.m2e.sourcelookupDirector");
      locator.initializeDefaults(config);
      return new Launch(config, mode, locator);
    }
  }
}
