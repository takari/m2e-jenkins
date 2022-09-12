package io.takari.m2e.jenkins.plugin;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.embedder.ICallable;
import org.eclipse.m2e.core.embedder.IMaven;
import org.eclipse.m2e.core.embedder.IMavenExecutionContext;
import org.eclipse.m2e.core.project.IMavenProjectFacade;

import io.takari.m2e.jenkins.internal.JenkinsPlugin;
import io.takari.m2e.jenkins.runtime.PluginUpdateCenter;

public class PluginDependenciesCalculator {

  private static final String STDGROUP_JENKINS = "org.jenkins-ci.plugins";
  private static final String STDGROUP_HUDSON = "org.jvnet.hudson.plugins";
  private static final String TYPE_HPI = "hpi";
  private static final String TYPE_JPI = "jpi";

  private PluginUpdateCenter updates;
  private List<PinnedPlugin> pinnedPlugins = new ArrayList<>();
  private String jenkinsVersion;
  private boolean includeOptional;
  private boolean includeTestScope;

  public PluginDependenciesCalculator useLatestVersions(PluginUpdateCenter updates) {
    this.updates = updates;
    return this;
  }

  public PluginDependenciesCalculator withPinnedPlugin(IJenkinsPlugin plugin, boolean upperBound) {
    pinnedPlugins.add(new PinnedPlugin(plugin, upperBound));
    return this;
  }

  public PluginDependenciesCalculator jenkinsVersion(String jenkinsVersion) {
    this.jenkinsVersion = jenkinsVersion;
    return this;
  }

  public PluginDependenciesCalculator includeOptional(boolean includeOptional) {
    this.includeOptional = includeOptional;
    return this;
  }

  public PluginDependenciesCalculator includeTestScope(boolean includeTestScope) {
    this.includeTestScope = includeTestScope;
    return this;
  }

  public DependenciesResult calculate(boolean forceUpdates, IProgressMonitor monitor) throws CoreException {
    return MavenPlugin.getMaven().execute(false, forceUpdates, new ICallable<DependenciesResult>() {
      @Override
      public DependenciesResult call(IMavenExecutionContext context, IProgressMonitor monitor) throws CoreException {
        try {
          CalculationContext ctx = new CalculationContext(context, monitor);
          DependenciesResult res = doCalculate(ctx);

          if (ctx.hasErrors()) {
            for (Map.Entry<GAV, List<String>> e : ctx.errors.entrySet()) {
              GAV gav = e.getKey();
              for (String msg : e.getValue()) {
                if (gav != null) {
                  JenkinsPlugin.error(gav + ": " + msg);
                } else {
                  JenkinsPlugin.error(msg);
                }
              }
            }
            throw new CoreException(
                new Status(IStatus.ERROR, JenkinsPlugin.ID, "Errors while calculating launch plan, consult error log"));
          }

          return res;
        } catch (IOException e) {
          throw new CoreException(new Status(IStatus.ERROR, JenkinsPlugin.ID, e.getMessage(), e));
        }
      }
    }, monitor);
  }

