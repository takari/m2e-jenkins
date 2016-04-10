package io.takari.m2e.jenkins.launcher.log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;

import org.apache.commons.io.FileUtils;

import ch.qos.logback.classic.spi.ClassPackagingData;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import sun.reflect.Reflection;

@SuppressWarnings({ "rawtypes", "restriction" })
public class PackagingDataCalculator {

  final static StackTraceElementProxy[] STEP_ARRAY_TEMPLATE = new StackTraceElementProxy[0];

  HashMap<String, ClassPackagingData> cache = new HashMap<String, ClassPackagingData>();

  private static boolean GET_CALLER_CLASS_METHOD_AVAILABLE = false;
  // private static boolean HAS_GET_CLASS_LOADER_PERMISSION = false;

  static {
    // if either the Reflection class or the getCallerClass method
    // are unavailable, then we won't invoke Reflection.getCallerClass()
    // This approach ensures that this class will *run* on JDK's lacking
    // sun.reflect.Reflection class. However, this class will *not compile*
    // on JDKs lacking sun.reflect.Reflection.
    try {
      Reflection.getCallerClass(2);
      GET_CALLER_CLASS_METHOD_AVAILABLE = true;
    } catch (NoClassDefFoundError e) {
    } catch (NoSuchMethodError e) {
    } catch (UnsupportedOperationException e) {
    } catch (Throwable e) {
      System.err.println("Unexpected exception");
      e.printStackTrace();
    }
  }

  public void calculate(IThrowableProxy tp) {
    while (tp != null) {
      populateFrames(tp.getStackTraceElementProxyArray());
      IThrowableProxy[] suppressed = tp.getSuppressed();
      if (suppressed != null) {
        for (IThrowableProxy current : suppressed) {
          populateFrames(current.getStackTraceElementProxyArray());
        }
      }
      tp = tp.getCause();
    }
  }

  void populateFrames(StackTraceElementProxy[] stepArray) {
    // in the initial part of this method we populate package information for
    // common stack frames
    final Throwable t = new Throwable("local stack reference");
    final StackTraceElement[] localteSTEArray = t.getStackTrace();
    final int commonFrames = findNumberOfCommonFrames(localteSTEArray, stepArray);
    final int localFirstCommon = localteSTEArray.length - commonFrames;
    final int stepFirstCommon = stepArray.length - commonFrames;

    ClassLoader lastExactClassLoader = null;
    ClassLoader firsExactClassLoader = null;

    int missfireCount = 0;
    for (int i = 0; i < commonFrames; i++) {
      Class callerClass = null;
      if (GET_CALLER_CLASS_METHOD_AVAILABLE) {
        callerClass = Reflection.getCallerClass(localFirstCommon + i - missfireCount + 1);
      }
      StackTraceElementProxy step = stepArray[stepFirstCommon + i];
      String stepClassname = step.getStackTraceElement().getClassName();

      if (callerClass != null && stepClassname.equals(callerClass.getName())) {
        // see also LBCLASSIC-263
        lastExactClassLoader = callerClass.getClassLoader();
        if (firsExactClassLoader == null) {
          firsExactClassLoader = lastExactClassLoader;
        }
        ClassPackagingData pi = calculateByExactType(callerClass);
        step.setClassPackagingData(pi);
      } else {
        missfireCount++;
        ClassPackagingData pi = computeBySTEP(step, lastExactClassLoader);
        step.setClassPackagingData(pi);
      }
    }
    populateUncommonFrames(commonFrames, stepArray, firsExactClassLoader);
  }

  void populateUncommonFrames(int commonFrames, StackTraceElementProxy[] stepArray, ClassLoader firstExactClassLoader) {
    int uncommonFrames = stepArray.length - commonFrames;
    for (int i = 0; i < uncommonFrames; i++) {
      StackTraceElementProxy step = stepArray[i];
      ClassPackagingData pi = computeBySTEP(step, firstExactClassLoader);
      step.setClassPackagingData(pi);
    }
  }

  private ClassPackagingData calculateByExactType(Class type) {
    String className = type.getName();
    ClassPackagingData cpd = cache.get(className);
    if (cpd != null) {
      return cpd;
    }
    String codeLocation = getClassLocation(type);
    cpd = new ClassPackagingData(codeLocation, null);
    cache.put(className, cpd);
    return cpd;
  }

