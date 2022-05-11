package io.takari.m2e.jenkins.plugin;

public class PluginDependency {
  private IJenkinsPlugin plugin;
  private boolean testScope;
  private boolean optional;
  private boolean override;

  public PluginDependency(IJenkinsPlugin plugin, boolean testScope, boolean optional) {
    this(plugin, testScope, optional, false);
  }

  public PluginDependency(IJenkinsPlugin plugin, boolean testScope, boolean optional, boolean override) {
    this.plugin = plugin;
    this.testScope = testScope;
    this.optional = optional;
    this.override = override;
  }

  public IJenkinsPlugin getPlugin() {
    return plugin;
  }

  public boolean isTestScope() {
    return testScope;
  }

  public boolean isOptional() {
    return optional;
  }

  public boolean isOverride() {
    return override;
  }
}
