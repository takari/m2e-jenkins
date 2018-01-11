package io.takari.m2e.jenkins.internal.launch;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.jobs.IJobManager;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.Job;
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
import io.takari.m2e.jenkins.PluginDependenciesCalculator;
import io.takari.m2e.jenkins.PluginDependenciesCalculator.DependenciesResult;
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

    Descriptor desc;

    IJobManager jm = Job.getJobManager();
    ISchedulingRule rule = ResourcesPlugin.getWorkspace().getRoot();
    jm.beginRule(rule, monitor);
    try {
      desc = createDescriptor(config, monitor);
    } finally {
      jm.endRule(rule);
    }

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

    List<String> vmArgsList = new ArrayList<>();
    Collections.addAll(vmArgsList, execArgs.getVMArgumentsArray());

    try {
      URL jrUrl = JenkinsRuntimePlugin.getInstance().getJrebelPlugin();
      if (jrUrl != null) {
        URL localURL = FileLocator.toFileURL(jrUrl);
        String path = new File(localURL.getFile()).getCanonicalPath();
        vmArgsList.add("-Drebel.plugins=" + path);
      }
    } catch (IOException ioe) {
    }

    String mainTypeName = JenkinsRuntimePlugin.getInstance().getLauncherClass();
    VMRunnerConfiguration runnerConfig = new VMRunnerConfiguration(mainTypeName,
        classPath.toArray(new String[classPath.size()]));
    runnerConfig.setProgramArguments(new String[] { descFile.getAbsolutePath() });
    runnerConfig.setEnvironment(envp);
    runnerConfig.setVMArguments(SourceLookupLaunchUtil.configureVMArgs(vmArgsList));
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

    Map<ArtifactKey, JenkinsPluginProject> projects = new HashMap<>();

    monitor.subTask("Configuring workspace plugins for launch..");
    for (String plugin : config.getPlugins()) {

      IProject project = ws.getProject(plugin);
      JenkinsPluginProject jp = JenkinsPluginProject.create(project, monitor);
      if (jp == null)
        continue;

      projects.put(new ArtifactKey(jp.getGroupId(), jp.getArtifactId(), null, null), jp);
    }

    PluginUpdateCenter updates = null;
    if (config.isLatestVersions()) {
      monitor.subTask("Getting list of latest plugin versions");
      updates = getUpdateCenter();
    }

    monitor.subTask("Collecting dependency plugins..");
    String forceJenkinsVersion = config.isForceJenkinsVersion() ? config.getJenkinsVersion() : null;

    PluginDependenciesCalculator calc = new PluginDependenciesCalculator() //
        .includeOptional(config.isIncludeOptional()) //
        .includeTestScope(config.isIncludeTestScope()) //
        .useLatestVersions(updates) //
        .jenkinsVersion(forceJenkinsVersion);

    for (JenkinsPluginProject jp : projects.values()) {
      calc = calc.withPinnedPlugin(jp, true);
    }

    DependenciesResult res = calc.calculate(config.isForceUpdate(), monitor);

    for (IJenkinsPlugin jp : res.getPlugins()) {
      PluginDesc pd = new PluginDesc();
      pd.setId(jp.getArtifactId());
      pd.setPluginFile(jp.getPluginFile(monitor, false).getAbsolutePath());
      pd.setResources(jp.getResources(monitor));
      desc.getPlugins().add(pd);
    }
    desc.setJenkinsWar(res.getJenkinsWar().getFile().getAbsolutePath());

    return desc;
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

  private static final Object updatesMutex = new Object();
  private static volatile PluginUpdateCenter UPDATES = null;
  private static volatile long lastUpdate = 0;
  private static final long UPDATE_THRESHOLD = 1000L * 60 * 60 * 24; // 1 day

  private PluginUpdateCenter getUpdateCenter() {
    long cur = System.currentTimeMillis();

    PluginUpdateCenter updates = UPDATES;
    if (updates == null || lastUpdate + UPDATE_THRESHOLD > cur) {
      synchronized (updatesMutex) {
        updates = UPDATES;
        if (updates == null || lastUpdate + UPDATE_THRESHOLD > cur) {
          try {
            updates = UPDATES = new PluginUpdateCenter();
            lastUpdate = cur;
          } catch (IOException e) {
            JenkinsPlugin.error("Cannot talk to update center", e);
          }
        }
      }
    }

    return updates;
  }
}
