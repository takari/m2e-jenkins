package io.takari.m2e.jenkins.internal.idx;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.project.MavenProject;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.compiler.DefaultErrorHandlingPolicies;
import org.eclipse.jdt.internal.compiler.batch.FileSystem;
import org.eclipse.jdt.internal.compiler.env.AccessRestriction;
import org.eclipse.jdt.internal.compiler.env.IBinaryType;
import org.eclipse.jdt.internal.compiler.env.INameEnvironment;
import org.eclipse.jdt.internal.compiler.env.ISourceType;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.compiler.impl.ITypeRequestor;
import org.eclipse.jdt.internal.compiler.lookup.LookupEnvironment;
import org.eclipse.jdt.internal.compiler.lookup.PackageBinding;
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding;
import org.eclipse.jdt.internal.compiler.problem.DefaultProblemFactory;
import org.eclipse.jdt.internal.compiler.problem.ProblemReporter;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.project.IMavenProjectFacade;

@SuppressWarnings("restriction")
public class ClassLookup implements ITypeRequestor {

  private LookupEnvironment lookupEnv;
  private File outputDir;
  private List<ReferenceBinding> bindings;

  private ClassLookup(IMavenProjectFacade facade, String output, INameEnvironment env) throws CoreException {
    IJavaProject jp = JavaCore.create(facade.getProject());
    CompilerOptions co = new CompilerOptions(jp.getOptions(true));

    this.outputDir = new File(output);

    this.lookupEnv = new LookupEnvironment(this, co,
        new ProblemReporter(DefaultErrorHandlingPolicies.proceedWithAllProblems(), co, new DefaultProblemFactory()),
        env);
    bindings = new ArrayList<>();
  }

  public List<ReferenceBinding> getBindings() {
    return bindings;
  }

  private void scan(IProgressMonitor monitor) {
    scanPackage(outputDir, "");
  }

  private void scanPackage(File dir, String pack) {
    for (File f : dir.listFiles()) {

      String name = f.getName();
      if (f.isDirectory()) {
        scanPackage(f, pack.isEmpty() ? name : (pack + "/" + name));
      } else if (name.endsWith(".class") && name.lastIndexOf('$') == -1) {
        scanClass(pack, name.substring(0, name.length() - 6));
      }
    }
  }

  private ReferenceBinding scanClass(String pack, String name) {
    if (pack.isEmpty()) {
      return scanType(new char[][] { name.toCharArray() });
    }
    return scanType(new char[][] { pack.toCharArray(), name.toCharArray() });
  }

  private ReferenceBinding scanType(char[][] cs) {
    ReferenceBinding type = lookupEnv.getType(cs);
    return add(type);
  }

  private ReferenceBinding add(ReferenceBinding binding) {
    if (!bindings.contains(binding)) {
      bindings.add(binding);
    }
    for (ReferenceBinding b : binding.memberTypes()) {
      add(b);
    }
    return binding;
  }

  public void accept(IBinaryType binaryType, PackageBinding packageBinding, AccessRestriction accessRestriction) {
    lookupEnv.createBinaryTypeFrom(binaryType, packageBinding, accessRestriction);
  }

  @Override
  public void accept(ISourceType[] sourceType, PackageBinding packageBinding, AccessRestriction accessRestriction) {
    System.out.println("Source found");
  }

  @Override
  public void accept(org.eclipse.jdt.internal.compiler.env.ICompilationUnit unit, AccessRestriction accessRestriction) {
    System.out.println("CU found");
  }

  public static ClassLookup create(IMavenProjectFacade facade, IProgressMonitor monitor) throws CoreException {
    MavenProject mp = facade.getMavenProject(monitor);
    String output = mp.getBuild().getOutputDirectory();

    ClassLookup cl = new ClassLookup(facade, output, createNameEnv(facade, output, monitor));
    cl.scan(monitor);
    return cl;
  }

  private static INameEnvironment createNameEnv(IMavenProjectFacade facade, String output, IProgressMonitor monitor)
      throws CoreException {
    return new FileSystem(getClassPath(JavaCore.create(facade.getProject()), output, monitor), new String[0], "UTF-8");
  }

  private static String[] getClassPath(IJavaProject jp, String outputDir, IProgressMonitor monitor)
      throws CoreException {
    List<String> cp = new ArrayList<>();

    cp.add(outputDir);
    IClasspathEntry[] resolvedClasspath = jp.getResolvedClasspath(true);

    for (IClasspathEntry cpe : resolvedClasspath) {
      int kind = cpe.getEntryKind();
      if (kind == IClasspathEntry.CPE_LIBRARY) {
        IPath path = cpe.getPath();
        System.out.println("Lib " + path.toOSString());
        cp.add(path.toOSString());
      } else if (kind == IClasspathEntry.CPE_PROJECT) {
        IPath path = cpe.getPath();
        System.out.println("Prj " + path.toOSString());
        IProject cpp = ResourcesPlugin.getWorkspace().getRoot().findMember(cpe.getPath()).getProject();
        String dir = MavenPlugin.getMavenProjectRegistry().getProject(cpp).getMavenProject(monitor).getBuild()
            .getOutputDirectory();
        cp.add(dir);
      }
    }

    return cp.toArray(new String[cp.size()]);
  }

}
