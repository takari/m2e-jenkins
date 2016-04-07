package io.takari.m2e.jenkins.internal;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Model;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.embedder.ICallable;
import org.eclipse.m2e.core.embedder.IMaven;
import org.eclipse.m2e.core.embedder.IMavenExecutionContext;
import org.eclipse.m2e.core.project.IMavenProjectFacade;

public class JenkinsPluginProject implements IJenkinsPlugin {

  private static final String TYPE_HPI = "hpi";
  private static final String TYPE_JPI = "jpi";
  private static final String HPI_PLUGIN_GROUP_ID = "org.jenkins-ci.tools";
  private static final String HPI_PLUGIN_ARTIFACT_ID = "maven-hpi-plugin";
  private static final String LOCALIZER_PLUGIN_GROUP_ID = "org.jvnet.localizer";
  private static final String LOCALIZER_PLUGIN_ARTIFACT_ID = "maven-localizer-plugin";

  private IMavenProjectFacade facade;
  private MavenProject mavenProject;

  private JenkinsPluginProject(IMavenProjectFacade facade, MavenProject mavenProject) {
    this.facade = facade;
    this.mavenProject = mavenProject;
  }

  public IProject getProject() {
    return facade.getProject();
  }

  public IMavenProjectFacade getFacade() {
    return facade;
  }

  public MavenProject getMavenProject() {
    return mavenProject;
  }

  @Override
  public String getGroupId() {
    return mavenProject.getGroupId();
  }

  @Override
  public String getArtifactId() {
    return mavenProject.getArtifactId();
  }

  @Override
  public String getVersion() {
    return mavenProject.getVersion();
  }

  public File getFile() {
    // assume that test-hpl mojo writes the file
    File testDir = new File(mavenProject.getBuild().getTestOutputDirectory());
    return new File(testDir, "the.hpl");
  }

  public File getLocation() {
    return new File(facade.getProject().getLocation().toOSString());
  }

  public List<IJenkinsPlugin> findPluginDependencies(IProgressMonitor monitor) throws CoreException {
    final List<IJenkinsPlugin> deps = new ArrayList<>();
    final IMaven maven = MavenPlugin.getMaven();

    maven.execute(false, true /* force update */, new ICallable<Void>() {
      @Override
      public Void call(IMavenExecutionContext context, IProgressMonitor monitor) throws CoreException {

        for (Artifact art : mavenProject.getArtifacts()) {
          if (monitor.isCanceled())
            break;

          JenkinsPluginProject prj = JenkinsPluginProject.create(art, monitor);
          if (prj != null) {
            deps.add(prj);
            continue;
          }

          IMavenProjectFacade facade = MavenPlugin.getMavenProjectRegistry().getMavenProject(art.getGroupId(),
              art.getArtifactId(), art.getVersion());
          if (facade != null) {
            // just a plain dependency, skip it
            continue;
          }

          // when a plugin depends on another plugin, it doesn't specify the
          // type as hpi or jpi, so we need to resolve its POM to see it
          File pom = resolveIfNeeded(art.getGroupId(), art.getArtifactId(), art.getVersion(), "pom", mavenProject,
              monitor);
          Model depModel = maven.readModel(pom);

          if (isJenkinsType(depModel.getPackaging())) {
            File hpi = resolveIfNeeded(art.getGroupId(), art.getArtifactId(), art.getVersion(), "hpi", mavenProject,
                monitor);
            deps.add(new JenkinsPluginArtifact(art.getGroupId(), art.getArtifactId(), art.getVersion(), hpi));
          }
        }
        return null;
      }
    }, monitor);

    if (monitor.isCanceled())
      return Collections.emptyList();

    return deps;
  }

  private File resolveIfNeeded(String groupId, String artifactId, String version, String type, MavenProject project,
      IProgressMonitor monitor) throws CoreException {

    IMaven maven = MavenPlugin.getMaven();
    File repoBasedir = new File(maven.getLocalRepositoryPath());

    String pomLocation = maven.getArtifactPath(maven.getLocalRepository(), groupId, artifactId, version, type, null);
    File file = new File(repoBasedir, pomLocation);

    // in most cases it should be there
    if (file.exists()) {
      return file;
    }

    // but if it's not..
    monitor.subTask("Resolving " + artifactId + ":" + version + " " + type);
    return MavenPlugin.getMaven().resolve(groupId, artifactId, version, type, null,
        mavenProject.getRemoteArtifactRepositories(), monitor).getFile();
  }

