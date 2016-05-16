package io.takari.m2e.jenkins.internal;

import java.util.Map;

import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import io.takari.m2e.jenkins.JenkinsPluginProject;
import io.takari.m2e.jenkins.internal.idx.AnnotationIndexer;
import io.takari.m2e.jenkins.internal.idx.HudsonAnnIndexer;
import io.takari.m2e.jenkins.internal.idx.SezpozIndexer;

public class JenkinsBuilder extends IncrementalProjectBuilder {

  public static final String ID = JenkinsPlugin.ID + ".builder";

  @Override
  protected IProject[] build(int kind, Map<String, String> args, IProgressMonitor monitor) throws CoreException {

    JenkinsPluginProject jp = JenkinsPluginProject.create(getProject(), monitor);
    if (jp != null) {
      try {

        if (kind == IncrementalProjectBuilder.FULL_BUILD) {
          processAnnotations(jp, null, monitor);
        } else {
          IResourceDelta delta = getDelta(getProject());
          processAnnotations(jp, delta, monitor);
        }

        IFolder target = ResourcesPlugin.getWorkspace().getRoot().getFolder(jp.getFacade().getOutputLocation());
        target.refreshLocal(IResource.DEPTH_INFINITE, monitor);

      } catch (CoreException e) {
        JenkinsPlugin.error("Error processing annotations", e);
      }
    }
    return null;
  }

  public void processAnnotations(JenkinsPluginProject jp, IResourceDelta delta, IProgressMonitor monitor)
      throws CoreException {
    AnnotationIndexer.process(jp.getFacade(), delta, monitor, new SezpozIndexer(), new HudsonAnnIndexer());
  }

  public static void add(IProject project) throws CoreException {
    IProjectDescription desc = project.getDescription();
    ICommand[] commands = desc.getBuildSpec();
    boolean found = false;

    for (int i = 0; i < commands.length; ++i) {
      if (commands[i].getBuilderName().equals(ID)) {
        found = true;
        break;
      }
    }
    if (!found) {
      // add builder to project
      ICommand command = desc.newCommand();
      command.setBuilderName(ID);
      ICommand[] newCommands = new ICommand[commands.length + 1];

      // Add it after other builders.
      System.arraycopy(commands, 0, newCommands, 0, commands.length);
      newCommands[commands.length] = command;
      desc.setBuildSpec(newCommands);
      project.setDescription(desc, null);
    }
  }

  public static void remove(IProject project) throws CoreException {
    IProjectDescription desc = project.getDescription();
    ICommand[] commands = desc.getBuildSpec();

    for (int i = 0; i < commands.length; ++i) {
      if (commands[i].getBuilderName().equals(ID)) {

        ICommand command = desc.newCommand();
        command.setBuilderName(ID);
        ICommand[] newCommands = new ICommand[commands.length - 1];
        if (i > 0)
          System.arraycopy(commands, 0, newCommands, 0, i);
        System.arraycopy(commands, i + 1, newCommands, i, commands.length - 1 - i);
        desc.setBuildSpec(newCommands);
        project.setDescription(desc, null);
        break;
      }
    }
  }
}
