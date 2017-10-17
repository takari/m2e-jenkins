package io.takari.m2e.jenkins.jrebel.cbp;

import org.zeroturnaround.bundled.javassist.CannotCompileException;
import org.zeroturnaround.bundled.javassist.ClassPool;
import org.zeroturnaround.bundled.javassist.CtClass;
import org.zeroturnaround.bundled.javassist.CtMethod;
import org.zeroturnaround.bundled.javassist.expr.ExprEditor;
import org.zeroturnaround.bundled.javassist.expr.MethodCall;
import org.zeroturnaround.javarebel.integration.support.JavassistClassBytecodeProcessor;

public class AbstractTearOffCBP extends JavassistClassBytecodeProcessor {

  @Override
  public void process(ClassPool cp, ClassLoader cl, CtClass ctClass) throws Exception {
    cp.importPackage("io.takari.m2e.jenkins.jrebel");

    CtMethod resolveScript = ctClass.getDeclaredMethod("resolveScript");
    resolveScript.insertBefore("ScriptCacheManager.get(this).beginLoad($1);");
    resolveScript.insertAfter("ScriptCacheManager.get(this).endLoad();", true);

    resolveScript.instrument(new ExprEditor() {
      @Override
      public void edit(MethodCall m) throws CannotCompileException {
        if (m.getMethodName().equals("getResource")) {
          m.replace("{ $_ = $proceed($$); ScriptCacheManager.get(this).registerResource($_); }");
        }
      }
    });
  }

}
