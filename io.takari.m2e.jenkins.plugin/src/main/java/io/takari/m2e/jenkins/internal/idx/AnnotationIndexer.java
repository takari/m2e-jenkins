package io.takari.m2e.jenkins.internal.idx;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.internal.compiler.lookup.AnnotationBinding;
import org.eclipse.jdt.internal.compiler.lookup.FieldBinding;
import org.eclipse.jdt.internal.compiler.lookup.MethodBinding;
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding;
import org.eclipse.m2e.core.project.IMavenProjectFacade;

@SuppressWarnings("restriction")
public abstract class AnnotationIndexer {

  private Set<String> indexedAnnotations = new HashSet<>();


  public void reindex(IMavenProjectFacade facade, IProgressMonitor monitor) throws CoreException {

    ClassLookup lookup = ClassLookup.create(facade, monitor);

    for (ReferenceBinding type : lookup.getBindings()) {

      // type
      ReferenceBinding t = type;
      while (t != null) {
        for (AnnotationBinding ann : t.getAnnotations()) {
          if (isIndexedAnnotation(ann)) {
            collectType(type, ann);
          }
        }
        t = t.superclass();
      }

      // fields
      for (FieldBinding fb : type.fields()) {
        String name = String.valueOf(fb.readableName());

        for (AnnotationBinding ann : fb.getAnnotations()) {
          if (isIndexedAnnotation(ann)) {
            collectField(type, name, ann);
          }
        }
      }

      // methods
      for (MethodBinding mb : type.methods()) {
        String name = String.valueOf(mb.readableName());
        int c = name.indexOf('(');
        if (c != -1)
          name = name.substring(0, c);

        for (AnnotationBinding ann : mb.getAnnotations()) {
          if (isIndexedAnnotation(ann)) {
            collectMethod(type, name, ann);
          }
        }
      }
    }

    done(facade.getProject());
  }

  protected abstract void collectType(ReferenceBinding type, AnnotationBinding ann);

  protected abstract void collectField(ReferenceBinding type, String name, AnnotationBinding ann);

  protected abstract void collectMethod(ReferenceBinding type, String name, AnnotationBinding ann);

  private boolean isIndexedAnnotation(AnnotationBinding ann) {
    AnnotationBinding[] annanns = ann.getAnnotationType().getAnnotations();
    for (AnnotationBinding annann : annanns) {
      ReferenceBinding annannType = annann.getAnnotationType();
      String name = String.valueOf(annannType.readableName());
      if (isIndexed0(name)) {
        return true;
      }
    }
    return false;
  }

  private boolean isIndexed0(String name) {
    if (indexedAnnotations.contains(name))
      return true;
    boolean indexed = isIndexed(name);
    if (indexed) {
      indexedAnnotations.add(name);
    }
    return indexed;
  }

  protected abstract void done(IProject project) throws CoreException;

  protected abstract boolean isIndexed(String name);

  public static void process(IMavenProjectFacade facade, IProgressMonitor monitor, AnnotationIndexer... idxs)
      throws CoreException {
    for (AnnotationIndexer idx : idxs) {
      idx.reindex(facade, monitor);
    }
  }

}
