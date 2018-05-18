package io.takari.m2e.jenkins.jrebel.cbp;

import org.zeroturnaround.bundled.javassist.ClassPool;
import org.zeroturnaround.bundled.javassist.CtClass;
import org.zeroturnaround.bundled.javassist.CtMethod;
import org.zeroturnaround.javarebel.integration.support.JavassistClassBytecodeProcessor;

/**
 * Registers loaded jelly/groovy/etc scripts
 */
public class StaplerCBP extends JavassistClassBytecodeProcessor {

  @Override
  public void process(ClassPool cp, ClassLoader cl, CtClass ctClass) throws Exception {
    cp.importPackage("io.takari.m2e.jenkins.jrebel");
    cp.importPackage("org.kohsuke.stapler");

    CtMethod tryInvoke = ctClass.getDeclaredMethod("tryInvoke");
    tryInvoke.insertBefore("MetaClassReloader.get().init(getWebApp());");
  }

}
