package io.takari.m2e.jenkins.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.artifact.versioning.ComparableVersion;
import org.eclipse.jetty.util.StringUtil;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.VisibilityChecker;

public class PluginUpdateCenter {

  public static final String UPDATE_CENTER_URL = "http://updates.jenkins-ci.org/update-center.json";
  public static final String PLUGIN_VERSIONS_URL = "http://updates.jenkins-ci.org/plugin-versions.json";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  static {
    MAPPER.setVisibility(
        new VisibilityChecker.Std(Visibility.NONE, Visibility.NONE, Visibility.NONE, Visibility.NONE, Visibility.ANY));
    MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  private Map<String, String> updateCenter;
  private Map<String, List<PluginVersion>> pluginVersions;

  public PluginUpdateCenter() throws IOException {
    load();
  }

  private void load() throws IOException {
    updateCenter = loadUpdateCenter();
    pluginVersions = loadPluginVersions();
  }

  /* ga->name */
  private Map<String, String> loadUpdateCenter() throws IOException {
    HttpURLConnection conn = (HttpURLConnection) new URL(UPDATE_CENTER_URL).openConnection();
    conn.setConnectTimeout(10000);

    String str;
    try (InputStream in = conn.getInputStream()) {
      str = read(in);
    }

    str = str.trim();
    if (str.startsWith("updateCenter.post(")) {
      str = str.substring("updateCenter.post(".length(), str.length() - 2).trim();
    }

    Map<String, String> plugins = new HashMap<>();

    UpdateCenterBean config = MAPPER.readValue(str, UpdateCenterBean.class);
    if (config != null && config.plugins != null) {
      for (Map.Entry<String, Plugin> e : config.plugins.entrySet()) {
        Plugin p = e.getValue();
        if (!StringUtil.isBlank(p.gav)) {
          String[] gav = p.gav.split(":", 3);
          plugins.put(gav[0] + ":" + gav[1], e.getKey());
        }
      }
    }
    return plugins;
  }

  /* name -> versions */
  private Map<String, List<PluginVersion>> loadPluginVersions() throws IOException {

    Map<String, List<PluginVersion>> pluginVersions = new HashMap<>();

    HttpURLConnection conn = (HttpURLConnection) new URL(PLUGIN_VERSIONS_URL).openConnection();
    conn.setConnectTimeout(10000);

    try (InputStream in = conn.getInputStream()) {
      JsonParser jp = MAPPER.getFactory().createParser(in);
      jp.nextToken();
      if (jp.isExpectedStartObjectToken()) {
        String field;
        while ((field = jp.nextFieldName()) != null) {
          switch (field) {
          case "plugins":
            jp.nextToken();
            readPluginVersionsList(jp, pluginVersions);
          default:
            jp.skipChildren();
          }
        }
      }
    }

    return pluginVersions;
  }

  private void readPluginVersionsList(JsonParser jp, Map<String, List<PluginVersion>> pluginVersions)
      throws IOException {
    if (jp.isExpectedStartObjectToken()) {
      String pluginName;
      while ((pluginName = jp.nextFieldName()) != null) {
        jp.nextToken();
        List<PluginVersion> pv = readPluginVersions(jp);
        pluginVersions.put(pluginName, pv);
      }
    } else {
      jp.skipChildren();
    }
  }

  private List<PluginVersion> readPluginVersions(JsonParser jp) throws IOException {
    List<PluginVersion> versions = new ArrayList<>();

    if (jp.isExpectedStartObjectToken()) {
      while (jp.nextFieldName() != null) {
        jp.nextToken();
        if (jp.isExpectedStartObjectToken()) {
          PluginVersion pv = jp.readValueAs(PluginVersion.class);
          if (pv != null) {
            pv.comparableVersion = new ComparableVersion(pv.version);
            pv.comparableCoreVersion = new ComparableVersion(pv.requiredCore);
            versions.add(pv);
          }
        } else {
          jp.skipChildren();
        }
      }
    } else {
      jp.skipChildren();
    }

    Collections.sort(versions);

    return versions;
  }

  private String read(InputStream in) throws IOException {
    StringWriter sw = new StringWriter();
    InputStreamReader r = new InputStreamReader(in, "UTF-8");

    char[] buf = new char[32 * 1024];
    int n = 0;
    while ((n = r.read(buf)) != -1) {
      sw.write(buf, 0, n);
    }
    return sw.toString();
  }

  public String getVersion(String groupId, String artifactId, String maxCoreVersion) {
    String name = updateCenter.get(groupId + ":" + artifactId);
    if (name == null) {
      return null;
    }
    List<PluginVersion> versions = pluginVersions.get(name);
    if (versions == null) {
      return null;
    }

    ComparableVersion cv = maxCoreVersion == null ? null : new ComparableVersion(maxCoreVersion);
    for (PluginVersion pv : versions) {
      if (cv == null || cv.compareTo(pv.comparableCoreVersion) >= 0) {
        return pv.version;
      }
    }

    return null;
  }

  static class UpdateCenterBean {
    Map<String, Plugin> plugins;
  }

  static class Plugin {
    String gav;
  }

  static class PluginVersion implements Comparable<PluginVersion> {
    String version;
    String requiredCore;
    ComparableVersion comparableCoreVersion;
    ComparableVersion comparableVersion;

    @Override
    public int compareTo(PluginVersion o) {
      return o.comparableVersion.compareTo(this.comparableVersion);
    }
  }
}
