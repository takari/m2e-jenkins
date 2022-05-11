package io.takari.m2e.jenkins.internal;

import java.util.Arrays;
import java.util.Set;

import org.apache.maven.plugin.MojoExecution;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.embedder.IMaven;
import org.eclipse.m2e.core.lifecyclemapping.model.IPluginExecutionMetadata;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.m2e.core.project.configurator.AbstractBuildParticipant;
import org.eclipse.m2e.core.project.configurator.AbstractBuildParticipant2;
import org.eclipse.m2e.core.project.configurator.AbstractProjectConfigurator;
import org.eclipse.m2e.core.project.configurator.ProjectConfigurationRequest;
import org.jboss.tools.maven.apt.MavenJdtAptPlugin;
import org.jboss.tools.maven.apt.preferences.AnnotationProcessingMode;
import org.jboss.tools.maven.apt.preferences.IPreferencesManager;
import org.osgi.framework.Bundle;

import io.takari.m2e.jenkins.plugin.JenkinsPluginProject;

public class JenkinsProjectConfigurator extends AbstractProjectConfigurator {

  @Override
  public void configure(ProjectConfigurationRequest request, IProgressMonitor monitor) throws CoreException {
    IProject project = request.getProject();

    IPreferencesManager pmgr = MavenJdtAptPlugin.getDefault().getPreferencesManager();

    // takari lifecycle does everything correctly
    if (!checkTakariLifecycle(request.getProject(), monitor)) {

      // enable m2e-apt on hpi projects
      if (pmgr.getAnnotationProcessorMode(project) == AnnotationProcessingMode.disabled) {
        pmgr.setAnnotationProcessorMode(project, AnnotationProcessingMode.jdt_apt);
      }
      JenkinsBuilder.add(project);
      JenkinsPlugin.info("Enabling m2e-apt for " + project.getName());

    } else {

      // disable m2e-apt
      if (pmgr.getAnnotationProcessorMode(project) != AnnotationProcessingMode.disabled) {
        pmgr.setAnnotationProcessorMode(project, AnnotationProcessingMode.disabled);
      }
      JenkinsBuilder.remove(project);
      JenkinsPlugin.info("Not enabling m2e-apt for " + project.getName() + " due to takari-lifecycle apt presence");

    }

    JenkinsNature.enable(project, monitor);
  }

  private boolean checkTakariLifecycle(IProject project, IProgressMonitor monitor) throws CoreException {
    // takari lifecycle manages annotation processing itself
    Bundle takariJdt = Platform.getBundle("io.takari.m2e.jdt.core");
    if (takariJdt != null) {
      JenkinsPluginProject jp = JenkinsPluginProject.create(project, monitor);
      String proc = jp.getMojoParameter("io.takari.maven.plugins", "takari-lifecycle-plugin", "compile", "proc",
          String.class, monitor);
      if (proc != null && !proc.equals("none")) {
        return true;
      }
    }
    return false;
  }

  @Override
  public AbstractBuildParticipant getBuildParticipant(IMavenProjectFacade projectFacade, final MojoExecution execution,
      IPluginExecutionMetadata executionMetadata) {
    final IMaven maven = MavenPlugin.getMaven();

    return new AbstractBuildParticipant2() {
      public Set<IProject> build(int kind, IProgressMonitor monitor) throws Exception {
        IMavenProjectFacade facade = getMavenProjectFacade();
        JenkinsPluginProject jdep = JenkinsPluginProject.create(facade, monitor);
        if (jdep != null) {
          boolean force = false;
          try {
            if (FULL_BUILD == kind) {
              force = true;
            }
            if (force || hasInterestingDelta(jdep)) {
              jdep.generateFixJar(force, monitor);
              jdep.generateTestHpl(execution, force, monitor);
              jdep.generateTestDependenciesIndex(maven.getExecutionContext(), force, monitor);
              facade.getProject().getFolder("target").refreshLocal(IResource.DEPTH_INFINITE, monitor);
            }
          } catch (Exception e) {
            JenkinsPlugin.error("Error running test-hpl on " + facade.getProject().getName(), e);
          }
        }
        return null;
      }

      private boolean hasInterestingDelta(JenkinsPluginProject jp) {
        IResourceDelta delta = getDelta(jp.getProject());
        if (delta != null) {
          for (IPath path : Arrays.asList( //
              jp.getHplLocation(), //
              jp.getTestDependenciesLocation(), //
              jp.getHpiTrickLocation())) {
            if (delta.findMember(path) != null) {
              return true;
            }
          }
        }
        return false;
      }
    };
  }

}
