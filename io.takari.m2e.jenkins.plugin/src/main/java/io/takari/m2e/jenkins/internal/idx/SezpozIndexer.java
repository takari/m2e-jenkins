package io.takari.m2e.jenkins.internal.idx;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.maven.project.MavenProject;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.IMemberValuePair;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.internal.compiler.impl.Constant;
import org.eclipse.jdt.internal.compiler.lookup.AnnotationBinding;
import org.eclipse.jdt.internal.compiler.lookup.ElementValuePair;
import org.eclipse.jdt.internal.compiler.lookup.FieldBinding;
import org.eclipse.jdt.internal.compiler.lookup.MethodBinding;
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.project.IMavenProjectFacade;

import io.takari.m2e.jenkins.JenkinsPlugin;
import net.java.sezpoz.impl.SerAnnotatedElement;
import net.java.sezpoz.impl.SezpozFactory;

@SuppressWarnings("restriction")
public class SezpozIndexer extends AnnotationIndexer {

  private Map<String, List<SerAnnotatedElement>> index = new HashMap<>();

  @Override
  protected boolean isAffectedByDelta(IMavenProjectFacade facade, IResourceDelta delta) {
    IPath path = facade.getOutputLocation().append("META-INF/annotations");
    IFolder folder = ResourcesPlugin.getWorkspace().getRoot().getFolder(path);
    path = folder.getProjectRelativePath();
    return delta.findMember(path) != null;
  }

  @Override
  protected boolean isIndexed(String name) {
    return "net.java.sezpoz.Indexable".equals(name);
  }

  @Override
  protected void collectType(ReferenceBinding type, AnnotationBinding ann) throws CoreException {
    index(name(type), null, false, ann);

  }

  private String name(ReferenceBinding type) {
    return String.valueOf(CharOperation.concatWith(type.compoundName, '.'));
  }

  @Override
  protected void collectField(ReferenceBinding type, String name, AnnotationBinding ann) throws CoreException {
    index(name(type), name, false, ann);
  }

  @Override
  protected void collectMethod(ReferenceBinding type, String name, AnnotationBinding ann) throws CoreException {
    index(name(type), name, true, ann);
  }

  private void index(String className, String memberName, boolean isMethod, AnnotationBinding ann) throws CoreException {
    ReferenceBinding annType = ann.getAnnotationType();

    String name = String.valueOf(annType.readableName());
    List<SerAnnotatedElement> l = index.get(name);
    if (l == null) {
      index.put(name, l = new ArrayList<>());
    }

    l.add(SezpozFactory.createAnnotatedElement(className, memberName, isMethod, translate(ann, annType)));
  }

  private TreeMap<String, Object> translate(AnnotationBinding ann, ReferenceBinding annType) throws CoreException {

    TreeMap<String, Object> values = new TreeMap<>();

    ElementValuePair[] pairs = ann.getElementValuePairs();
    for (ElementValuePair pair : pairs) {
      String name = new String(pair.getName());
      Object value = translate(pair.getValue());
      values.put(name, value);
    }

    for (MethodBinding mb : annType.methods()) {
      String name = new String(mb.shortReadableName());
      if (name.endsWith("()"))
        name = name.substring(0, name.length() - 2);
      if (!values.containsKey(name)) {
        Object value = translate(mb.getDefaultValue());
        values.put(name, value);
      }
    }
    return values;
  }

  private Object translate(Object value) throws CoreException {
    if (value.getClass().isArray()) {
      Object[] varr = (Object[]) value;
      List<Object> data = new ArrayList<>();
      for (Object v : varr) {
        data.add(translate(v));
      }
      return data;
    }

    if (value instanceof ReferenceBinding) {
      ReferenceBinding rb = (ReferenceBinding) value;
      return SezpozFactory.createTypeConst(name(rb));
    }

    if (value instanceof FieldBinding) {
      FieldBinding fb = (FieldBinding) value;
      return SezpozFactory.createEnumConst(String.valueOf(fb.declaringClass.readableName()),
          String.valueOf(fb.readableName()));
    }

    if (value instanceof AnnotationBinding) {
      AnnotationBinding ann = (AnnotationBinding) value;
      ReferenceBinding annType = ann.getAnnotationType();
      return SezpozFactory.createAnnConst(String.valueOf(annType.readableName()), translate(ann, annType));
    }

    if (value instanceof Constant) {
      return constantValue((Constant) value);
    }

    throw new CoreException(new Status(IStatus.ERROR, JenkinsPlugin.ID, "Unknown annotation parameter type " + value));
  }

  private Object constantValue(Constant constant) {
    switch (constant.typeID()) {
    case Constant.T_JavaLangBoolean:
    case Constant.T_boolean:
      return constant.booleanValue();
    case Constant.T_JavaLangByte:
    case Constant.T_byte:
      return constant.byteValue();
    case Constant.T_JavaLangCharacter:
    case Constant.T_char:
      return constant.charValue();
    case Constant.T_JavaLangDouble:
    case Constant.T_double:
      return constant.doubleValue();
    case Constant.T_JavaLangFloat:
    case Constant.T_float:
      return constant.floatValue();
    case Constant.T_JavaLangInteger:
    case Constant.T_int:
      return constant.intValue();
    case Constant.T_JavaLangLong:
    case Constant.T_long:
      return constant.longValue();
    case Constant.T_JavaLangShort:
    case Constant.T_short:
      return constant.shortValue();
    case Constant.T_JavaLangString:
      return constant.toString();
    }
    return null;
  }

  private TreeMap<String, Object> translate(IAnnotation a) throws CoreException {
    TreeMap<String, Object> values = new TreeMap<String, Object>();
    for (IMemberValuePair pair : a.getMemberValuePairs()) {
      values.put(pair.getMemberName(), translate(pair.getValue(), pair.getValueKind()));
    }
    return values;
  }

  private Object translate(Object value, int valueKind) throws CoreException {
    if (value.getClass().isArray()) {
      List<Object> values = new ArrayList<>();
      for (Object v : ((Object[]) value)) {
        values.add(translate(v, valueKind));
      }
      return values;
    }

    switch (valueKind) {
    case IMemberValuePair.K_ANNOTATION:
      IAnnotation v = (IAnnotation) value;
      return SezpozFactory.createAnnConst(v.getElementName(), translate(v));

    case IMemberValuePair.K_CLASS:
      return SezpozFactory.createTypeConst(value.toString());

    case IMemberValuePair.K_QUALIFIED_NAME:
      String cnst = value.toString();
      int dot = cnst.lastIndexOf('.');
      if (dot == -1)
        throw new IllegalStateException("Bogus qualified name " + value);
      return SezpozFactory.createEnumConst(cnst.substring(0, dot), cnst.substring(dot + 1));

    case IMemberValuePair.K_SIMPLE_NAME:
    case IMemberValuePair.K_UNKNOWN:
      throw new IllegalStateException("Unknown annotation value " + valueKind + ", " + value);
    default:
      return value;
    }
  }

  @Override
  protected void done(IProject project) throws CoreException {
    MavenProject mp = MavenPlugin.getMavenProjectRegistry().getProject(project).getMavenProject();
    File dir = new File(mp.getBuild().getOutputDirectory(), "META-INF/annotations");
    try {
      SezpozFactory.write(index, dir);
    } catch (FileNotFoundException e) {
      JenkinsPlugin.warning("Cannot write annotation index", e);
    } catch (IOException e) {
      throw new CoreException(new Status(IStatus.ERROR, JenkinsPlugin.ID, "Error writing sezpoz index", e));
    }
  }

}
