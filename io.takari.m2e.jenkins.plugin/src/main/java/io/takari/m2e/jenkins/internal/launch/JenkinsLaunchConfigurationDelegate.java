package io.takari.m2e.jenkins.internal.launch;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.jdt.launching.AbstractJavaLaunchConfigurationDelegate;
import org.eclipse.jdt.launching.ExecutionArguments;
import org.eclipse.jdt.launching.IVMRunner;
import org.eclipse.jdt.launching.VMRunnerConfiguration;
import org.eclipse.m2e.core.embedder.ArtifactKey;
import org.eclipse.m2e.core.internal.Bundles;
import org.osgi.framework.Bundle;

import io.takari.m2e.jenkins.IJenkinsPlugin;
import io.takari.m2e.jenkins.JenkinsPluginProject;
import io.takari.m2e.jenkins.PluginDependency;
import io.takari.m2e.jenkins.internal.JenkinsPlugin;
import io.takari.m2e.jenkins.launcher.desc.Descriptor;
import io.takari.m2e.jenkins.launcher.desc.PluginDesc;
import io.takari.m2e.jenkins.runtime.JenkinsRuntimePlugin;
import io.takari.m2e.jenkins.runtime.PluginUpdateCenter;

@SuppressWarnings("restriction")
public class JenkinsLaunchConfigurationDelegate extends AbstractJavaLaunchConfigurationDelegate {

  public static final String ID = "io.takari.m2e.jenkins.plugin.launching.jenkins";

  private static final String RUNTIME_BUNDLE_SYMBOLICNAME = "io.takari.m2e.jenkins.runtime";

  @Override
  public ILaunch getLaunch(ILaunchConfiguration configuration, String mode) throws CoreException {
    return SourceLookupLaunchUtil.createLaunch(configuration, mode);
  }

  @Override
  public void launch(ILaunchConfiguration configuration, String mode, ILaunch launch, IProgressMonitor monitor)
      throws CoreException {

    if (monitor == null) {
      monitor = new NullProgressMonitor();
    }
    
    monitor.beginTask(MessageFormat.format("{0}...", configuration.getName()), 3);
    
    if (monitor.isCanceled()) {
      return;
    }
    
    JenkinsLaunchConfig config = new JenkinsLaunchConfig();
    config.initializeFrom(configuration);

    Descriptor desc = createDescriptor(config, monitor);
    File descFile = writeDescriptor(desc);
    
    monitor.subTask("Verifying launch attributes...");
    
    IVMRunner runner = getVMRunner(configuration, mode);
    
    String workingDirName = LaunchingUtils.substituteVar(config.getWorkDir());
    File workDir = new File(workingDirName);
    if (!workDir.exists()) {
      if (!workDir.mkdirs()) {
        throw new IllegalStateException("Cannot create work dir " + workingDirName);
      }
    }
    
    String[] envp = getEnvironment(configuration);

    String pgmArgs = getProgramArguments(configuration);
    String vmArgs = getVMArguments(configuration);
    ExecutionArguments execArgs = new ExecutionArguments(vmArgs, pgmArgs);

    Map<String, Object> vmAttributesMap = getVMSpecificAttributesMap(configuration);

    List<String> classPath = new ArrayList<>();
    Collections.addAll(classPath, getClasspath(configuration));
    classPath.addAll(getRuntimeClasspath());

    String mainTypeName = JenkinsRuntimePlugin.getInstance().getLauncherClass();
    VMRunnerConfiguration runnerConfig = new VMRunnerConfiguration(mainTypeName,
        classPath.toArray(new String[classPath.size()]));
    runnerConfig.setProgramArguments(new String[] { descFile.getAbsolutePath() });
    runnerConfig.setEnvironment(envp);
    runnerConfig.setVMArguments(SourceLookupLaunchUtil.configureVMArgs(execArgs.getVMArgumentsArray()));
    runnerConfig.setWorkingDirectory(workingDirName);
    runnerConfig.setVMSpecificAttributesMap(vmAttributesMap);
    runnerConfig.setBootClassPath(getBootpath(configuration));

    if (monitor.isCanceled()) {
      return;
    }

    prepareStopInMain(configuration);

    monitor.worked(1);

    runner.run(runnerConfig, launch, monitor);

    if (monitor.isCanceled()) {
      return;
    }

    monitor.done();
  }

  private static List<String> CLASSPATH;

  private List<String> getRuntimeClasspath() {
    if (CLASSPATH == null) {
      LinkedHashSet<String> allentries = new LinkedHashSet<String>();
      Bundle runtimeBundle = Bundles.findDependencyBundle(JenkinsPlugin.getInstance().getBundle(),
          RUNTIME_BUNDLE_SYMBOLICNAME);
      allentries.addAll(Bundles.getClasspathEntries(runtimeBundle));
      CLASSPATH = new ArrayList<>(allentries);
    }
    return CLASSPATH;
  }
  
