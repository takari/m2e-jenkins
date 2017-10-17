package io.takari.m2e.jenkins.internal.launch;

import java.util.Collection;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.Launch;

public class SourceLookupLaunchUtil {

  public static ILaunch createLaunch(ILaunchConfiguration config, String mode) throws CoreException {
    if (Platform.getBundle("com.ifedorenko.jdt.launching") == null) {
      return new Launch(config, mode, null);
    }

    return Provider.newLaunch(config, mode);
  }

  public static String[] configureVMArgs(Collection<String> args) throws CoreException {

    if (Platform.getBundle("com.ifedorenko.jdt.launching") != null) {
      // add sourcelookup agent
      args.add(Provider.getJavaagentString());
    }

    return args.toArray(new String[args.size()]);
  }

  private static class Provider {
    @SuppressWarnings("restriction")
    static String getJavaagentString() throws CoreException {
      return com.ifedorenko.jdt.internal.launching.sourcelookup.advanced.AdvancedSourceLookupSupport
          .getJavaagentString();
    }

    @SuppressWarnings("restriction")
    static ILaunch newLaunch(ILaunchConfiguration config, String mode) throws CoreException {
      return com.ifedorenko.jdt.internal.launching.sourcelookup.advanced.AdvancedSourceLookupSupport
          .createAdvancedLaunch(config, mode);
    }
  }
}
