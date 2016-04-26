package io.takari.m2e.jenkins.plugin.internal;

import java.util.Map;

import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import io.takari.m2e.jenkins.JenkinsPlugin;
import io.takari.m2e.jenkins.internal.JenkinsPluginProject;

public class JenkinsBuilder extends IncrementalProjectBuilder {

  public static final String ID = JenkinsPlugin.ID + ".builder";

  @Override
  protected IProject[] build(int kind, Map<String, String> args, IProgressMonitor monitor) throws CoreException {

    JenkinsPluginProject jp = JenkinsPluginProject.create(getProject(), monitor);
    if (jp != null) {
      if (kind == IncrementalProjectBuilder.FULL_BUILD) {
        fullBuild(jp, monitor);
      } else {
        IResourceDelta delta = getDelta(getProject());
        if (delta == null) {
          fullBuild(jp, monitor);
        } else {
          incrementalBuild(jp, delta, monitor);
        }
      }
    }
    return null;
  }

  private void fullBuild(JenkinsPluginProject jp, IProgressMonitor monitor) throws CoreException {
    // TODO Auto-generated method stub

    // will be called by maven
    // jp.generateMessages(monitor);
    jp.processAnnotations(monitor);
  }

  private void incrementalBuild(JenkinsPluginProject jp, IResourceDelta delta, IProgressMonitor monitor)
      throws CoreException {

    final String fileMask = jp.getLocalizerMojoParameter("generate", "fileMask", String.class, monitor);
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
      jp.generateMessages(monitor);
    }

    jp.processAnnotations(monitor, delta);
    /*
     * ResourcesPlugin.getWorkspace().getRoot().getFolder(jp.getFacade().
     * getOutputLocation()) .refreshLocal(IResource.DEPTH_INFINITE, monitor);
     */
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

      // Add it before other builders.
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