  public JenkinsPluginArtifact findJenkinsWar(IProgressMonitor monitor)
      throws CoreException {

    String jenkinsWarId = getHPIMojoParameter("test-hpl", "jenkinsWarId", String.class, monitor);

    Set<Artifact> artifacts = facade.getMavenProject().getArtifacts();
    for (Artifact a : artifacts) {
      System.out.println(a.toString());
      boolean match;
      if (jenkinsWarId != null)
        match = (a.getGroupId() + ':' + a.getArtifactId()).equals(jenkinsWarId);
      else
        match = a.getArtifactId().equals("jenkins-war") || a.getArtifactId().equals("hudson-war");
      if (match) {
        IMavenProjectFacade warProject = MavenPlugin.getMavenProjectRegistry().getMavenProject(a.getGroupId(),
            a.getArtifactId(), a.getVersion());
        File f;
        if (warProject != null) {
          f = new File(warProject.getProject().getLocation().toOSString());
        } else {
          f = resolveIfNeeded(a.getGroupId(), a.getArtifactId(), a.getVersion(), "war", mavenProject, monitor);
        }
        return new JenkinsPluginArtifact(a.getGroupId(), a.getArtifactId(), a.getVersion(), f);
      }
    }
    return null;
  }

  public <T> T getHPIMojoParameter(String goal, String parameter, Class<T> asType, IProgressMonitor monitor)
      throws CoreException {
    return getMojoParameter(HPI_PLUGIN_GROUP_ID, HPI_PLUGIN_ARTIFACT_ID, goal, parameter, asType, monitor);
  }

  public String getLocalizerOutputDir(IProgressMonitor monitor) throws CoreException {
    return getMojoParameter(LOCALIZER_PLUGIN_GROUP_ID, LOCALIZER_PLUGIN_ARTIFACT_ID, "generate", "outputDirectory",
        String.class,
        monitor);
  }

  protected <T> T getMojoParameter(String groupId, String artifactId, String goal, String parameter, Class<T> asType,
      IProgressMonitor monitor) throws CoreException {
    List<MojoExecution> mojoExecutions = facade.getMojoExecutions(groupId, artifactId, monitor,
        goal);

    for (MojoExecution mojoExecution : mojoExecutions) {

      T value = MavenPlugin.getMaven().getMojoParameterValue(mavenProject, mojoExecution, parameter, asType,
          monitor);
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  public static JenkinsPluginProject create(IProject project, IProgressMonitor monitor) {
    return create(MavenPlugin.getMavenProjectRegistry().getProject(project), monitor);
  }

  public static JenkinsPluginProject create(Artifact art, IProgressMonitor monitor) {
    return create(
        MavenPlugin.getMavenProjectRegistry().getMavenProject(art.getGroupId(), art.getArtifactId(), art.getVersion()),
        monitor);
  }

  private static JenkinsPluginProject create(IMavenProjectFacade facade, IProgressMonitor monitor) {
    if (facade == null)
      return null;
    if (!isJenkinsType(facade.getPackaging()))
      return null;
    try {
      MavenProject mavenProject = facade.getMavenProject(monitor);
      return new JenkinsPluginProject(facade, mavenProject);
    } catch (CoreException e) {
      return null;
    }
  }

  private static boolean isJenkinsType(String type) {
    return TYPE_HPI.equals(type) || TYPE_JPI.equals(type);
  }

  public static List<JenkinsPluginProject> getProjects(IProgressMonitor monitor) {
    IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
    List<JenkinsPluginProject> jps = new ArrayList<>();

    for (IProject project : root.getProjects()) {
      JenkinsPluginProject jp = JenkinsPluginProject.create(project, monitor);
      if (jp != null) {
        jps.add(jp);
      }
    }

    return jps;
  }

  public List<String> getResources() {
    List<String> res = new ArrayList<>();
    for (Resource r : mavenProject.getBuild().getResources()) {
      String loc = facade.getProject().getLocation().append(r.getDirectory()).toOSString();
      res.add(loc);
    }
    return res;
  }

}
