package io.takari.m2e.jenkins.internal;

import java.util.Set;

import org.apache.maven.plugin.MojoExecution;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.embedder.ICallable;
import org.eclipse.m2e.core.embedder.IMaven;
import org.eclipse.m2e.core.embedder.IMavenExecutionContext;
import org.eclipse.m2e.core.lifecyclemapping.model.IPluginExecutionMetadata;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.m2e.core.project.configurator.AbstractBuildParticipant;
import org.eclipse.m2e.core.project.configurator.AbstractBuildParticipant2;
import org.eclipse.m2e.core.project.configurator.AbstractProjectConfigurator;
import org.eclipse.m2e.core.project.configurator.ProjectConfigurationRequest;

import io.takari.m2e.jenkins.JenkinsPluginProject;

public class JenkinsLocalizerProjectConfigurator extends AbstractProjectConfigurator {

  private static final String LOCALIZER_PLUGIN_GROUP_ID = "org.jvnet.localizer";
  private static final String LOCALIZER_PLUGIN_ARTIFACT_ID = "maven-localizer-plugin";

  @Override
  public void configure(ProjectConfigurationRequest request, IProgressMonitor monitor) throws CoreException {

  }

  @Override
  public AbstractBuildParticipant getBuildParticipant(IMavenProjectFacade projectFacade, MojoExecution execution,
      IPluginExecutionMetadata executionMetadata) {
    return new LocalizerBuildParticipant();
  }

  public static class LocalizerBuildParticipant extends AbstractBuildParticipant2 {

    @Override
    public Set<IProject> build(int kind, IProgressMonitor monitor) throws Exception {

      IProject project = getMavenProjectFacade().getProject();
      JenkinsPluginProject jp = JenkinsPluginProject.create(project, monitor);
      if (jp != null) {
        if (kind == IncrementalProjectBuilder.FULL_BUILD) {
          fullBuild(jp, monitor);
        } else {
          IResourceDelta delta = getDelta(project);
          if (delta == null) {
            fullBuild(jp, monitor);
          } else {
            incrementalBuild(jp, delta, monitor);
          }
        }
      }
      return null;
    }

    private void fullBuild(final JenkinsPluginProject jp, IProgressMonitor monitor) throws CoreException {
      // execute localier:generate
      jp.executeMojo(LOCALIZER_PLUGIN_GROUP_ID, LOCALIZER_PLUGIN_ARTIFACT_ID, "generate", monitor);
      String output = jp.getMojoParameter(LOCALIZER_PLUGIN_GROUP_ID, LOCALIZER_PLUGIN_ARTIFACT_ID, "generate",
          "outputDirectory", String.class, monitor);
      jp.getProject().getFolder(output).refreshLocal(IResource.DEPTH_INFINITE, monitor);

      // add generated source folder
      // older versions didn't seem to do that correctly
      final IMaven maven = MavenPlugin.getMaven();
      maven.execute(new ICallable<Void>() {
        @Override
        public Void call(IMavenExecutionContext context, IProgressMonitor monitor) throws CoreException {

          IMavenProjectFacade mp = getMavenProjectFacade();

          String generatedSources = jp.getMojoParameter(LOCALIZER_PLUGIN_GROUP_ID, LOCALIZER_PLUGIN_ARTIFACT_ID,
              "generate", "outputDirectory", String.class, monitor);
          mp.getMavenProject().addCompileSourceRoot(generatedSources);

          return null;
        }
      }, monitor);
    }

    private void incrementalBuild(JenkinsPluginProject jp, IResourceDelta delta, IProgressMonitor monitor)
        throws CoreException {

      final String fileMask = jp.getMojoParameter(LOCALIZER_PLUGIN_GROUP_ID, LOCALIZER_PLUGIN_ARTIFACT_ID, "generate",
          "fileMask", String.class, monitor);
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
        fullBuild(jp, monitor);
      }
    }
  }
}
