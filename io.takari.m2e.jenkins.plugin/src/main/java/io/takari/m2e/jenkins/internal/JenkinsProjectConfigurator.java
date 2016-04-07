package io.takari.m2e.jenkins.internal;

import java.io.File;

import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.m2e.core.lifecyclemapping.model.IPluginExecutionMetadata;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.m2e.core.project.configurator.AbstractBuildParticipant;
import org.eclipse.m2e.core.project.configurator.AbstractProjectConfigurator;
import org.eclipse.m2e.core.project.configurator.MojoExecutionBuildParticipant;
import org.eclipse.m2e.core.project.configurator.ProjectConfigurationRequest;
import org.jboss.tools.maven.apt.MavenJdtAptPlugin;
import org.jboss.tools.maven.apt.preferences.AnnotationProcessingMode;
import org.jboss.tools.maven.apt.preferences.IPreferencesManager;

public class JenkinsProjectConfigurator extends AbstractProjectConfigurator {

  @Override
  public void configure(ProjectConfigurationRequest request, IProgressMonitor monitor) throws CoreException {
    IProject project = request.getProject();

    // enable m2e-apt on hpi projects
    IPreferencesManager pmgr = MavenJdtAptPlugin.getDefault().getPreferencesManager();
    if (pmgr.getAnnotationProcessorMode(project) == AnnotationProcessingMode.disabled) {
      pmgr.setAnnotationProcessorMode(project, AnnotationProcessingMode.jdt_apt);
    }
  }

  @Override
  public AbstractBuildParticipant getBuildParticipant(IMavenProjectFacade projectFacade, MojoExecution execution,
      IPluginExecutionMetadata executionMetadata) {
    return new MojoExecutionBuildParticipant(execution, false, true);
  }

  protected IContainer getOutputLocation(ProjectConfigurationRequest request, IProject project) {
    MavenProject mavenProject = request.getMavenProject();
    return getFolder(project, mavenProject.getBuild().getOutputDirectory());
  }

  protected IFolder getFolder(IProject project, String absolutePath) {
    if (project.getLocation().makeAbsolute().equals(Path.fromOSString(absolutePath))) {
      return project.getFolder(project.getLocation());
    }
    return project.getFolder(getProjectRelativePath(project, absolutePath));
  }

  protected IPath getProjectRelativePath(IProject project, String absolutePath) {
    File basedir = project.getLocation().toFile();
    String relative;
    if (absolutePath.equals(basedir.getAbsolutePath())) {
      relative = "."; //$NON-NLS-1$
    } else if (absolutePath.startsWith(basedir.getAbsolutePath())) {
      relative = absolutePath.substring(basedir.getAbsolutePath().length() + 1);
    } else {
      relative = absolutePath;
    }
    return new Path(relative.replace('\\', '/')); // $NON-NLS-1$ //$NON-NLS-2$
  }

}
