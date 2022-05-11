package io.takari.m2e.jenkins.internal;

import java.util.Set;

import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.m2e.core.lifecyclemapping.model.IPluginExecutionMetadata;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.m2e.core.project.configurator.AbstractBuildParticipant;
import org.eclipse.m2e.core.project.configurator.AbstractBuildParticipant2;
import org.eclipse.m2e.core.project.configurator.AbstractProjectConfigurator;
import org.eclipse.m2e.core.project.configurator.ProjectConfigurationRequest;

import io.takari.m2e.jenkins.plugin.JenkinsPluginProject;

public class JenkinsLocalizerProjectConfigurator extends AbstractProjectConfigurator {

  @Override
  public void configure(ProjectConfigurationRequest request, IProgressMonitor monitor) throws CoreException {
  }

  @Override
  public AbstractBuildParticipant getBuildParticipant(IMavenProjectFacade projectFacade, final MojoExecution execution,
      IPluginExecutionMetadata executionMetadata) {
    return new AbstractBuildParticipant2() {

      @Override
      public Set<IProject> build(int kind, IProgressMonitor monitor) throws Exception {
        IProject project = getMavenProjectFacade().getProject();
        MavenProject mavenProject = getMavenProjectFacade().getMavenProject();

        if (kind == PRECONFIGURE_BUILD) {
          String output = maven.getMojoParameterValue(mavenProject, execution, "outputDirectory", String.class,
              monitor);
          mavenProject.addCompileSourceRoot(output);
        } else if (kind != CLEAN_BUILD) {
          JenkinsPluginProject jp = JenkinsPluginProject.create(project, monitor);
          if (jp != null) {
            if (kind == IncrementalProjectBuilder.FULL_BUILD) {
              fullBuild(execution, monitor);
            } else {
              IResourceDelta delta = getDelta(project);
              if (delta == null) {
                fullBuild(execution, monitor);
              } else {
                incrementalBuild(execution, delta, monitor);
              }
            }
          }
        }
        return null;
      }

      private void fullBuild(MojoExecution execution, IProgressMonitor monitor) throws CoreException {
        IProject project = getMavenProjectFacade().getProject();
        MavenProject mavenProject = getMavenProjectFacade().getMavenProject();

        // execute localier:generate
        maven.execute(mavenProject, execution, monitor);
        String output = maven.getMojoParameterValue(mavenProject, execution, "outputDirectory", String.class, monitor);
        project.getFolder(output).refreshLocal(IResource.DEPTH_INFINITE, monitor);
      }

      private void incrementalBuild(MojoExecution execution, IResourceDelta delta, IProgressMonitor monitor)
          throws CoreException {

        MavenProject mavenProject = getMavenProjectFacade().getMavenProject();
        final String fileMask = maven.getMojoParameterValue(mavenProject, execution, "fileMask", String.class, monitor);
        final boolean[] messagesChanged = new boolean[] { false };

        delta.accept(new IResourceDeltaVisitor() {
          @Override
          public boolean visit(IResourceDelta delta) throws CoreException {
            IResource res = delta.getResource();
            if (!messagesChanged[0] && res.getType() == IResource.FILE) {

              String name = res.getName();

              if (!name.endsWith(".properties") || name.contains("_"))
                return true;

              if (fileMask != null && !name.equals(fileMask))
                return true;

              messagesChanged[0] = true;
            }
            return true;
          }
        });

        if (messagesChanged[0]) {
          fullBuild(execution, monitor);
        }
      }

    };
  }

}
