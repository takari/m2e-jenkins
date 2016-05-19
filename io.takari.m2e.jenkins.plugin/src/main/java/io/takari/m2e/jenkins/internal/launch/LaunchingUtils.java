package io.takari.m2e.jenkins.internal.launch;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.variables.VariablesPlugin;


public class LaunchingUtils {

  private static final String PROJECT_LOCATION_VARIABLE_NAME = "project_loc";

  private static final String WORKSPACE_LOCATION_VARIABLE_NAME = "workspace_loc";

  /**
   * Substitute any variable
   */
  public static String substituteVar(String s) throws CoreException {
    if(s != null) {
      return VariablesPlugin.getDefault().getStringVariableManager().performStringSubstitution(s);
    }
    return null;
  }

  /**
   * Generate project_loc variable expression for the given project.
   */
  public static String generateProjectLocationVariableExpression(IProject project) {
    return VariablesPlugin.getDefault().getStringVariableManager()
        .generateVariableExpression(PROJECT_LOCATION_VARIABLE_NAME, project.getName());
  }

  public static String generateWorkspaceLocationVariableExpression(IPath path) {
    return VariablesPlugin.getDefault().getStringVariableManager()
        .generateVariableExpression(WORKSPACE_LOCATION_VARIABLE_NAME, path.toString());
  }
}