  private ClassPackagingData computeBySTEP(StackTraceElementProxy step, ClassLoader lastExactClassLoader) {
    String className = step.getStackTraceElement().getClassName();
    ClassPackagingData cpd = cache.get(className);
    if (cpd != null) {
      return cpd;
    }
    Class type = bestEffortLoadClass(lastExactClassLoader, className);
    String codeLocation = getClassLocation(type);
    cpd = new ClassPackagingData(codeLocation, null);
    cache.put(className, cpd);
    return cpd;
  }

  private Class loadClass(ClassLoader cl, String className) {
    if (cl == null) {
      return null;
    }
    try {
      return cl.loadClass(className);
    } catch (ClassNotFoundException e1) {
      return null;
    } catch (NoClassDefFoundError e1) {
      return null;
    } catch (Exception e) {
      e.printStackTrace(); // this is unexpected
      return null;
    }

  }

  private Class bestEffortLoadClass(ClassLoader lastGuaranteedClassLoader, String className) {
    Class result = loadClass(lastGuaranteedClassLoader, className);
    if (result != null) {
      return result;
    }
    ClassLoader tccl = Thread.currentThread().getContextClassLoader();
    if (tccl != lastGuaranteedClassLoader) {
      result = loadClass(tccl, className);
    }
    if (result != null) {
      return result;
    }

    try {
      return Class.forName(className);
    } catch (ClassNotFoundException e1) {
      return null;
    } catch (NoClassDefFoundError e1) {
      return null;
    } catch (Exception e) {
      e.printStackTrace(); // this is unexpected
      return null;
    }
  }

  static int findNumberOfCommonFrames(StackTraceElement[] steArray, StackTraceElementProxy[] otherSTEPArray) {
    if (otherSTEPArray == null) {
      return 0;
    }

    int steIndex = steArray.length - 1;
    int parentIndex = otherSTEPArray.length - 1;
    int count = 0;
    while (steIndex >= 0 && parentIndex >= 0) {
      if (steArray[steIndex].equals(otherSTEPArray[parentIndex].getStackTraceElement())) {
        count++;
      } else {
        break;
      }
      steIndex--;
      parentIndex--;
    }
    return count;
  }

  private static String getClassLocation(Class<?> clazz) {
    String jarName = null;

    URL classUrl = getCodeSourceLocation(clazz);
    if (classUrl != null) {
      InputStream in = null;
      try {
        String url = classUrl.toString();
        if (url.endsWith(".jar")) {
          String file = classUrl.getFile();
          int idx = file.lastIndexOf('/');
          if (idx >= 0) {
            jarName = file.substring(idx + 1);
          } else {
            jarName = file;
          }
          url = "jar:" + url + "!/";
        } else {
          if (!url.endsWith("/"))
            url = url + "/";

          if (url.endsWith("/target/classes/")) {
            String prjUrl = url.substring(0, url.length() - "/target/classes/".length());
            if (prjUrl.startsWith("file:")) {
              prjUrl = prjUrl.substring(5);
            }
            File prjDir = new File(prjUrl);
            File dotprj = new File(prjDir, ".project");
            if (dotprj.exists()) {
              String contents = FileUtils.readFileToString(dotprj);
              int istart = contents.indexOf("<name>");
              if (istart != -1) {
                int iend = contents.indexOf("</name>", istart);
                if (iend != -1) {
                  jarName = new String(contents.substring(istart + 6, iend));
                }
              }
            } else {
              jarName = dotprj.toString();
            }

            if (jarName == null) {
              jarName = prjDir.getName();
            }
          }
        }
      } catch (IOException e) {
        // ignore
      } finally {
        if (in != null) {
          try {
            in.close();
          } catch (IOException e) {
          }
        }
      }
    }

    return jarName;
  }

  private static URL getCodeSourceLocation(Class<?> clazz) {
    if (clazz == null || clazz.getProtectionDomain() == null || clazz.getProtectionDomain().getCodeSource() == null
        || clazz.getProtectionDomain().getCodeSource().getLocation() == null)
      return null;
    return clazz.getProtectionDomain().getCodeSource().getLocation();
  }
}
