package io.takari.m2e.jenkins;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
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
import org.apache.maven.project.artifact.MavenMetadataSource;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.embedder.ArtifactKey;
import org.eclipse.m2e.core.embedder.ICallable;
import org.eclipse.m2e.core.embedder.IMaven;
import org.eclipse.m2e.core.embedder.IMavenExecutionContext;
import org.eclipse.m2e.core.internal.embedder.MavenImpl;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.m2e.core.project.IMavenProjectRegistry;

import io.takari.m2e.jenkins.internal.JenkinsPlugin;
import io.takari.m2e.jenkins.runtime.PluginUpdateCenter;

public class JenkinsPluginProject implements IJenkinsPlugin {

  private static final String TYPE_HPI = "hpi";
  private static final String TYPE_JPI = "jpi";
  public static final String HPI_PLUGIN_GROUP_ID = "org.jenkins-ci.tools";
  public static final String HPI_PLUGIN_ARTIFACT_ID = "maven-hpi-plugin";

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

  private static IPath relativize(IProject project, IPath path) {
    return path.makeRelativeTo(project.getFullPath());
  }

  private File toFile(IPath projectRelativPath) {
    return facade.getProject().getLocation().append(projectRelativPath).toFile();
  }

  public IPath getHplLocation() {
    return relativize(facade.getProject(), facade.getOutputLocation().append("the.hpl"));
  }

  public IPath getTestHplLocation() {
    return relativize(facade.getProject(), facade.getTestOutputLocation().append("the.hpl"));
  }

  public IPath getHpiTrickLocation() {
    return facade.getProject().getFile("target/hpitrick.jar").getProjectRelativePath();
  }

  public IPath getTestDependenciesLocation() {
    return relativize(facade.getProject(), facade.getTestOutputLocation().append("test-dependencies"));
  }

  @Override
  public File getPluginFile(IProgressMonitor monitor, boolean regenerate) throws CoreException {
    return generateTestHpl(regenerate, monitor);
  }

  public void executeMojo(String groupId, String artifactId, String goal, IProgressMonitor monitor)
      throws CoreException {
    final List<MojoExecution> mojoExecutions = facade.getMojoExecutions(groupId, artifactId, monitor, goal);
    if (mojoExecutions == null || mojoExecutions.isEmpty()) {
      throw new IllegalStateException(
          "No executions for " + groupId + ":" + artifactId + " in " + facade.getArtifactKey());
    }

    final IMaven maven = MavenPlugin.getMaven();
    final IMavenProjectRegistry registry = MavenPlugin.getMavenProjectRegistry();

    registry.execute(facade, new ICallable<Void>() {
      @Override
      public Void call(IMavenExecutionContext context, IProgressMonitor monitor) throws CoreException {
        // context.execute(project, callable, monitor);
        for (MojoExecution mojoExecution : mojoExecutions) {
          maven.execute(facade.getMavenProject(), mojoExecution, monitor);
          break;
        }
        return null;
      }

    }, monitor);
  }

  public File generateTestHpl(boolean force, IProgressMonitor monitor) throws CoreException {
    File hpl = toFile(getHplLocation());
    File testHpl = toFile(getTestHplLocation());
    if (!hpl.exists() || !testHpl.exists()) {
      force = true;
    }
    if (!force) {
      try {
        String hplContent = new String(Files.readAllBytes(hpl.toPath()), "UTF-8");
        String testHplContent = new String(Files.readAllBytes(testHpl.toPath()), "UTF-8");
        if (!hplContent.equals(testHplContent)) {
          force = true;
        }
      } catch (IOException e) {
        force = true;
      }
    }

    if (!force) {
      return hpl;
    }

    final boolean doForce = force;

    final IMavenProjectRegistry registry = MavenPlugin.getMavenProjectRegistry();
    List<MojoExecution> mojoExecutions = facade.getMojoExecutions(HPI_PLUGIN_GROUP_ID, HPI_PLUGIN_ARTIFACT_ID, monitor,
        "test-hpl");
    final MojoExecution testHplMojo = mojoExecutions.isEmpty() ? null : mojoExecutions.get(0);

    if (testHplMojo != null) {
      try {
        return registry.execute(facade, new ICallable<File>() {
          @Override
          public File call(IMavenExecutionContext context, IProgressMonitor monitor) throws CoreException {
            return generateTestHpl(testHplMojo, doForce, monitor);
          }
        }, monitor);
      } catch (Exception e) {
        JenkinsPlugin.error("Error running test-hpl on " + facade.getProject().getName(), e);
      }
    }
    return null;
  }

