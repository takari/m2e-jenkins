package io.takari.m2e.jenkins.launcher.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Jetty log implementation that delegates to slf4j
 */
public class JettyLogWrapper implements org.eclipse.jetty.util.log.Logger {
  private Logger logger;
  private String name;

  public JettyLogWrapper() throws Exception {
    this("Jetty");
  }

  public JettyLogWrapper(String name) {
    this.name = name;
    logger = LoggerFactory.getLogger(name);
  }

  public boolean isDebugEnabled() {
    return logger.isDebugEnabled();
  }

  public void debug(Throwable arg0) {
    logger.debug("", arg0);
  }

  public void debug(String arg0, Object... arg1) {
    logger.debug(format(arg0, arg1));
  }

  public void debug(String msg, Throwable th) {
    logger.debug(msg, th);
  }

  public void debug(String msg, long value) {
    logger.debug(msg, value);
  }

  public void info(Throwable arg0) {
    logger.info("", arg0);
  }

  public void info(String arg0, Object... arg1) {
    logger.info(format(arg0, arg1));
  }

  public void info(String arg0, Throwable arg1) {
    logger.info(arg0, arg1);
  }

  public void ignore(Throwable arg0) {
    logger.debug("", arg0);
  }

  public void warn(Throwable arg0) {
    logger.info("", arg0);
  }

  public void warn(String arg0, Object... arg1) {
    logger.warn(format(arg0, arg1));
  }

  public void warn(String msg, Throwable th) {
    if (th instanceof RuntimeException || th instanceof Error)
      logger.error(msg, th);
    else
      logger.warn(msg, th);
  }

  public org.eclipse.jetty.util.log.Logger getLogger(String name) {
    return new JettyLogWrapper(name);
  }

  public String toString() {
    return logger.toString();
  }

  public void setDebugEnabled(boolean enabled) {
    warn("setDebugEnabled not implemented", null, null);
  }

  private String format(String msg, Object... args) {
    msg = String.valueOf(msg); // Avoids NPE
    String braces = "{}";
    StringBuilder builder = new StringBuilder();
    int start = 0;
    for (Object arg : args) {
      int bracesIndex = msg.indexOf(braces, start);
      if (bracesIndex < 0) {
        builder.append(msg.substring(start));
        builder.append(" ");
        builder.append(arg);
        start = msg.length();
      } else {
        builder.append(msg.substring(start, bracesIndex));
        builder.append(String.valueOf(arg));
        start = bracesIndex + braces.length();
      }
    }
    builder.append(msg.substring(start));
    return builder.toString();
  }

  public String getName() {
    return name;
  }

}
