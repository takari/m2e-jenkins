package io.takari.m2e.jenkins.internal;

import java.util.Map;

import org.apache.maven.plugin.MojoExecution;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.apt.core.internal.util.FactoryContainer;
import org.eclipse.jdt.apt.core.internal.util.FactoryPath;
import org.eclipse.jdt.apt.core.internal.util.FactoryPath.Attributes;
import org.eclipse.jdt.apt.core.util.AptConfig;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.m2e.core.lifecyclemapping.model.IPluginExecutionMetadata;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.m2e.core.project.configurator.AbstractBuildParticipant;
import org.eclipse.m2e.core.project.configurator.AbstractProjectConfigurator;
import org.eclipse.m2e.core.project.configurator.MojoExecutionBuildParticipant;
import org.eclipse.m2e.core.project.configurator.ProjectConfigurationRequest;

@SuppressWarnings("restriction")
public class JenkinsProjectAptConfigurator extends AbstractProjectConfigurator {

  @Override
  public void configure(ProjectConfigurationRequest request, IProgressMonitor monitor) throws CoreException {
    IJavaProject jp = JavaCore.create(request.getProject());
    FactoryPath fp = (FactoryPath) AptConfig.getFactoryPath(jp);
    Map<FactoryContainer, Attributes> ctrs = fp.getAllContainers();

    // set up batch mode for sezpoz lib
    for (Map.Entry<FactoryContainer, Attributes> e : ctrs.entrySet()) {
      if (e.getKey().getId().startsWith("M2_REPO/net/java/sezpoz/sezpoz/")) {
        e.getValue().setRunInBatchMode(true);
      }
    }
    fp.setContainers(ctrs);
    AptConfig.setFactoryPath(jp, fp);
  }

  @Override
  public AbstractBuildParticipant getBuildParticipant(IMavenProjectFacade projectFacade, MojoExecution execution,
      IPluginExecutionMetadata executionMetadata) {
    return new MojoExecutionBuildParticipant(execution, false, true);
  }

}