  private Descriptor createDescriptor(JenkinsLaunchConfig config, IProgressMonitor monitor) throws CoreException {
    // locate all used plugins' hpl files, those should have been generated by
    // test-hpl mojo
    // File testDir = new File(project.getBuild().getTestOutputDirectory());

    monitor.subTask("Creating descriptor...");

    IWorkspaceRoot ws = ResourcesPlugin.getWorkspace().getRoot();

    Descriptor desc = new Descriptor();
    desc.setHost("0.0.0.0");
    desc.setPort(config.getPort());
    desc.setContext("/" + config.getContext());
    desc.setDisableCaches(config.isDisableCaches());
    desc.setSkipUpdateWizard(config.isSkipUpdateWizard());
    desc.setPlugins(new ArrayList<PluginDesc>());

    String jwarVersion = null;
    JenkinsPluginProject jwarProject = null;

    Map<ArtifactKey, JenkinsPluginProject> projects = new HashMap<>();
    for (String plugin : config.getPlugins()) {

      IProject project = ws.getProject(plugin);
      JenkinsPluginProject jp = JenkinsPluginProject.create(project, monitor);
      if (jp == null)
        continue;

      // select the highest jenkins version from selected plugins
      Artifact jw = jp.findJenkinsWar(monitor, false);
      if (jwarVersion == null || compareVersions(jw.getVersion(), jwarVersion) > 0) {
        jwarVersion = jw.getVersion();
        jwarProject = jp;
      }

      PluginDesc pd = new PluginDesc();
      pd.setId(jp.getFacade().getArtifactKey().getArtifactId());
      pd.setPluginFile(jp.getPluginFile(monitor, true).getAbsolutePath());
      pd.setResources(jp.getResources(monitor));
      desc.getPlugins().add(pd);

      projects.put(new ArtifactKey(jp.getGroupId(), jp.getArtifactId(), null, null), jp);
    }

    Map<ArtifactKey, DependencyContainer> dependencyPlugins = new HashMap<>();

    PluginUpdateCenter updates = null;
    if (config.isLatestVersions()) {
      try {
        monitor.subTask("Getting list of latest plugin versions");
        updates = new PluginUpdateCenter();
      } catch (IOException e) {
        JenkinsPlugin.error("Cannot talk to update center", e);
        updates = null;
      }
    }

    for (JenkinsPluginProject jp : projects.values()) {

      // collect dependency plugins, but take the highest version
      List<PluginDependency> deps = jp.findPluginDependencies(monitor, updates);
      for (PluginDependency dep : deps) {
        IJenkinsPlugin pd = dep.getPlugin();
        ArtifactKey ak = new ArtifactKey(pd.getGroupId(), pd.getArtifactId(), null, null);

        // do not override plugin project with a dependency artifact
        if (projects.containsKey(ak)) {
          continue;
        }

        DependencyContainer existing = dependencyPlugins.get(ak);
        if (existing == null) {
          dependencyPlugins.put(ak, new DependencyContainer(dep));
        } else {
          existing.update(dep);
        }
      }
    }
    
    // add dependency plugins
    for (DependencyContainer c : dependencyPlugins.values()) {
      if (!config.isIncludeOptional() && c.isOptional())
        continue;
      if (!config.isIncludeTestScope() && c.isTestScope())
        continue;

      if (c.isOverride()) {
        // TODO log overrides
      }

      IJenkinsPlugin jp = c.getPlugin();

      PluginDesc pd = new PluginDesc();
      pd.setId(jp.getArtifactId());
      pd.setPluginFile(jp.getPluginFile(monitor, true).getAbsolutePath());
      pd.setResources(jp.getResources(monitor));
      desc.getPlugins().add(pd);
    }
    
    Artifact jwar = jwarProject.findJenkinsWar(monitor, true);

    desc.setJenkinsWar(jwar.getFile().getAbsolutePath());

    return desc;
  }

  private static int compareVersions(String v1, String v2) {
    ArtifactVersion ver1 = new DefaultArtifactVersion(v1);
    ArtifactVersion ver2 = new DefaultArtifactVersion(v2);
    return ver1.compareTo(ver2);
  }

  private File writeDescriptor(Descriptor desc) {
    IPath stateLocation = JenkinsPlugin.getInstance().getStateLocation();
    File descriptorFile = stateLocation.append("descriptor").append(String.valueOf(System.currentTimeMillis()) + ".xml")
        .toFile();

    if (descriptorFile.exists()) {
      if (!descriptorFile.delete()) {
        throw new IllegalStateException("Cannot delete existing file " + descriptorFile);
      }
    }

    try {
      descriptorFile.getParentFile().mkdirs();

      if (!descriptorFile.createNewFile()) {
        throw new IllegalStateException("Cannot create temporary file " + descriptorFile);
      }
    } catch (IOException e) {
      throw new IllegalStateException("Cannot create temporary file " + descriptorFile, e);
    }

    desc.write(descriptorFile);
    return descriptorFile;
  }

  private static class DependencyContainer {
    private IJenkinsPlugin plugin;
    private boolean optional;
    private boolean testScope;
    private boolean override;

    public DependencyContainer(PluginDependency dep) {
      plugin = dep.getPlugin();
      optional = dep.isOptional();
      testScope = dep.isTestScope();
      override = dep.isOverride();
    }

    public void update(PluginDependency dep) {

      if (compareVersions(plugin.getVersion(), dep.getPlugin().getVersion()) < 0) {
        plugin = dep.getPlugin();
        override = dep.isOverride();
      }
      optional &= dep.isOptional();
      testScope &= dep.isTestScope();
    }

    public IJenkinsPlugin getPlugin() {
      return plugin;
    }

    public boolean isOptional() {
      return optional;
    }

    public boolean isTestScope() {
      return testScope;
    }

    public boolean isOverride() {
      return override;
    }
  }
}