  private DependenciesResult doCalculate(CalculationContext ctx) throws CoreException, IOException {

    // maven employs a 'nearest' dependency resolution strategy, but jenkins
    // requires the lower bound of dependency is met at runtime, so for
    // A->C:1.0,
    // B->C:2.0, we'd need a C:2.0 at runtime

    // find out required jenkins version from plugin selection
    String jenkinsVer = jenkinsVersion;
    for (PinnedPlugin pp : pinnedPlugins) {
      GAV gav = GAV.from(pp.plugin);
      String newVer = ctx.getJenkinsVersion(gav);

      if (jenkinsVersion != null && cmp(newVer, jenkinsVersion) > 0) {
        ctx.error(null, "Requested jenkins core `" + jenkinsVersion + "` is older than `" + newVer
            + "` required by pinned plugin " + gav);
      }

      if (jenkinsVer == null || cmp(newVer, jenkinsVer) > 0) {
        jenkinsVer = newVer;
      }
    }

    // set plugins' jenkins core version as the baseline
    jenkinsVersion = jenkinsVer;

    // first pass: map requested plugins and their dependencies
    for (PinnedPlugin pp : pinnedPlugins) {
      ctx.managePlugin(pp.plugin, pp.upperBound, new LinkedHashSet<GA>());
    }

    // second pass over managed deps: add any required detached dependencies
    Collection<ManagedVersion> mngs = new ArrayList<>(ctx.management.values());
    for (ManagedVersion mng : mngs) {
      GAV gav = mng.gav;
      List<Dependency> deps = ctx.getDependencies(gav);

      String pluginJenkinsVer = ctx.getJenkinsVersion(gav);
      if (pluginJenkinsVer == null) {
        continue;
      }
      List<Dependency> detachedDeps = getDetachedDependencies(jenkinsVer, pluginJenkinsVer);

      for (Dependency detDep : detachedDeps) {
        IJenkinsPlugin depPlugin = ctx.resolvePlugin(detDep.gav);
        ctx.managePlugin(depPlugin, new LinkedHashSet<GA>());
      }

      deps.addAll(detachedDeps);
    }

    // third pass: collect all plugins
    Deque<GA> queue = new LinkedList<>();
    for (PinnedPlugin pp : pinnedPlugins) {
      queue.offer(GA.from(pp.plugin));
    }

    Set<GAV> memento = new HashSet<>();
    List<IJenkinsPlugin> collectedPlugins = new ArrayList<>();
    while (!queue.isEmpty()) {
      GA ga = queue.poll();
      ManagedVersion mng = ctx.management.get(ga);
      if (!memento.add(mng.gav)) {
        continue;
      }

      collectedPlugins.add(ctx.resolvePlugin(mng.gav));
      List<Dependency> deps = ctx.getDependencies(mng.gav);
      for (Dependency dep : deps) {
        if (!includeOptional && dep.optional) {
          continue;
        }
        if (!includeTestScope && dep.test) {
          continue;
        }
        queue.offer(dep.gav.ga);
      }
    }

    Artifact jenkinsWar = anyProject().findJenkinsWar(ctx.monitor, jenkinsVer, true);
    return new DependenciesResult(collectedPlugins, jenkinsWar);
  }

  private GAV latest(GA ga) {
    if (updates == null) {
      return null;
    }
    PluginUpdates pu = new PluginUpdates(updates, jenkinsVersion);
    String ver;
    if (ga.isStd()) {
      ver = pu.getVersion(STDGROUP_JENKINS, ga.getArtifactId());
      if (ver != null) {
        return new GAV(STDGROUP_JENKINS, ga.getArtifactId(), ver);
      }
      ver = pu.getVersion(STDGROUP_HUDSON, ga.getArtifactId());
      if (ver != null) {
        return new GAV(STDGROUP_HUDSON, ga.getArtifactId(), ver);
      }
    } else {
      ver = pu.getVersion(ga.getGroupId(), ga.getArtifactId());
      if (ver != null) {
        return new GAV(ga, ver);
      }
    }
    return null;
  }

  private JenkinsPluginProject anyProject() {
    for (PinnedPlugin pp : pinnedPlugins) {
      if (pp.plugin instanceof JenkinsPluginProject) {
        return (JenkinsPluginProject) pp.plugin;
      }
    }
    return null;
  }

  private static boolean isJenkinsType(String type) {
    return TYPE_HPI.equals(type) || TYPE_JPI.equals(type);
  }

  private static boolean isTestScope(String scope) {
    return "test".equals(scope);
  }

  private class CalculationContext {
    final IMavenExecutionContext mctx;
    final IProgressMonitor monitor;
    final Map<GA, ManagedVersion> management = new HashMap<>();
    final Map<GAV, List<Dependency>> dependencyCache = new HashMap<>();
    final Map<GAV, IJenkinsPlugin> pluginCache = new HashMap<>();
    final Map<GAV, List<String>> errors = new HashMap<>();

    public CalculationContext(IMavenExecutionContext mctx, IProgressMonitor monitor) {
      this.mctx = mctx;
      this.monitor = monitor;
    }

    public boolean hasErrors() {
      return !errors.isEmpty();
    }

    private boolean managePlugin(IJenkinsPlugin plugin, Set<GA> processed) throws CoreException {
      return managePlugin(plugin, false, processed);
    }

