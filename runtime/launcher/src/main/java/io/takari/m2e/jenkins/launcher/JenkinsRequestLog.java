package io.takari.m2e.jenkins.launcher;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JenkinsRequestLog extends AbstractLifeCycle implements RequestLog {

  private static final String STAPLER_TRACE = "Stapler-Trace-";
  private static Logger log = LoggerFactory.getLogger("RequestLog");
  
  /*
   * urls that are frequently polled and spam the console
   */
  private static final Set<String> skipped = new HashSet<>(Arrays.asList(
      "/ajaxBuildQueue", //
      "/ajaxExecutors", //
      "/buildHistory/ajax", //
      "/progressiveHtml"));

  @Override
  public void log(Request request, Response response) {

    String pathInfo = request.getPathInfo();

    if (pathInfo == null)
      return;

    // skip those to prevent flooding the console
    for (String s : skipped) {
      if (pathInfo.endsWith(s)) {
        return;
      }
    }

    if (response.getStatus() == 404) {
      String reason = response.getReason();
      if (reason != null)
        reason = ": " + reason;
      log.info(pathInfo + " " + response.getStatus() + reason);
      return;
    }

    int staplerIdx = -1;
    String headerName = null;

    for (String hn : response.getHeaderNames()) {
      if (hn.startsWith(STAPLER_TRACE)) {
        String sidx = hn.substring(STAPLER_TRACE.length(), hn.length());
        int idx;
        try {
          idx = Integer.parseInt(sidx);
        } catch (NumberFormatException e) {
          idx = -1;
        }
        if (idx > staplerIdx) {
          staplerIdx = idx;
          headerName = hn;
        }
      }
    }

    if (headerName != null) {
      String value = response.getHeader(headerName);
      log.info(pathInfo + " " + value);
    }
  }

}
