package io.takari.m2e.jenkins.internal.launch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

  public static String[] configureVMArgs(String[] args) throws CoreException {

    if (Platform.getBundle("com.ifedorenko.jdt.launching") == null) {
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