    private boolean managePlugin(IJenkinsPlugin plugin, boolean upperBound, Set<GA> processed) throws CoreException {
      GA ga = GA.from(plugin);
      if (processed.contains(ga)) {
        JenkinsPlugin.warning("Skipping cycle " + processed + " -> " + ga);
        return false;
      }
      processed.add(ga);

      try {
        ManagedVersion mng = manageBounds(ga, plugin.getVersion(), upperBound);
        if (mng == null) {
          return false;
        }

        GAV gav = new GAV(ga, mng.getVersion());
        IJenkinsPlugin jp = updatePlugin(gav, plugin);

        if (jp != null) {
          if (!dependencyCache.containsKey(gav)) {
            for (Dependency dep : getDependencies(gav)) {
              IJenkinsPlugin depPlugin = resolvePlugin(dep.gav);
              managePlugin(depPlugin, processed);
            }
          }
          return true;
        }
        return false;

      } finally {
        processed.remove(ga);
      }
    }

    private IJenkinsPlugin updatePlugin(GAV gav, IJenkinsPlugin candidate) throws CoreException {
      IJenkinsPlugin plugin;
      if (candidate.getVersion().equals(gav.getVersion())) {
        plugin = candidate;
        pluginCache.put(gav, plugin);
      } else {
        plugin = resolvePlugin(gav);
      }

      if (plugin == null) {
        error(gav, "Cannot resolve " + gav); // TODO
        return null;
      }

      return plugin;
    }

    private List<Dependency> getDependencies(GAV gav) throws CoreException {

      List<Dependency> deps = dependencyCache.get(gav);
      if (deps != null) {
        return deps;
      }

      deps = new ArrayList<>();

      IJenkinsPlugin plugin = resolvePlugin(gav);
      if (plugin == null) {
        error(gav, "Cannot resolve " + gav);
        return deps;
      }

      MavenProject pluginProject = plugin.getMavenProject(monitor);
      if (pluginProject == null) {
        error(gav, "Cannot get maven project for " + gav);
        return deps;
      }

      List<org.apache.maven.model.Dependency> mvnDeps = pluginProject.getDependencies();

      for (org.apache.maven.model.Dependency mvnDep : mvnDeps) {
        GAV dgav = GAV.from(mvnDep);

        // check if we need to break known cyclic dep
        if (breakCycle(gav.ga, dgav.ga)) {
          continue;
        }

        // check if it's a plugin
        IJenkinsPlugin depPlugin = resolvePlugin(dgav);
        if (depPlugin != null) {
          deps.add(new Dependency(dgav, mvnDep.isOptional(), isTestScope(mvnDep.getScope())));
        }
      }

      dependencyCache.put(gav, deps);

      return deps;
    }

    private ManagedVersion manageBounds(GA ga, String ver, boolean upperBound) {
      ManagedVersion mng = management.get(ga);
      GAV gav = new GAV(ga, ver);
      if (!upperBound) {

        // check latest version
        GAV newGav = latest(ga);

        // do not update to latest if we depend on a newer version than what is
        // available on update site
        if (newGav != null && cmp(newGav.getVersion(), gav.getVersion()) < 0) {
          newGav = null;
        }
        if (newGav != null) {
          if (mng != null && mng.upperBound && cmp(newGav.getVersion(), mng.getVersion()) > 0) {
            newGav = mng.gav;
          }
          gav = newGav;
        }
      }

      if (mng != null) {
        String mngVer = mng.getVersion();
        if (mng.upperBound && cmp(gav.getVersion(), mngVer) > 0) {
          error(null, ""); // TODO
          gav = mng.gav;
        }
        if (cmp(gav.getVersion(), mngVer) < 0) {
          gav = mng.gav;
        }
        upperBound |= mng.upperBound;
      }

      mng = new ManagedVersion(gav, upperBound);
      management.put(ga, mng);
      return mng;
    }

    private void error(GAV gav, String msg) {
      error(gav, msg, null);
    }

    private void error(GAV gav, String msg, Throwable t) {
      List<String> errs = errors.get(gav);
      if (errs == null) {
        errors.put(gav, errs = new ArrayList<>());
      }
      if (t != null) {
        StringWriter sw = new StringWriter();
        sw.append(msg).append(":");
        t.printStackTrace(new PrintWriter(sw, true));
        msg = sw.toString();
      }
      errs.add(msg);
    }