  @SuppressWarnings("deprecation")
  public File generateTestHpl(MojoExecution mojoExecution, boolean force, IProgressMonitor monitor)
      throws CoreException {

    MavenProject mavenProject = getMavenProject(monitor);
    File testHpl = toFile(getTestHplLocation());
    File hpl = toFile(getHplLocation());

    if (!testHpl.exists() || force) {
      monitor.subTask("Generating .hpl for " + facade.getProject().getName());
      final IMaven maven = MavenPlugin.getMaven();

      Set<Artifact> depArts = mavenProject.getDependencyArtifacts();

      Set<Artifact> newDepArts = depArts;

      if (newDepArts == null) {
        try {
          @SuppressWarnings("restriction")
          ArtifactFactory artifactFactory = ((MavenImpl) maven).getPlexusContainer().lookup(ArtifactFactory.class);
          newDepArts = MavenMetadataSource.createArtifacts(artifactFactory, mavenProject.getDependencies(), null, null,
              mavenProject);
        } catch (Exception e) {
          throw new IllegalStateException(e);
        }
      }

      mavenProject.setDependencyArtifacts(JenkinsPluginProject.fixArtifactFiles(newDepArts, monitor));

      try {
        maven.execute(mavenProject, mojoExecution, monitor);
      } finally {
        mavenProject.setDependencyArtifacts(depArts);
      }
    }

    hpl.delete();
    if (testHpl.exists()) {
      try {
        Files.copy(testHpl.toPath(), hpl.toPath());
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }

    return hpl;
  }

  /**
   * Dirty attempt to fix maven-hpi-plugin's MavenArtifact#getActualArtifactId()
   * which doesn't work without actual artifact files
   */
  public static Set<Artifact> fixArtifactFiles(Set<Artifact> arts, IProgressMonitor monitor) throws CoreException {
    if (arts == null) {
      return null;
    }
    Set<Artifact> newArts = new LinkedHashSet<>();
    for (Artifact art : arts) {
      JenkinsPluginProject jdep = JenkinsPluginProject.create(art.getGroupId(), art.getArtifactId(), art.getVersion(),
          monitor);
      if (jdep != null) {
        try {
          File fixJar = jdep.generateFixJar(false, monitor);
          art = new DefaultArtifact(art.getGroupId(), art.getArtifactId(), art.getVersion(), art.getScope(),
              art.getType(), art.getClassifier(), art.getArtifactHandler());
          art.setFile(fixJar);
          art.setResolved(true);
        } catch (IOException e) {
          JenkinsPlugin.error("Error generating temp jar for " + jdep.getFacade().getProject().getName(), e);
        }
      }
      newArts.add(art);
    }
    return newArts;
  }

  /**
   * Dirty attempt to fix maven-hpi-plugin's MavenArtifact#getActualArtifactId()
   * which doesn't work without actual artifact files
   */
  public File generateFixJar(boolean force, IProgressMonitor monitor) throws CoreException, IOException {
    File tempJar = toFile(getHpiTrickLocation());
    if (tempJar.exists()) {
      if (!force) {
        return tempJar;
      }
      tempJar.delete();
    }

    String v = facade.getArtifactKey().getVersion();
    String pluginVersionDescription = getMojoParameter(HPI_PLUGIN_GROUP_ID, HPI_PLUGIN_ARTIFACT_ID, "test-hpl",
        "pluginVersionDescription", String.class, monitor);
    if (v.endsWith("-SNAPSHOT") && pluginVersionDescription == null) {
      String dt = new SimpleDateFormat("MM/dd/yyyy HH:mm").format(new Date());
      pluginVersionDescription = "private-" + dt + "-" + System.getProperty("user.name");
    }
    if (pluginVersionDescription != null) {
      v += " (" + pluginVersionDescription + ")";
    }

    Manifest mf = new Manifest();
    Attributes mainAttributes = mf.getMainAttributes();
    mainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
    mainAttributes.putValue("Short-Name", facade.getArtifactKey().getArtifactId());
    mainAttributes.putValue("Plugin-Version", v);

    tempJar.getParentFile().mkdirs();
    tempJar.createNewFile();
    try (JarOutputStream jar = new JarOutputStream(new FileOutputStream(tempJar), mf)) {
      jar.flush();
    }
    return tempJar;
  }

  public List<String> getResources(IProgressMonitor monitor) throws CoreException {
    List<String> res = new ArrayList<>();
    for (Resource r : getMavenProject(monitor).getBuild().getResources()) {
      String loc = facade.getProject().getLocation().append(r.getDirectory()).toOSString();
      res.add(loc);
    }
    return res;
  }

  public void generateTestDependenciesIndex(IMavenExecutionContext context, boolean force, IProgressMonitor monitor)
      throws CoreException, IOException {

    // mimic TestDependencyMojo

    File testDir = toFile(getTestDependenciesLocation());
    testDir.mkdirs();

    File idxFile = new File(testDir, "index");
    Files.deleteIfExists(idxFile.toPath());
    try (Writer w = new OutputStreamWriter(new FileOutputStream(idxFile), "UTF-8")) {
      for (PluginDependency dep : findPluginDependencies(null, context, monitor)) {
        IJenkinsPlugin plugin = dep.getPlugin();
        if (plugin instanceof JenkinsPluginProject) {
          continue;
        }

        File pluginFile = plugin.getPluginFile(monitor, false);
        String artifactId = getActualArtifactId(pluginFile);
        File dst = new File(testDir, artifactId + ".hpi");
        Files.deleteIfExists(dst.toPath());
        Files.copy(pluginFile.toPath(), dst.toPath());
        w.write(artifactId + "\n");
      }
    }
  }

  private String getActualArtifactId(File f) throws IOException {
    try (JarFile jf = new JarFile(f)) {
      return jf.getManifest().getMainAttributes().getValue("Short-Name");
    }
  }

  private List<PluginDependency> findPluginDependencies(final PluginUpdateCenter updates,
      IMavenExecutionContext context, IProgressMonitor monitor) throws CoreException {
    MavenProject mp = getMavenProject(monitor);
    List<PluginDependency> deps = new ArrayList<>();

    for (Artifact art : mp.getArtifacts()) {
      if (monitor.isCanceled())
        break;

      ArtifactKey ak = getPlugin(updates, art.getGroupId(), art.getArtifactId(), art.getVersion());

      IJenkinsPlugin jp = resolvePlugin(ak.getGroupId(), ak.getArtifactId(), ak.getVersion(), mp, context, monitor);
      if (jp != null) {
        deps.add(new PluginDependency(jp, isTestScope(art.getScope()), false));
      }
    }
    if (updates != null) {
      return correctVersions(deps, context, updates, monitor);
    }
    return deps;
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
      MavenProject mp = readProject(pom, context, getRemoteArtifactRepositories(containingProject, monitor));
      if (mp != null) {
        return new JenkinsPluginArtifact(mp, this);
      }
      JenkinsPlugin.error("Cannot read maven project " + groupId + ":" + artifactId + ":" + version);
    }

    return null;
  }

