package io.takari.m2e.jenkins.internal.launch;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import org.eclipse.debug.core.Launch;
import org.eclipse.jdt.internal.launching.sourcelookup.advanced.AdvancedSourceLookupDirector;
import org.eclipse.jdt.launching.AbstractJavaLaunchConfigurationDelegate;
import org.eclipse.jdt.launching.ExecutionArguments;
import org.eclipse.jdt.launching.IVMRunner;
import org.eclipse.jdt.launching.VMRunnerConfiguration;
import org.eclipse.jdt.launching.sourcelookup.advanced.AdvancedSourceLookup;
import org.eclipse.m2e.core.embedder.ArtifactKey;
import org.eclipse.m2e.core.internal.Bundles;
import org.osgi.framework.Bundle;
import org.osgi.framework.namespace.BundleNamespace;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;

import io.takari.m2e.jenkins.internal.JenkinsPlugin;
import io.takari.m2e.jenkins.launcher.desc.Descriptor;
import io.takari.m2e.jenkins.launcher.desc.PluginDesc;
import io.takari.m2e.jenkins.plugin.IJenkinsPlugin;
import io.takari.m2e.jenkins.plugin.JenkinsPluginProject;
import io.takari.m2e.jenkins.plugin.PluginDependenciesCalculator;
import io.takari.m2e.jenkins.plugin.PluginDependenciesCalculator.DependenciesResult;
import io.takari.m2e.jenkins.runtime.JenkinsRuntimePlugin;
import io.takari.m2e.jenkins.runtime.PluginUpdateCenter;

@SuppressWarnings("restriction")
public class JenkinsLaunchConfigurationDelegate extends AbstractJavaLaunchConfigurationDelegate {

  public static final String ID = "io.takari.m2e.jenkins.plugin.launching.jenkins";

  private static final String RUNTIME_BUNDLE_SYMBOLICNAME = "io.takari.m2e.jenkins.runtime";

  public JenkinsLaunchConfigurationDelegate() {
    allowAdvancedSourcelookup();
  }

  @Override
  public ILaunch getLaunch(ILaunchConfiguration configuration, String mode) throws CoreException {
    return new Launch(configuration, mode,
        AdvancedSourceLookup.createSourceLocator(AdvancedSourceLookupDirector.ID, configuration));
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

    String pgmArgs = concat(descFile.getAbsolutePath(), getProgramArguments(configuration));
    String vmArgs = concat(getVMArguments(configuration), getVMArguments(configuration, mode));
    ExecutionArguments execArgs = new ExecutionArguments(vmArgs, pgmArgs);

    JenkinsPlugin.info("Starting jenkins with " + vmArgs);

    List<String> classPath = new ArrayList<>();
    Collections.addAll(classPath, getClasspath(configuration));
    classPath.addAll(getRuntimeClasspath());

    String mainTypeName = JenkinsRuntimePlugin.getInstance().getLauncherClass();
    VMRunnerConfiguration runnerConfig = new VMRunnerConfiguration(mainTypeName, classPath.toArray(new String[0]));
    runnerConfig.setEnvironment(getEnvironment(configuration));
    runnerConfig.setProgramArguments(execArgs.getProgramArgumentsArray());
    runnerConfig.setVMArguments(execArgs.getVMArgumentsArray());
    runnerConfig.setWorkingDirectory(workingDirName);
    runnerConfig.setVMSpecificAttributesMap(getVMSpecificAttributesMap(configuration));
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

  @Override
  public String getVMArguments(ILaunchConfiguration configuration) throws CoreException {
    String vmArgs = super.getVMArguments(configuration);

    try {
      URL jrUrl = JenkinsRuntimePlugin.getInstance().getJrebelPlugin();
      if (jrUrl != null) {
        URL localURL = FileLocator.toFileURL(jrUrl);
        String path = new File(localURL.getFile()).getCanonicalPath();
        vmArgs += " -Drebel.plugins=\"" + path + "\"";
      }
    } catch (IOException ioe) {
    }

    return vmArgs;
  }

  private static String concat(String args1, String args2) {
    StringBuilder args = new StringBuilder();
    if (args1 != null && !args1.isEmpty()) {
      args.append(args1);
    }
    if (args2 != null && !args2.isEmpty()) {
      args.append(" "); //$NON-NLS-1$
      args.append(args2);
    }
    return args.toString();
  }

  private static List<String> CLASSPATH;

  private List<String> getRuntimeClasspath() {
    if (CLASSPATH == null) {
      LinkedHashSet<String> allentries = new LinkedHashSet<String>();
      Bundle runtimeBundle = findDependencyBundle(JenkinsPlugin.getInstance().getBundle(),
          RUNTIME_BUNDLE_SYMBOLICNAME, new HashSet<>());
      allentries.addAll(Bundles.getClasspathEntries(runtimeBundle));
      CLASSPATH = new ArrayList<>(allentries);
    }
    return CLASSPATH;
  }

  private static Bundle findDependencyBundle(Bundle bundle, String dependencyName, Set<Bundle> visited) {
    BundleWiring bundleWiring = bundle.adapt(BundleWiring.class);
    if (bundleWiring == null) {
      return null;
    }
    ArrayList<BundleWire> dependencies = new ArrayList<BundleWire>();
    dependencies.addAll(bundleWiring.getRequiredWires(BundleNamespace.BUNDLE_NAMESPACE));
    dependencies.addAll(bundleWiring.getRequiredWires(PackageNamespace.PACKAGE_NAMESPACE));
    for (BundleWire wire : dependencies) {
      Bundle requiredBundle = wire.getProviderWiring().getBundle();
      if (requiredBundle != null && visited.add(requiredBundle)) {
        if (dependencyName.equals(requiredBundle.getSymbolicName())) {
          return requiredBundle;
        }
        Bundle required = findDependencyBundle(requiredBundle, dependencyName, visited);
        if (required != null) {
          return required;
        }
      }
    }
    return null;
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