    private IJenkinsPlugin resolvePlugin(GAV gav) throws CoreException {
      IJenkinsPlugin cached = pluginCache.get(gav);
      if (cached != null) {
        return cached;
      }
      for (PinnedPlugin pp : pinnedPlugins) {
        if (pp.plugin instanceof JenkinsPluginProject) {
          ResolutionResult res = resolvePlugin(gav, (JenkinsPluginProject) pp.plugin);
          if (!res.resolved) {
            continue;
          }
          pluginCache.put(gav, res.plugin);
          return res.plugin;
        }
      }
      return null;
    }

    private ResolutionResult resolvePlugin(GAV gav, JenkinsPluginProject rootProject) throws CoreException {
      String groupId = gav.getGroupId();
      String artifactId = gav.getArtifactId();
      String version = gav.getVersion();

      JenkinsPluginProject prj = JenkinsPluginProject.create(groupId, artifactId, version, monitor);
      if (prj != null) {
        return new ResolutionResult(prj, true);
      }

      IMavenProjectFacade facade = MavenPlugin.getMavenProjectRegistry().getMavenProject(groupId, artifactId, version);
      if (facade != null) {
        // just a plain dependency
        return new ResolutionResult(null, true);
      }

      // when a plugin depends on another plugin, it doesn't specify the
      // type as hpi or jpi, so we need to resolve its POM to see it
      File pom = null;
      try {
        pom = resolveIfNeeded(gav, "pom", rootProject);
      } catch (CoreException e) {
      }
      if (pom == null && gav.ga.isStd()) {
        if (gav.getGroupId().equals(STDGROUP_HUDSON)) {
          gav = gav.withGroupId(STDGROUP_JENKINS);
        } else {
          gav = gav.withGroupId(STDGROUP_HUDSON);
        }
        try {
          pom = resolveIfNeeded(gav, "pom", rootProject);
        } catch (CoreException e) {
        }
      }
      if (pom == null) {
        return new ResolutionResult(null, false);
      }

      Model model = MavenPlugin.getMaven().readModel(pom);

      if (!isJenkinsType(model.getPackaging())) {
        return new ResolutionResult(null, true);
      }

      MavenProject mp = readProject(gav, pom, rootProject);
      if (mp != null) {
        return new ResolutionResult(new JenkinsPluginArtifact(mp, rootProject), true);
      }
      return new ResolutionResult(null, false);
    }

    private String getJenkinsVersion(GAV gav) throws CoreException, IOException {

      IJenkinsPlugin plugin = resolvePlugin(gav);
      if (plugin instanceof JenkinsPluginProject) {
        JenkinsPluginProject project = (JenkinsPluginProject) plugin;
        return project.findJenkinsWar(monitor, null, false).getVersion();
      }

      MavenProject mp = plugin.getMavenProject(monitor);
      Set<Artifact> artifacts = mp.getArtifacts();

      for (Artifact a : artifacts) {
        if (a.getArtifactId().equals("jenkins-war") || a.getArtifactId().equals("hudson-war")) {
          return a.getVersion();
        }
      }

      File file = resolve(gav, "jar", anyProject(), false);
      Manifest m;
      try (JarFile jar = new JarFile(file)) {
        m = jar.getManifest();
      }

      Attributes attrs = m.getMainAttributes();
      String jenkinsVersion = attrs.getValue("Jenkins-Version");
      if (jenkinsVersion == null) {
        jenkinsVersion = attrs.getValue("Hudson-Version");
      }
      if (jenkinsVersion.equals("null")) {
        jenkinsVersion = null;
      }
      return jenkinsVersion;
    }

    File resolveIfNeeded(GAV gav, String type, JenkinsPluginProject rootProject) throws CoreException {
      return resolve(gav, type, rootProject, mctx.getExecutionRequest().isUpdateSnapshots());
    }

    File resolve(GAV gav, String type, JenkinsPluginProject rootProject, boolean force) throws CoreException {
      IMaven maven = MavenPlugin.getMaven();
      File repoBasedir = new File(maven.getLocalRepositoryPath());

      String fileLocation = maven.getArtifactPath(maven.getLocalRepository(), gav.getGroupId(), gav.getArtifactId(),
          gav.getVersion(), type, null);
      File file = new File(repoBasedir, fileLocation);

      // in most cases it should be there
      if (file.exists() && !force) {
        return file;
      }

      // but if it's not..
      monitor.subTask("Resolving " + gav + " " + type);
      List<ArtifactRepository> repositories = getRemoteArtifactRepositories(rootProject);
      Artifact art = maven.resolve(gav.getGroupId(), gav.getArtifactId(), gav.getVersion(), type, null, repositories,
          monitor);
      return art.getFile();
    }

