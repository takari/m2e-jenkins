package io.takari.m2e.jenkins.jrebel.cbp;

import org.zeroturnaround.bundled.javassist.ClassPool;
import org.zeroturnaround.bundled.javassist.CtClass;
import org.zeroturnaround.bundled.javassist.CtMethod;
import org.zeroturnaround.javarebel.integration.support.JavassistClassBytecodeProcessor;

import io.takari.m2e.jenkins.jrebel.IntrospectorExt;

public class IntrospectorBaseCBP extends JavassistClassBytecodeProcessor {

  @Override
  public void process(ClassPool cp, ClassLoader cl, CtClass ctClass) throws Exception {
    cp.importPackage("io.takari.m2e.jenkins.jrebel");
    cp.importPackage("java.util");

    ctClass.addInterface(cp.get(IntrospectorExt.class.getName()));

    ctClass.addMethod(CtMethod.make(""
        + "public void clearClass(String className) {"
        + "  synchronized (classMethodMaps) {"
        + "    cachedClassNames.remove(className); "
        + "    Iterator it = classMethodMaps.keySet().iterator();"
        + "    while(it.hasNext()){"
        + "      Class cl = (Class) it.next();"
        + "      if(cl.getName().equals(className)) it.remove();"
        + "    }"
        + "  }"
        + "}", ctClass));

  }

}
