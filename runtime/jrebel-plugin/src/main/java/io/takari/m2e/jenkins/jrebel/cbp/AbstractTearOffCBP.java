package io.takari.m2e.jenkins.jrebel.cbp;

import org.zeroturnaround.bundled.javassist.ClassPool;
import org.zeroturnaround.bundled.javassist.CtClass;
import org.zeroturnaround.bundled.javassist.CtMethod;
import org.zeroturnaround.javarebel.integration.support.JavassistClassBytecodeProcessor;

/**
 * Registers loaded jelly/groovy/etc scripts
 */
public class AbstractTearOffCBP extends JavassistClassBytecodeProcessor {

  @Override
  public void process(ClassPool cp, ClassLoader cl, CtClass ctClass) throws Exception {
    cp.importPackage("io.takari.m2e.jenkins.jrebel");
    cp.importPackage("org.kohsuke.stapler");

    CtMethod loadScript = ctClass.getDeclaredMethod("loadScript");
    loadScript.insertBefore("ScriptCacheManager.beginLoad(this, $1);");
    loadScript.insertAfter("ScriptCacheManager.endLoad();", true);
    
    
    CtMethod getResource = ctClass.getDeclaredMethod("getResource");
    getResource.insertAfter("ScriptCacheManager.registerResource($_);");

    ctClass.addMethod(CtMethod.make(""
        + "public MetaClass getOwner() {"
        + "  return owner;"
        + "}", ctClass));
  }

}
