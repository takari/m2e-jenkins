package io.takari.m2e.jenkins.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jetty.util.StringUtil;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.VisibilityChecker;

public class PluginUpdateCenter {

  public static final String UPDACE_CENTER_URL = "http://updates.jenkins-ci.org/update-center.json";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  static {
    MAPPER.setVisibility(
        new VisibilityChecker.Std(Visibility.NONE, Visibility.NONE, Visibility.NONE, Visibility.NONE, Visibility.ANY));
    MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  private Map<String, String> plugins;

  public PluginUpdateCenter() throws IOException {
    plugins = new HashMap<>();
    load();
  }

  private void load() throws IOException {
    
    HttpURLConnection conn = (HttpURLConnection) new URL(UPDACE_CENTER_URL).openConnection();
    conn.setConnectTimeout(10000);

    String str = read(conn.getInputStream());
    str = str.trim();
    if (str.startsWith("updateCenter.post(")) {
      str = str.substring("updateCenter.post(".length(), str.length() - 2).trim();
    }

    Config config = MAPPER.readValue(str, Config.class);
    if (config != null && config.plugins != null) {
      for (Plugin p : config.plugins.values()) {
        if (!StringUtil.isBlank(p.gav)) {
          String[] gav = p.gav.split(":", 3);
          plugins.put(gav[0] + ":" + gav[1], gav[2]);
        }
      }
    }
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

  public String getVersion(String groupId, String artifactId) {
    return plugins.get(groupId + ":" + artifactId);
  }

  static class Config {
    Map<String, Plugin> plugins;
  }

  static class Plugin {
    String gav;
  }
}
