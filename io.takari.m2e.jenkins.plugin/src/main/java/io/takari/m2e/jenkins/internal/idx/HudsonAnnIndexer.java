package io.takari.m2e.jenkins.internal.idx;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.maven.project.MavenProject;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.internal.compiler.lookup.AnnotationBinding;
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.project.IMavenProjectFacade;

import io.takari.m2e.jenkins.JenkinsPlugin;

@SuppressWarnings("restriction")
public class HudsonAnnIndexer extends AnnotationIndexer {

  private Map<String, Set<String>> index = new HashMap<>();

  @Override
  protected boolean isAffectedByDelta(IMavenProjectFacade facade, IResourceDelta delta) {
    IPath path = facade.getOutputLocation().append("META-INF/annotations");
    path = ResourcesPlugin.getWorkspace().getRoot().getFolder(path).getProjectRelativePath();
    return delta.findMember(path) != null;
  }

  @Override
  protected boolean isIndexed(String name) {
    return "org.jvnet.hudson.annotation_indexer.Indexed".equals(name);
  }

  @Override
  protected void collectType(ReferenceBinding type, AnnotationBinding ann) {
    index(name(type), null, false, ann);

  }

  private String name(ReferenceBinding type) {
    return String.valueOf(CharOperation.concatWith(type.compoundName, '.'));
  }

  @Override
  protected void collectField(ReferenceBinding type, String name, AnnotationBinding ann) {
    index(name(type), name, false, ann);
  }

  @Override
  protected void collectMethod(ReferenceBinding type, String name, AnnotationBinding ann) {
    index(name(type), name, true, ann);
  }

  private void index(String className, String memberName, boolean isMethod, AnnotationBinding ann) {
    ReferenceBinding annType = ann.getAnnotationType();
    String name = String.valueOf(annType.readableName());
    Set<String> classes = index.get(name);
    if (classes == null) {
      index.put(name, classes = new HashSet<>());
    }
    classes.add(className);
  }

  @Override
  protected void done(IProject project) throws CoreException {
    MavenProject mp = MavenPlugin.getMavenProjectRegistry().getProject(project).getMavenProject();
    File dir = new File(mp.getBuild().getOutputDirectory(), "META-INF/annotations");
    try {
      for (Map.Entry<String, Set<String>> e : index.entrySet()) {
        PrintWriter w = new PrintWriter(new FileOutputStream(new File(dir, e.getKey())));
        try {
          for (String el : e.getValue()) {
            w.println(el);
          }
        } finally {
          w.close();
        }
      }
    } catch (FileNotFoundException e) {
      JenkinsPlugin.warning("Cannot write annotation index", e);
    }
  }

}
