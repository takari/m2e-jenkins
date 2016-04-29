package io.takari.m2e.jenkins.launcher;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JenkinsRequestLog extends AbstractLifeCycle implements RequestLog {

  private static final String STAPLER_TRACE = "Stapler-Trace-";
  private static Logger log = LoggerFactory.getLogger("RequestLog");

  @Override
  public void log(Request request, Response response) {

    String pathInfo = request.getPathInfo();

    if (pathInfo == null)
      return;

    // skip those to prevent flooding the console
    if (pathInfo.endsWith("/ajaxBuildQueue") || pathInfo.endsWith("/ajaxExecutors"))
      return;

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
