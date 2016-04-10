package io.takari.m2e.jenkins.launcher.log;

import java.util.Arrays;
import java.util.List;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.filter.AbstractMatcherFilter;
import ch.qos.logback.core.spi.FilterReply;

public class ErrOutFilter extends AbstractMatcherFilter<LoggingEvent> {

  @Override
  public FilterReply decide(LoggingEvent event) {
    if (!isStarted()) {
      return FilterReply.NEUTRAL;
    }

    LoggingEvent loggingEvent = (LoggingEvent) event;

    List<Level> eventsToKeep = Arrays.asList(Level.WARN, Level.ERROR);
    if (eventsToKeep.contains(loggingEvent.getLevel())) {
      return FilterReply.NEUTRAL;
    } else {
      return FilterReply.DENY;
    }
  }
}