  protected boolean isTestScope(String scope) {
    return "test".equals(scope);
  }

  private MavenProject readProject(File pom, IMavenExecutionContext context,
      List<ArtifactRepository> remoteRepositories) throws CoreException {
    ProjectBuildingRequest req = context.newProjectBuildingRequest();
    req.setProcessPlugins(false);
    req.setResolveDependencies(false);
    req.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
    req.setRemoteRepositories(remoteRepositories);
    MavenExecutionResult res = MavenPlugin.getMaven().readMavenProject(pom, req);
    for (Throwable t : res.getExceptions()) {
      JenkinsPlugin.error("Error reading pom " + pom, t);
    }
    return res.getProject();
  }

  protected List<PluginDependency> correctVersions(List<PluginDependency> plugins, IMavenExecutionContext context,
      PluginUpdateCenter updates, IProgressMonitor monitor) throws CoreException {

    // transitive dependency plugins might contain optional dependencies on other
    // plugins and will not be happy if older versions of such dependencies are
    // installed, so bump their versions

    Map<ArtifactKey, PluginContainer> pluginMap = new HashMap<>();
    for (PluginDependency jp : plugins) {
      pluginMap.put(key(jp.getPlugin()), new PluginContainer(jp));
    }

    // plugins might get processed multiple times
    Deque<PluginDependency> q = new LinkedList<>(plugins);

    while (!q.isEmpty()) {
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

        ArtifactVersion dver = ver(dep.getVersion());

        ArtifactKey ak = getPlugin(updates, dep.getGroupId(), dep.getArtifactId(), dver.toString());

        IJenkinsPlugin newJp = resolvePlugin(ak.getGroupId(), ak.getArtifactId(), ak.getVersion(), mp, context,
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
    for (PluginContainer pc : pluginMap.values()) {
      result.add(pc.getDependency());
    }
    return result;
  }

  private static ArtifactKey getPlugin(PluginUpdateCenter updates, String groupId, String artifactId, String version) {
    if (updates != null) {
      String newVersion = updates.getVersion(groupId, artifactId);
      if (newVersion == null && groupId.equals("org.jvnet.hudson.plugins")) {
        // try with new groupId
        String newGroupId = "org.jenkins-ci.plugins";
        newVersion = updates.getVersion(newGroupId, artifactId);
        if (newVersion != null) {
          groupId = newGroupId;
        }
      }

      if (newVersion != null) {
        version = newVersion;
      }
    }
    return new ArtifactKey(groupId, artifactId, version, null);
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

  File resolveIfNeeded(String groupId, String artifactId, String version, String type, MavenProject project,
      IProgressMonitor monitor) throws CoreException {
    return resolve(groupId, artifactId, version, type, project, false, monitor);
  }

  File resolve(String groupId, String artifactId, String version, String type, MavenProject project, boolean force,
      IProgressMonitor monitor) throws CoreException {
    IMaven maven = MavenPlugin.getMaven();
    File repoBasedir = new File(maven.getLocalRepositoryPath());

    String fileLocation = maven.getArtifactPath(maven.getLocalRepository(), groupId, artifactId, version, type, null);
    File file = new File(repoBasedir, fileLocation);

    // in most cases it should be there
    if (file.exists() && !force) {
      return file;
    }

    // but if it's not..
    monitor.subTask("Resolving " + artifactId + ":" + version + " " + type);
    return MavenPlugin.getMaven()
        .resolve(groupId, artifactId, version, type, null, getRemoteArtifactRepositories(project, monitor), monitor)
        .getFile();
  }

  private List<ArtifactRepository> getRemoteArtifactRepositories(MavenProject containing, IProgressMonitor monitor)
      throws CoreException {
    List<ArtifactRepository> rootRepos = containing.getRemoteArtifactRepositories();
    MavenProject rootProject = getMavenProject(monitor);
    if (containing == rootProject) {
      return rootRepos;
    }

    List<ArtifactRepository> allRepos = new ArrayList<>(rootRepos);
    allRepos.addAll(containing.getRemoteArtifactRepositories());

    return allRepos;
  }

  public Artifact findJenkinsWar(IProgressMonitor monitor, String forceVersion, boolean download) throws CoreException {

    String jenkinsWarId = getMojoParameter(HPI_PLUGIN_GROUP_ID, HPI_PLUGIN_ARTIFACT_ID, "test-hpl", "jenkinsWarId",
        String.class, monitor);

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
          String version = forceVersion != null ? forceVersion : a.getVersion();

          IMavenProjectFacade warProject = MavenPlugin.getMavenProjectRegistry().getMavenProject(a.getGroupId(),
              a.getArtifactId(), version);

          a = new DefaultArtifact(a.getGroupId(), a.getArtifactId(), version, null, "war", "", null);
          File f;
          if (warProject != null) {
            f = new File(warProject.getProject().getLocation().toOSString());
          } else {
            f = resolveIfNeeded(a.getGroupId(), a.getArtifactId(), version, "war", mp, monitor);
          }
          a.setFile(f);
        }
        return a;
      }
    }
    return null;
  }

  public <T> T getMojoParameter(String groupId, String artifactId, String goal, String parameter, Class<T> asType,
      IProgressMonitor monitor) throws CoreException {
    List<MojoExecution> mojoExecutions = facade.getMojoExecutions(groupId, artifactId, monitor, goal);

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

  public static JenkinsPluginProject create(IMavenProjectFacade facade, IProgressMonitor monitor) {
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
