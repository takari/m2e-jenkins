package io.takari.m2e.jenkins.jrebel.cbp;

import org.zeroturnaround.bundled.javassist.ClassPool;
import org.zeroturnaround.bundled.javassist.CtClass;
import org.zeroturnaround.bundled.javassist.CtMethod;
import org.zeroturnaround.javarebel.integration.support.JavassistClassBytecodeProcessor;

import io.takari.m2e.jenkins.jrebel.IReloadableWebapp;

/**
 * Allows retrieving WebApp.classMap field
 */
public class WebAppCBP extends JavassistClassBytecodeProcessor {

  @Override
  public void process(ClassPool cp, ClassLoader cl, CtClass ctClass) throws Exception {
    cp.importPackage("io.takari.m2e.jenkins.jrebel");
    cp.importPackage("org.kohsuke.stapler");
    cp.importPackage("java.util");
    
    ctClass.addInterface(cp.get(IReloadableWebapp.class.getName()));
    ctClass.addMethod(CtMethod.make(""
        + "public Map getClassMap() {"
        + "  return classMap;"
        + "}", ctClass));
  }

}
