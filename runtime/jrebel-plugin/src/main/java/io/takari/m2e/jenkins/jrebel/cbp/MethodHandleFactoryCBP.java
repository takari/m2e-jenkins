package io.takari.m2e.jenkins.jrebel.cbp;

import org.zeroturnaround.bundled.javassist.ClassPool;
import org.zeroturnaround.bundled.javassist.CtClass;
import org.zeroturnaround.javarebel.integration.support.JavassistClassBytecodeProcessor;

public class MethodHandleFactoryCBP extends JavassistClassBytecodeProcessor {

  @Override
  public void process(ClassPool cp, ClassLoader cl, CtClass ctClass) throws Exception {
    cp.importPackage("io.takari.m2e.jenkins.jrebel");
    cp.importPackage("org.kohsuke.stapler");

    ctClass.getDeclaredMethod("get").setBody("{ return io.takari.m2e.jenkins.jrebel.MethodHandleFactory.get($1); }");
  }

}
