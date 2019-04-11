package io.takari.m2e.jenkins.launcher;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.FileUtils;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.webapp.WebAppClassLoader;
import org.eclipse.jetty.webapp.WebAppContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import io.takari.m2e.jenkins.launcher.desc.Descriptor;
import io.takari.m2e.jenkins.launcher.desc.PluginDesc;
import io.takari.m2e.jenkins.launcher.log.JettyLogWrapper;

public class Main {

  static {
    SLF4JBridgeHandler.removeHandlersForRootLogger();
    SLF4JBridgeHandler.install();

    LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
    lc.setPackagingDataEnabled(false);

    boolean allDebug = Boolean.parseBoolean(System.getProperty("debugLogs"));
    ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory
        .getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
    root.setLevel(allDebug ? Level.TRACE : Level.INFO);
    System.setProperty("org.eclipse.jetty.util.log.class", JettyLogWrapper.class.getName());
  }

  private static Logger log = LoggerFactory.getLogger("Launcher");

  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      log.info("Usage: launcher.jar <descriptorLocation>");
      return;
    }

    String descLocation = args[0];
    Descriptor desc = Descriptor.read(new File(descLocation));

    // auto-enable stapler trace, unless otherwise configured already.
    setSystemPropertyIfEmpty("stapler.trace", "true");

    // enable view auto refreshing via stapler
    if (desc.isDisableCaches()) {
      setSystemPropertyIfEmpty("stapler.jelly.noCache", "true");
    }

    // allow Jetty to accept a bigger form so that it can handle update center
    // JSON post
    setSystemPropertyIfEmpty("org.eclipse.jetty.Request.maxFormContentSize", "-1");

    // general-purpose system property so that we can tell from Jenkins if we
    // are running in the hpi:run mode.
    setSystemPropertyIfEmpty("hudson.hpi.run", "true");

    // this adds 3 secs to the shutdown time. Skip it.
    setSystemPropertyIfEmpty("hudson.DNSMultiCast.disabled", "true");

    // expose the current top-directory of the plugin for dev-mode-plugin
    setSystemPropertyIfEmpty("jenkins.moduleRoot", new File(".").getCanonicalPath());

    File workDir = new File(".").getCanonicalFile();
    File jenkinsHomeDir = new File(workDir, "work");

    File targetDir = new File(workDir, "target");
    File tmpDir;
    if (targetDir.exists()) {
      // we're in maven project context
      tmpDir = new File(targetDir, "tmp");
    } else {
      tmpDir = new File(workDir, "tmp");
    }

    // set JENKINS_HOME
    String jenkinsHome = jenkinsHomeDir.getCanonicalPath();

    setSystemPropertyIfEmpty("JENKINS_HOME", jenkinsHome);
    log.info("Jenkins home: " + jenkinsHome);
    log.info("Jenkins war: " + desc.getJenkinsWar());

    // TODO support launching exploded wars
    if (new File(desc.getJenkinsWar()).isDirectory()) {
      throw new IllegalStateException("Running exploded jenkins war is not supported yet");
    }

    if (desc.isSkipUpdateWizard()) {
      String version = getWebappVersion(new File(desc.getJenkinsWar()));
      if (version != null) {
        if (!jenkinsHomeDir.exists()) {
          jenkinsHomeDir.mkdirs();
        }
        writeVersion(new File(jenkinsHomeDir, "jenkins.install.InstallUtil.lastExecVersion"), version);
        writeVersion(new File(jenkinsHomeDir, "jenkins.install.UpgradeWizard.state"), version);
      }
    }

    File pluginsDir = new File(jenkinsHomeDir, "plugins");
    FileUtils.deleteDirectory(pluginsDir);
    pluginsDir.mkdirs();

    StringBuilder res = new StringBuilder();
    for (PluginDesc pd : desc.getPlugins()) {
      if (pd.getResources() != null) {
        for (String resource : pd.getResources()) {
          if (res.length() != 0)
            res.append(';');
          res.append(resource);
        }
      }

      // copy plugin file under jenkinsHome
      File pf = new File(pd.getPluginFile());
      String ext = pf.getName().endsWith(".hpl") ? ".hpl" : ".jpl";
      File target = new File(pluginsDir, pd.getId() + ext);

      log.info("Copying plugin: " + pd.getPluginFile() + " to " + target.getName());
      FileUtils.copyFile(pf, target);
      // pin the dependency plugin, so that even if a different version of the
      // same plugin is bundled to Jenkins, we still use the plugin as specified
      // by the POM of the plugin.
      FileUtils.writeStringToFile(new File(target + ".pinned"), "pinned");
    }

    // path to local stapler resources from plugins
    if (res.length() > 0) {
      setSystemPropertyIfEmpty("stapler.resourcePath", res.toString());
    }

    runServer(desc, tmpDir);
  }

  private static void writeVersion(File file, String version) throws IOException {
    FileUtils.write(file, version);
  }

  private static void runServer(Descriptor desc, File tmpDir) throws Exception {
    Resource.setDefaultUseCaches(false);

    Server server = new Server();

    Connector[] connectors = server.getConnectors();
    if (connectors == null || connectors.length == 0) {

      HttpConfiguration httpConfig = new HttpConfiguration();
      httpConfig.setRequestHeaderSize(32 * 1024);
      httpConfig.setResponseHeaderSize(32 * 1024);
      httpConfig.setOutputBufferSize(32 * 1024);

      HttpConnectionFactory factory = new HttpConnectionFactory(httpConfig);
      factory.setInputBufferSize(32 * 1024);

      ServerConnector connector = new ServerConnector(server, factory);
      connector.setHost(desc.getHost());
      connector.setPort(desc.getPort());
      server.setConnectors(new Connector[] { connector });
    }

    DefaultHandler defaultHandler = new DefaultHandler();
    RequestLogHandler requestLogHandler = new RequestLogHandler();
    requestLogHandler.setRequestLog(new JenkinsRequestLog());

    ContextHandlerCollection contexts = (ContextHandlerCollection) server
        .getChildHandlerByClass(ContextHandlerCollection.class);
    if (contexts == null) {
      contexts = new ContextHandlerCollection();
      HandlerCollection handlers = (HandlerCollection) server.getChildHandlerByClass(HandlerCollection.class);
      if (handlers == null) {
        handlers = new HandlerCollection();
        server.setHandler(handlers);
        handlers.setHandlers(new Handler[] { contexts, defaultHandler, requestLogHandler });
      } else {
        handlers.addHandler(contexts);
      }
    }

    WebAppContext webapp = new WebAppContext();
    webapp.setContextPath(desc.getContext());
    contexts.addHandler(webapp);
    configureWebApplication(webapp, desc, tmpDir);
    server.start();
    server.join();
  }

  private static void configureWebApplication(WebAppContext webapp, Descriptor desc, File tmpDir) throws Exception {

    File webAppFile = new File(desc.getJenkinsWar());
    webapp.setWar(webAppFile.getCanonicalPath());
    webapp.setTempDirectory(tmpDir);
    webapp.setPersistTempDirectory(true);

    File extractedWebAppDir = new File(tmpDir, "webapp");
    if (isExtractedWebAppDirStale(extractedWebAppDir, webAppFile)) {
      FileUtils.deleteDirectory(extractedWebAppDir);
    }

    // cf. https://wiki.jenkins-ci.org/display/JENKINS/Jetty
    HashLoginService hashLoginService = (new HashLoginService("Jenkins Realm"));
    hashLoginService.setConfig("work/etc/realm.properties");
    webapp.getSecurityHandler().setLoginService(hashLoginService);

    webapp.setAttribute("org.eclipse.jetty.server.webapp.WebInfIncludeJarPattern", ".*/classes/.*");
    // to allow the development environment to run multiple "mvn hpi:run" with
    // different port,
    // use different session cookie names. Otherwise they can mix up. See
    // http://stackoverflow.com/questions/1612177/are-http-cookies-port-specific
    webapp.getSessionHandler().getSessionManager().getSessionCookieConfig()
        .setName("JSESSIONID." + UUID.randomUUID().toString().replace("-", "").substring(0, 8));

    try {
      // for Jenkins modules, swap the component from jenkins.war by
      // target/classes
      // via classloader magic
      WebAppClassLoader wacl = new WebAppClassLoader(
          new JettyAndServletApiOnlyClassLoader(null, Main.class.getClassLoader()), webapp);
      webapp.setClassLoader(wacl);
    } catch (IOException e) {
      throw new Error(e);
    }
  }

  private static final String VERSION_PATH = "META-INF/maven/org.jenkins-ci.main/jenkins-war/pom.properties";
  private static final String VERSION_PROP = "version";

  private static boolean isExtractedWebAppDirStale(File extractedWebAppDir, File webApp) throws IOException {
    if (!extractedWebAppDir.isDirectory()) {
      log.info(extractedWebAppDir + " does not yet exist, will receive " + webApp);
      return false;
    }
    if (extractedWebAppDir.lastModified() < webApp.lastModified()) {
      log.info(extractedWebAppDir + " is older than " + webApp + ", will recreate");
      return true;
    }
    File extractedPath = new File(extractedWebAppDir, VERSION_PATH);
    if (!extractedPath.isFile()) {
      log.warn("no such file " + extractedPath);
      return false;
    }
    InputStream is = new FileInputStream(extractedPath);
    String extractedVersion;
    try {
      extractedVersion = loadVersion(is);
    } finally {
      is.close();
    }
    if (extractedVersion == null) {
      log.warn("no " + VERSION_PROP + " in " + extractedPath);
      return false;
    }
    String originalVersion = getWebappVersion(webApp);
    if (originalVersion == null) {
      log.warn("no " + VERSION_PATH + " in " + webApp);
      return false;
    }
    if (!extractedVersion.equals(originalVersion)) {
      log.info("Version " + extractedVersion + " in " + extractedWebAppDir + " does not match " + originalVersion
          + " in " + webApp + ", will recreate");
      return true;
    }
    log.info(extractedWebAppDir + " already up to date with respect to " + webApp);
    return false;
  }

  private static String getWebappVersion(File webApp) throws IOException {
    try (ZipFile zip = new ZipFile(webApp)) {
      ZipEntry entry = zip.getEntry(VERSION_PATH);
      if (entry == null) {
        log.warn("no " + VERSION_PATH + " in " + webApp);
        return null;
      }
      try (InputStream is = zip.getInputStream(entry)) {
        return loadVersion(is);
      }
    }
  }

  private static String loadVersion(InputStream is) throws IOException {
    Properties props = new Properties();
    props.load(is);
    return props.getProperty(VERSION_PROP);
  }

  private static void setSystemPropertyIfEmpty(String name, String value) {
    if (System.getProperty(name) == null)
      System.setProperty(name, value);
  }
}
