package net.java.sezpoz.impl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class SezpozFactory {

  public static SerAnnotatedElement createAnnotatedElement(String className, String memberName, boolean isMethod,
      TreeMap<String, Object> values) {
    return new SerAnnotatedElement(className, memberName, isMethod, values);
  }

  public static SerAnnConst createAnnConst(String name, TreeMap<String, Object> values) {
    return new SerAnnConst(name, values);
  }

  public static SerEnumConst createEnumConst(String enumName, String constName) {
    return new SerEnumConst(enumName, constName);
  }

  public static SerTypeConst createTypeConst(String name) {
    return new SerTypeConst(name);
  }

  public static void write(Map<String, List<SerAnnotatedElement>> output, File dir) throws IOException { // META-INF/annotations
    for (Map.Entry<String, List<SerAnnotatedElement>> outputEntry : output.entrySet()) {
      String annName = outputEntry.getKey();
      List<SerAnnotatedElement> elements = outputEntry.getValue();
      File out = new File(dir, annName);
      if (out.exists()) {
        out.delete();
      }
      out.getParentFile().mkdirs();
      out.createNewFile();

      ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(out));
      try {
        for (SerAnnotatedElement el : elements) {
          oos.writeObject(el);
        }
        oos.writeObject(null);
        oos.flush();
      } finally {
        oos.close();
      }
    }
  }
}