    private List<ArtifactRepository> getRemoteArtifactRepositories(JenkinsPluginProject rootProject)
        throws CoreException {
      return rootProject.getMavenProject(monitor).getRemoteArtifactRepositories();
    }

    private MavenProject readProject(GAV gav, File pom, JenkinsPluginProject rootProject) throws CoreException {
      List<ArtifactRepository> remoteRepositories = getRemoteArtifactRepositories(rootProject);
      ProjectBuildingRequest req = mctx.newProjectBuildingRequest();
      req.setProcessPlugins(false);
      req.setResolveDependencies(false);
      req.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
      req.setRemoteRepositories(remoteRepositories);
      MavenExecutionResult res = MavenPlugin.getMaven().readMavenProject(pom, req);
      boolean errors = false;
      for (Throwable t : res.getExceptions()) {
        error(gav, "Error reading pom " + pom, t);
        errors = true;
      }
      return errors ? null : res.getProject();
    }
  }

  private static class GA {
    final String groupId;
    final String artifactId;
    final String realGroupId;

    GA(String groupId, String artifactId) {
      if (groupId.equals(STDGROUP_HUDSON) || groupId.equals(STDGROUP_JENKINS)) {
        realGroupId = groupId;
        this.groupId = "<std>";
      } else {
        realGroupId = null;
        this.groupId = groupId;
      }

      this.artifactId = artifactId;
    }

    public String getGroupId() {
      return realGroupId != null ? realGroupId : groupId;
    }

    public String getArtifactId() {
      return artifactId;
    }

    public boolean isStd() {
      return realGroupId != null;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof GA) {
        GA that = (GA) obj;
        return this.groupId.equals(that.groupId) && this.artifactId.equals(that.artifactId);
      }
      return false;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + artifactId.hashCode();
      result = prime * result + groupId.hashCode();
      return result;
    }

    @Override
    public String toString() {
      return getGroupId() + ":" + artifactId;
    }

