package io.takari.m2e.jenkins.jrebel.cbp;

import org.zeroturnaround.bundled.javassist.ClassPool;
import org.zeroturnaround.bundled.javassist.CtClass;
import org.zeroturnaround.bundled.javassist.CtMethod;
import org.zeroturnaround.javarebel.integration.support.JavassistClassBytecodeProcessor;

import io.takari.m2e.jenkins.jrebel.UberspectExt;

/**
 * Allows extracting introspector from UberspectImpl (used in IntrospectorExt)
 */
public class UberspectImplCBP extends JavassistClassBytecodeProcessor {

  @Override
  public void process(ClassPool cp, ClassLoader cl, CtClass ctClass) throws Exception {
    cp.importPackage("io.takari.m2e.jenkins.jrebel");

    ctClass.addInterface(cp.get(UberspectExt.class.getName()));

    ctClass.addMethod(CtMethod.make("public Object getIntrospector() { return introspector; }", ctClass));
  }

}
