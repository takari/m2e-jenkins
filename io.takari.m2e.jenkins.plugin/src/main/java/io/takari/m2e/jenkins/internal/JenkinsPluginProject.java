package io.takari.m2e.jenkins.internal;

import java.io.File;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.Restriction;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Resource;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.embedder.ArtifactKey;
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

  private JenkinsPluginProject(IMavenProjectFacade facade) {
    this.facade = facade;
  }

  public IProject getProject() {
    return facade.getProject();
  }

  public IMavenProjectFacade getFacade() {
    return facade;
  }

  @Override
  public String getGroupId() {
    return facade.getArtifactKey().getGroupId();
  }

  @Override
  public String getArtifactId() {
    return facade.getArtifactKey().getArtifactId();
  }

  @Override
  public String getVersion() {
    return facade.getArtifactKey().getVersion();
  }

  @Override
  public MavenProject getMavenProject(IProgressMonitor monitor) throws CoreException {
    if (mavenProject == null) {
      mavenProject = facade.getMavenProject(monitor);
    }
    return mavenProject;
  }

  @Override
  public File getPluginFile(IProgressMonitor monitor) throws CoreException {
    // assume that test-hpl mojo writes the file
    File testDir = new File(getMavenProject(monitor).getBuild().getTestOutputDirectory());
    File hpl = new File(testDir, "the.hpl");

    if (!hpl.exists()) {
      generatePluginFile(monitor);
    }

    return hpl;
  }

  private void generatePluginFile(IProgressMonitor monitor) throws CoreException {
    monitor.subTask("Generating .hpl for " + facade.getProject().getName());
    final List<MojoExecution> mojoExecutions = facade.getMojoExecutions(HPI_PLUGIN_GROUP_ID, HPI_PLUGIN_ARTIFACT_ID,
        monitor,
        "test-hpl");

    MavenPlugin.getMavenProjectRegistry().execute(facade, new ICallable<Void>() {
      @Override
      public Void call(IMavenExecutionContext context, IProgressMonitor monitor) throws CoreException {
        // context.execute(project, callable, monitor);
        for (MojoExecution mojoExecution : mojoExecutions) {
          MavenPlugin.getMaven().execute(facade.getMavenProject(), mojoExecution, monitor);
          break;
        }
        return null;
      }
    }, monitor);
  }

  public List<String> getResources(IProgressMonitor monitor) throws CoreException {
    List<String> res = new ArrayList<>();
    for (Resource r : getMavenProject(monitor).getBuild().getResources()) {
      String loc = facade.getProject().getLocation().append(r.getDirectory()).toOSString();
      res.add(loc);
    }
    return res;
  }

  public List<PluginDependency> findPluginDependencies(IProgressMonitor monitor)
      throws CoreException {
    final IMaven maven = MavenPlugin.getMaven();

    return maven.execute(false, true /* force update */, new ICallable<List<PluginDependency>>() {
      @Override
      public List<PluginDependency> call(IMavenExecutionContext context, IProgressMonitor monitor)
          throws CoreException {
        MavenProject mp = getMavenProject(monitor);
        List<PluginDependency> deps = new ArrayList<>();

        for (Artifact art : mp.getArtifacts()) {
          if (monitor.isCanceled())
            break;
          IJenkinsPlugin jp = resolvePlugin(art.getGroupId(), art.getArtifactId(), art.getVersion(), mp,
              context, monitor);
          if (jp != null) {
            deps.add(new PluginDependency(jp, isTestScope(art.getScope()), false));
          }
        }
        return correctVersions(deps, context, monitor);
      }

    }, monitor);
  }

  private IJenkinsPlugin resolvePlugin(String groupId, String artifactId, String version,
      MavenProject containingProject, IMavenExecutionContext context, IProgressMonitor monitor) throws CoreException {
    JenkinsPluginProject prj = JenkinsPluginProject.create(groupId, artifactId, version, monitor);
    if (prj != null) {
      return prj;
    }

    IMavenProjectFacade facade = MavenPlugin.getMavenProjectRegistry().getMavenProject(groupId, artifactId, version);
    if (facade != null) {
      // just a plain dependency
      return null;
    }

    // when a plugin depends on another plugin, it doesn't specify the
    // type as hpi or jpi, so we need to resolve its POM to see it
    File pom = resolveIfNeeded(groupId, artifactId, version, "pom", containingProject, monitor);

    Model model = MavenPlugin.getMaven().readModel(pom);

    if (isJenkinsType(model.getPackaging())) {
      MavenProject mp = readProject(pom, context);
      return new JenkinsPluginArtifact(mp, containingProject);
    }

    return null;
  }

  protected boolean isTestScope(String scope) {
    return "test".equals(scope);
  }

  private MavenProject readProject(File pom, IMavenExecutionContext context) throws CoreException {
    ProjectBuildingRequest req = context.newProjectBuildingRequest();
    req.setProcessPlugins(false);
    req.setResolveDependencies(false);
    req.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
    MavenExecutionResult res = MavenPlugin.getMaven().readMavenProject(pom, req);
    return res.getProject();
  }

  protected List<PluginDependency> correctVersions(List<PluginDependency> plugins,
      IMavenExecutionContext context,
      IProgressMonitor monitor) throws CoreException {
    
    // transitive dependency plugins might contain optional dependencies on other 
    // plugins and will not be happy if older versions of such dependencies are
    // installed, so bump their versions
    
    Map<ArtifactKey, PluginContainer> pluginMap = new HashMap<>();
    for (PluginDependency jp : plugins) {
      pluginMap.put(key(jp.getPlugin()), new PluginContainer(jp));
    }
    
    // plugins might get processed multiple times
    Deque<PluginDependency> q = new LinkedList<>(plugins);
    
    while(!q.isEmpty()) {
      PluginDependency pd = q.removeFirst();
      IJenkinsPlugin jp = pd.getPlugin();
      MavenProject mp = jp.getMavenProject(monitor);
      
      List<Dependency> deps = mp.getDependencies();

      for (Dependency dep : deps) {
        ArtifactKey key = key(dep);
        PluginContainer dc = pluginMap.get(key);

        boolean existingIsOptional = (dc == null ? true : dc.getDependency().isOptional());
        boolean considerAsOptional = pd.isOptional() || dep.isOptional();
        boolean optional = existingIsOptional && considerAsOptional;

        boolean existingIsTestScope = (dc == null ? true : dc.getDependency().isTestScope());
        boolean considerAsTestScope = pd.isTestScope() || isTestScope(dep.getScope());
        boolean testScope = existingIsTestScope && considerAsTestScope;

        ArtifactVersion dver = ver(dep);

        IJenkinsPlugin newJp = resolvePlugin(dep.getGroupId(), dep.getArtifactId(), dver.toString(), mp, context,
            monitor);

        if (dc == null && newJp == null)
          continue;

        if (dc != null) {
          if (dver.compareTo(dc.getVersion()) > 0) {

            if (newJp == null) {
              throw new IllegalStateException("Cannot resolve a newer version of existing plugin " + dep);
            }

            PluginDependency newPd = new PluginDependency(newJp, testScope, optional, true /* override */);
            q.remove(dc.getDependency());
            dc.setPlugin(newPd);
            q.add(newPd);
          }
        } else {
          PluginDependency newPd = new PluginDependency(newJp, testScope, optional);
          pluginMap.put(key, new PluginContainer(newPd));
          q.add(newPd);
        }
      }
    }
    
    List<PluginDependency> result = new ArrayList<>();
    for(PluginContainer pc: pluginMap.values()) {
      result.add(pc.getDependency());
    }
    return result;
  }

  private static ArtifactVersion ver(Dependency dep) {
    return ver(dep.getVersion());
  }

  private static ArtifactVersion ver(String ver) {
    VersionRange r;
    try {
      r = VersionRange.createFromVersionSpec(ver);
    } catch (InvalidVersionSpecificationException e) {
      throw new IllegalStateException("Can't parse version " + ver, e);
    }
    if (r.getRecommendedVersion() != null)
      return r.getRecommendedVersion();

    for (Restriction rx : r.getRestrictions()) {
      if (rx.isLowerBoundInclusive()) {
        return rx.getLowerBound();
      }
      if (rx.isUpperBoundInclusive()) {
        return rx.getUpperBound();
      }
    }
    throw new IllegalStateException("Can't decide which version to use " + ver);
  }

  private static ArtifactKey key(Dependency dep) {
    return new ArtifactKey(dep.getGroupId(), dep.getArtifactId(), null, null);
  }

  private static ArtifactKey key(IJenkinsPlugin jp) {
    return new ArtifactKey(jp.getGroupId(), jp.getArtifactId(), null, null);
  }

  static File resolveIfNeeded(String groupId, String artifactId, String version, String type, MavenProject project,
      IProgressMonitor monitor) throws CoreException {

    IMaven maven = MavenPlugin.getMaven();
    File repoBasedir = new File(maven.getLocalRepositoryPath());

    String fileLocation = maven.getArtifactPath(maven.getLocalRepository(), groupId, artifactId, version, type, null);
    File file = new File(repoBasedir, fileLocation);

    // in most cases it should be there
    if (file.exists()) {
      return file;
    }

    // but if it's not..
    monitor.subTask("Resolving " + artifactId + ":" + version + " " + type);
    return MavenPlugin.getMaven().resolve(groupId, artifactId, version, type, null,
        project.getRemoteArtifactRepositories(), monitor).getFile();
  }

  public Artifact findJenkinsWar(IProgressMonitor monitor, boolean download)
      throws CoreException {

    String jenkinsWarId = getHPIMojoParameter("test-hpl", "jenkinsWarId", String.class, monitor);

    MavenProject mp = getMavenProject(monitor);
    Set<Artifact> artifacts = mp.getArtifacts();

    for (Artifact a : artifacts) {
      boolean match;
      if (jenkinsWarId != null)
        match = (a.getGroupId() + ':' + a.getArtifactId()).equals(jenkinsWarId);
      else
        match = a.getArtifactId().equals("jenkins-war") || a.getArtifactId().equals("hudson-war");
      if (match) {
        if (download) {
          IMavenProjectFacade warProject = MavenPlugin.getMavenProjectRegistry().getMavenProject(a.getGroupId(),
              a.getArtifactId(), a.getVersion());

          a = new DefaultArtifact(a.getGroupId(), a.getArtifactId(), a.getVersion(), null, "war", "", null);
          File f;
          if (warProject != null) {
            f = new File(warProject.getProject().getLocation().toOSString());
          } else {
            f = resolveIfNeeded(a.getGroupId(), a.getArtifactId(), a.getVersion(), "war", mp, monitor);
          }
          a.setFile(f);
        }
        return a;
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

      T value = MavenPlugin.getMaven().getMojoParameterValue(getMavenProject(monitor), mojoExecution, parameter, asType,
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

  public static JenkinsPluginProject create(String groupId, String artifactId, String version,
      IProgressMonitor monitor) {
    return create(MavenPlugin.getMavenProjectRegistry().getMavenProject(groupId, artifactId, version), monitor);
  }

  private static JenkinsPluginProject create(IMavenProjectFacade facade, IProgressMonitor monitor) {
    if (facade == null)
      return null;
    if (!isJenkinsType(facade.getPackaging()))
      return null;
    return new JenkinsPluginProject(facade);
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

  private static class PluginContainer {
    PluginDependency dependency;
    ArtifactVersion version;

    public PluginContainer(PluginDependency dependency) {
      setPlugin(dependency);
    }

    public void setPlugin(PluginDependency dependency) {
      this.dependency = dependency;
      this.version = new DefaultArtifactVersion(dependency.getPlugin().getVersion());
    }

    public PluginDependency getDependency() {
      return dependency;
    }

    public ArtifactVersion getVersion() {
      return version;
    }
  }
}
