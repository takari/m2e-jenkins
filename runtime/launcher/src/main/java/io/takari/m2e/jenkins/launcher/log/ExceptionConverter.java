package io.takari.m2e.jenkins.launcher.log;

import ch.qos.logback.classic.pattern.ExtendedThrowableProxyConverter;
import ch.qos.logback.classic.spi.ClassPackagingData;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;

public class ExceptionConverter extends ExtendedThrowableProxyConverter {

  private ClassPackagingData prevClassVersion;

  private PackagingDataCalculator calc = new PackagingDataCalculator();

  @Override
  protected String throwableProxyToString(IThrowableProxy tp) {
    calc.calculate(tp);
    return super.throwableProxyToString(tp);
  }

  @Override
  protected void subjoinSTEPArray(StringBuilder buf, int indent, IThrowableProxy tp) {
    prevClassVersion = null;
    try {
      super.subjoinSTEPArray(buf, indent, tp);
    } finally {
      prevClassVersion = null;
    }
  }

  @Override
  protected void extraData(StringBuilder builder, StackTraceElementProxy step) {
    ClassPackagingData cpd = step.getClassPackagingData();
    if (cpd != null && cpd.getCodeLocation() != null) {
      builder.append(" [");
      if (prevClassVersion != null && prevClassVersion.equals(cpd)) {
        builder.append("...");
      } else {
        builder.append(cpd.getCodeLocation());
      }
      builder.append("]");
    }
    prevClassVersion = cpd;
  }

}
