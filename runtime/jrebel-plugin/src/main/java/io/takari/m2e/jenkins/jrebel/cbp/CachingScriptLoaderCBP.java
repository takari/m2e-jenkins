package io.takari.m2e.jenkins.jrebel.cbp;

import org.zeroturnaround.bundled.javassist.CannotCompileException;
import org.zeroturnaround.bundled.javassist.ClassPool;
import org.zeroturnaround.bundled.javassist.CtClass;
import org.zeroturnaround.bundled.javassist.CtField;
import org.zeroturnaround.bundled.javassist.CtMethod;
import org.zeroturnaround.bundled.javassist.expr.ExprEditor;
import org.zeroturnaround.bundled.javassist.expr.MethodCall;
import org.zeroturnaround.javarebel.integration.support.JavassistClassBytecodeProcessor;

import io.takari.m2e.jenkins.jrebel.IManageableScriptLoader;

/**
 * Clears cached jelly/groovy/etc scripts when source changes
 */
public class CachingScriptLoaderCBP extends JavassistClassBytecodeProcessor {

  @Override
  public void process(ClassPool cp, ClassLoader cl, CtClass ctClass) throws Exception {

    cp.importPackage("io.takari.m2e.jenkins.jrebel");

    ctClass.addInterface(cp.get(IManageableScriptLoader.class.getName()));
    ctClass.addField(CtField.make("private ScriptCacheManager __cacheManager = new ScriptCacheManager(this);", ctClass));
    ctClass.addMethod(CtMethod.make("public ScriptCacheManager __getCacheManager() { return __cacheManager; }", ctClass));

    ctClass.getDeclaredMethod("findScript").instrument(new ExprEditor() {
      @Override
      public void edit(MethodCall m) throws CannotCompileException {
        if (m.getMethodName().equals("getUnchecked")) {
          m.replace("{ __getCacheManager().clearStale(scripts, $1); $_ = $proceed($$); }");
        }
      }
    });

  }

}