    public static GA from(IJenkinsPlugin p) {
      return new GA(p.getGroupId(), p.getArtifactId());
    }
  }

  private static class GAV {
    final GA ga;
    final String version;

    GAV(String groupId, String artifactId, String version) {
      this(new GA(groupId, artifactId), version);
    }

    GAV(GA ga, String version) {
      this.ga = ga;
      this.version = version;
    }

    public String getGroupId() {
      return ga.getGroupId();
    }

    public String getArtifactId() {
      return ga.getArtifactId();
    }

    public String getVersion() {
      return version;
    }

    public GAV withGroupId(String groupId) {
      return new GAV(groupId, getArtifactId(), version);
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof GAV) {
        GAV that = (GAV) obj;
        return this.ga.equals(that.ga) && this.version.equals(that.version);
      }
      return false;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ga.hashCode();
      result = prime * result + version.hashCode();
      return result;
    }

    @Override
    public String toString() {
      return ga + ":" + version;
    }

    public static GAV from(org.apache.maven.model.Dependency dep) {
      return new GAV(dep.getGroupId(), dep.getArtifactId(), dep.getVersion());
    }

    public static GAV from(IJenkinsPlugin plugin) {
      return new GAV(plugin.getGroupId(), plugin.getArtifactId(), plugin.getVersion());
    }
  }

  private static int cmp(String ver1, String ver2) {
    return new DefaultArtifactVersion(ver1).compareTo(new DefaultArtifactVersion(ver2));
  }

  private static boolean breakCycle(GA plugin, GA depPlugin) {
    Set<String> s = BREAK_CYCLES.get(plugin.artifactId);
    return s != null && s.contains(depPlugin.artifactId);
  }

  private static List<Dependency> getDetachedDependencies(String jenkinsVersion, String pluginJenkinsVersion) {
    List<Dependency> detachedDeps = new ArrayList<>();
    for (Map.Entry<String, DetachedPlugin> e : DETACHED.entrySet()) {
      DetachedPlugin d = e.getValue();
      if (cmp(jenkinsVersion, d.splitWhen) >= 0 // provisioned jenkins no longer
                                                // contains the detached plugin
          && cmp(pluginJenkinsVersion, d.splitWhen) < 0) { // plugin requires
                                                           // pre-split version
                                                           // of jenkins
        detachedDeps.add(new Dependency(new GAV(d.groupId, e.getKey(), d.requireVersion), false, false));
      }
    }
    return detachedDeps;
  }

  private static final Map<String, Set<String>> BREAK_CYCLES = map( //
      "script-security", set("matrix-auth", "windows-slaves", "antisamy-markup-formatter", "matrix-project"), //
      "credentials", set("matrix-auth", "windows-slaves"));

  private static final Map<String, DetachedPlugin> DETACHED = map( //
      "maven-plugin", new DetachedPlugin("1.297", "1.296", "org.jenkins-ci.main"), //
      "subversion", new DetachedPlugin("1.311", "1.0"), //
      "cvs", new DetachedPlugin("1.341", "0.1"), //
      "ant", new DetachedPlugin("1.431", "1.0"), //
      "javadoc", new DetachedPlugin("1.431", "1.0"), //
      "external-monitor-job", new DetachedPlugin("1.468", "1.0"), //
      "ldap", new DetachedPlugin("1.468", "1.0"), //
      "pam-auth", new DetachedPlugin("1.468", "1.0"), //
      "mailer", new DetachedPlugin("1.494", "1.2"), //
      "matrix-auth", new DetachedPlugin("1.536", "1.0.2"), //
      "windows-slaves", new DetachedPlugin("1.548", "1.0"), //
      "antisamy-markup-formatter", new DetachedPlugin("1.554", "1.0"), //
      "matrix-project", new DetachedPlugin("1.562", "1.0"), //
      "junit", new DetachedPlugin("1.578", "1.0"));

  private static class ManagedVersion {
    final GAV gav;
    final boolean upperBound;

    ManagedVersion(GAV gav, boolean upperBound) {
      this.gav = gav;
      this.upperBound = upperBound;
    }

    String getVersion() {
      return gav.getVersion();
    }
  }

  private static class Dependency {
    final GAV gav;
    final boolean optional;
    final boolean test;

    public Dependency(GAV gav, boolean optional, boolean test) {
      this.gav = gav;
      this.optional = optional;
      this.test = test;
    }
  }

  private static class PinnedPlugin {
    final IJenkinsPlugin plugin;
    final boolean upperBound;

    PinnedPlugin(IJenkinsPlugin plugin, boolean upperBound) {
      this.plugin = plugin;
      this.upperBound = upperBound;
    }
  }

  private static class ResolutionResult {
    final IJenkinsPlugin plugin;
    final boolean resolved;

    ResolutionResult(IJenkinsPlugin plugin, boolean resolved) {
      this.plugin = plugin;
      this.resolved = resolved;
    }
  }

  private static class DetachedPlugin {
    final String splitWhen;
    final String requireVersion;
    final String groupId;

    DetachedPlugin(String splitWhen, String requireVersion) {
      this(splitWhen, requireVersion, STDGROUP_JENKINS);
    }

    DetachedPlugin(String splitWhen, String requireVersion, String groupId) {
      this.splitWhen = splitWhen;
      this.requireVersion = requireVersion;
      this.groupId = groupId;
    }
  }

  public static class DependenciesResult {
    private List<IJenkinsPlugin> plugins;
    private Artifact jenkinsWar;

    public DependenciesResult(List<IJenkinsPlugin> plugins, Artifact jenkinsWar) {
      this.plugins = plugins;
      this.jenkinsWar = jenkinsWar;
    }

    public List<IJenkinsPlugin> getPlugins() {
      return plugins;
    }

    public Artifact getJenkinsWar() {
      return jenkinsWar;
    }
  }

  @SuppressWarnings("unchecked")
  private static final <T, V> Map<T, V> map(Object... objs) {
    Map<T, V> map = new HashMap<>();
    if (objs != null) {
      for (int i = 0; i < objs.length; i += 2) {
        if (i + 1 < objs.length) {
          map.put((T) objs[i], (V) objs[i + 1]);
        }
      }
    }
    return Collections.unmodifiableMap(map);
  }

  @SuppressWarnings("unchecked")
  private static final <T> Set<T> set(T... objs) {
    Set<T> set = new HashSet<>();
    Collections.addAll(set, objs);
    return Collections.unmodifiableSet(set);
  }
}
