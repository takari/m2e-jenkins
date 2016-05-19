package io.takari.m2e.jenkins.internal.ui;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.databinding.Binding;
import org.eclipse.core.databinding.DataBindingContext;
import org.eclipse.core.databinding.UpdateValueStrategy;
import org.eclipse.core.databinding.beans.BeanProperties;
import org.eclipse.core.databinding.observable.set.ISetChangeListener;
import org.eclipse.core.databinding.observable.set.SetChangeEvent;
import org.eclipse.core.databinding.observable.value.IObservableValue;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.ui.StringVariableSelectionDialog;
import org.eclipse.jdt.debug.ui.launchConfigurations.JavaLaunchTab;
import org.eclipse.jface.databinding.swt.WidgetProperties;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.dialogs.ContainerSelectionDialog;

import io.takari.m2e.jenkins.JenkinsPluginProject;
import io.takari.m2e.jenkins.internal.JenkinsPlugin;
import io.takari.m2e.jenkins.internal.launch.JenkinsLaunchConfig;
import io.takari.m2e.jenkins.internal.launch.LaunchingUtils;
import io.takari.m2e.jenkins.internal.ui.databinding.IntegerToStringConverter;
import io.takari.m2e.jenkins.internal.ui.databinding.IntegerValidator;
import io.takari.m2e.jenkins.internal.ui.databinding.SetSelectionObservable;
import io.takari.m2e.jenkins.internal.ui.databinding.StringToIntegerConverter;

public class JenkinsMainTab extends JavaLaunchTab {

  private List<JenkinsPluginProject> projects;

  private JenkinsLaunchConfig config = new JenkinsLaunchConfig();

  private Combo cmbWorkDir;
  private Text txtPort;
  private Text txtContext;
  private List<Button> pluginCheckButtons;
  private Button btnIncludeTestScope;
  private Button btnIncludeOptionalTransitive;
  private Button btnDisableCaches;
  private Button btnLatestVersions;

  private DataBindingContext bindingContext;

  private boolean updating;

  public JenkinsMainTab() {
    projects = JenkinsPluginProject.getProjects(new NullProgressMonitor());
  }

  @Override
  public String getName() {
    return "Jenkins";
  }

  @Override
  public void setDefaults(ILaunchConfigurationWorkingCopy configuration) {
    config.setDefaults(configuration);
  }

  @Override
  public void performApply(ILaunchConfigurationWorkingCopy configuration) {
    config.performApply(configuration);
  }

  @Override
  public void initializeFrom(ILaunchConfiguration configuration) {
    super.initializeFrom(configuration);
    boolean u = updating;
    updating = true;
    try {
      try {
        if (JenkinsLaunchConfig.needsMigration(configuration)) {
          JenkinsLaunchConfig.migrate(configuration);
        }
      } catch (CoreException e) {
        JenkinsPlugin.error("Error migrating launch config", e);
      }
      config.initializeFrom(configuration);
    } finally {
      updating = u;
    }

    bindingContext.updateModels();
    updateCombo();
  }

  public boolean isValid(ILaunchConfiguration config) {

    JenkinsLaunchConfig conf = new JenkinsLaunchConfig();
    conf.initializeFrom(config);

    for (Object o : bindingContext.getBindings()) {
      Binding b = (Binding) o;
      IStatus s = (IStatus) b.getValidationStatus().getValue();
      if (s != null) {
        if (!s.isOK()) {
          setErrorMessage(s.getMessage());
          return false;
        }
      }
    }

    Set<String> plugins = new HashSet<>(conf.getPlugins());

    boolean containsAny = false;
    for (JenkinsPluginProject p : projects) {
      if (plugins.contains(p.getProject().getName())) {
        containsAny = true;
        break;
      }
    }

    if (!containsAny) {
      setErrorMessage("No plugins selected");
      return false;
    }

    try {
      String workingDirName = LaunchingUtils.substituteVar(conf.getWorkDir());

      if (workingDirName == null || workingDirName.trim().isEmpty()) {
        setErrorMessage("Working dir is not set");
        return false;
      }
    } catch (CoreException e) {
      setErrorMessage(e.getMessage());
      return false;
    }

    setErrorMessage(null);
    return true;
  }

  /**
   * @wbp.parser.entryPoint
   */
  @Override
  public void createControl(Composite parent) {
    Composite comp = new Composite(parent, SWT.NONE);
    setControl(comp);
    comp.setLayout(new GridLayout(1, false));

    Group grpJetty = new Group(comp, SWT.NONE);
    grpJetty.setText("Server");
    grpJetty.setLayout(new GridLayout(8, false));
    grpJetty.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 4, 1));

    Label lblWorkDir = new Label(grpJetty, SWT.NONE);
    lblWorkDir.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
    lblWorkDir.setText("Work dir:");

    cmbWorkDir = new Combo(grpJetty, SWT.BORDER);
    cmbWorkDir.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 4, 1));

    Button btnBrowseWS = new Button(grpJetty, SWT.NONE);
    btnBrowseWS.setText("Workspace...");
    btnBrowseWS.addSelectionListener(new BrowseWorkspaceDirAction(cmbWorkDir, "Select work dir"));

    Button btnBrowseFS = new Button(grpJetty, SWT.NONE);
    btnBrowseFS.setText("Filesystem...");
    btnBrowseFS.addSelectionListener(new BrowseDirAction(cmbWorkDir));

    Button btnBrowseVars = new Button(grpJetty, SWT.NONE);
    btnBrowseVars.setText("Variables...");
    btnBrowseVars.addSelectionListener(new VariablesAction(cmbWorkDir));

    Label lblHost = new Label(grpJetty, SWT.NONE);
    lblHost.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
    lblHost.setText("Host:");

    Label lblHst = new Label(grpJetty, SWT.NONE);
    lblHst.setText("http://localhost:");

    txtPort = new Text(grpJetty, SWT.BORDER);
    GridData gd_txtPort = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
    gd_txtPort.widthHint = 40;
    txtPort.setLayoutData(gd_txtPort);
    txtPort.setTextLimit(5);

    Label lblCtx = new Label(grpJetty, SWT.NONE);
    lblCtx.setText("/");

    txtContext = new Text(grpJetty, SWT.BORDER);
    GridData gd_txtContext = new GridData(SWT.LEFT, SWT.CENTER, false, false, 4, 1);
    gd_txtContext.widthHint = 150;
    txtContext.setLayoutData(gd_txtContext);

    Group grpOptions = new Group(comp, SWT.NONE);
    grpOptions.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
    grpOptions.setText("Options");
    grpOptions.setLayout(new GridLayout(1, false));

    btnDisableCaches = new Button(grpOptions, SWT.CHECK);
    btnDisableCaches.setText("Disable caches (slows down responses)");

    Group grpPlugins = new Group(comp, SWT.NONE);
    grpPlugins.setText("Plugin projects");
    grpPlugins.setLayout(new GridLayout(1, false));
    grpPlugins.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 4, 1));

    pluginCheckButtons = new ArrayList<>();

    for (JenkinsPluginProject jp : projects) {
      Button btnCheckButton = new Button(grpPlugins, SWT.CHECK);
      btnCheckButton.setText(jp.getProject().getName());
      btnCheckButton.setData(jp);
      pluginCheckButtons.add(btnCheckButton);
    }

    btnIncludeTestScope = new Button(grpPlugins, SWT.CHECK);
    btnIncludeTestScope.setText("Include test scope");
    btnIncludeOptionalTransitive = new Button(grpPlugins, SWT.CHECK);
    btnIncludeOptionalTransitive.setText("Include optional transitive plugins");

    btnLatestVersions = new Button(grpPlugins, SWT.CHECK);
    btnLatestVersions.setText("Use latest available plugin versions");

    updateCombo();

    bindingContext = initDataBindings();

    initAdditionalBindings();

    config.addPropertyChangeListener(new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        if (updating)
          return;
        updateLaunchConfigurationDialog();
      }
    });
  }

  private void updateCombo() {

    List<String> items = new ArrayList<>();
    for (JenkinsPluginProject jp : projects) {
      String name = jp.getProject().getName();
      if (config.getPlugins().contains(name)) {
        items.add(LaunchingUtils
            .generateWorkspaceLocationVariableExpression(jp.getProject().getFullPath()) + "/work");
      }
    }
    String value = cmbWorkDir.getText();

    cmbWorkDir.setItems(items.toArray(new String[items.size()]));
    cmbWorkDir.setText(value);
  }

  private void initAdditionalBindings() {
    for (Button b : pluginCheckButtons) {
      JenkinsPluginProject jp = (JenkinsPluginProject) b.getData();

      IObservableValue buttonSelectionObservable = WidgetProperties.selection().observe(b);
      IObservableValue setElementObservable = new SetSelectionObservable(config, "plugins", jp.getProject().getName());
      bindingContext.bindValue(buttonSelectionObservable, setElementObservable, null, null);
    }

    config.getPluginsObservable().addSetChangeListener(new ISetChangeListener() {
      @Override
      public void handleSetChange(SetChangeEvent event) {
        if (updating)
          return;
        updateCombo();
      }
    });
  }

  private class BrowseWorkspaceDirAction extends SelectionAdapter {

    private Combo target;

    private String label;

    public BrowseWorkspaceDirAction(Combo target, String label) {
      this.target = target;
      this.label = label;
    }

    public void widgetSelected(SelectionEvent e) {
      ContainerSelectionDialog dialog = new ContainerSelectionDialog(getShell(), //
          ResourcesPlugin.getWorkspace().getRoot(), false, label); // $NON-NLS-1$
      dialog.showClosedProjects(false);

      int buttonId = dialog.open();
      if (buttonId == IDialogConstants.OK_ID) {
        Object[] resource = dialog.getResult();
        if (resource != null && resource.length > 0) {
          String fileLoc = LaunchingUtils.generateWorkspaceLocationVariableExpression((IPath) resource[0]);
          target.setText(fileLoc);
          updateLaunchConfigurationDialog();
        }
      }
    }
  }

  private class BrowseDirAction extends SelectionAdapter {

    private Combo target;

    public BrowseDirAction(Combo target) {
      this.target = target;
    }

    public void widgetSelected(SelectionEvent e) {
      DirectoryDialog dialog = new DirectoryDialog(getShell(), SWT.NONE);
      dialog.setFilterPath(target.getText());
      String text = dialog.open();
      if (text != null) {
        target.setText(text);
        updateLaunchConfigurationDialog();
      }
    }
  }

  private class VariablesAction extends SelectionAdapter {

    private Combo target;

    public VariablesAction(Combo target) {
      this.target = target;
    }

    public void widgetSelected(SelectionEvent e) {
      StringVariableSelectionDialog dialog = new StringVariableSelectionDialog(getShell());
      dialog.open();
      String variable = dialog.getVariableExpression();
      if (variable != null) {
        Point sel = target.getSelection();
        String text = target.getText();
        target.setText(text.substring(0, sel.x) + variable + text.substring(sel.y, text.length()));
        updateLaunchConfigurationDialog();
      }
    }
  }
  protected DataBindingContext initDataBindings() {
    DataBindingContext bindingContext = new DataBindingContext();
    //
    IObservableValue observeTextTxtWorkDirObserveWidget = WidgetProperties.text().observe(cmbWorkDir);
    IObservableValue workDirConfigObserveValue = BeanProperties.value("workDir").observe(config);
    bindingContext.bindValue(observeTextTxtWorkDirObserveWidget, workDirConfigObserveValue, null, null);
    //
    IObservableValue observeTextTxtPortObserveWidget = WidgetProperties.text(SWT.Modify).observe(txtPort);
    IObservableValue portConfigObserveValue = BeanProperties.value("port").observe(config);
    UpdateValueStrategy portTargetToModel = new UpdateValueStrategy();
    portTargetToModel.setConverter(new StringToIntegerConverter());
    portTargetToModel.setAfterGetValidator(new IntegerValidator(txtPort, "port", true));
    UpdateValueStrategy portModelToTarget = new UpdateValueStrategy();
    portModelToTarget.setConverter(new IntegerToStringConverter());
    bindingContext.bindValue(observeTextTxtPortObserveWidget, portConfigObserveValue, portTargetToModel,
        portModelToTarget);
    //
    IObservableValue observeTextTxtContextObserveWidget = WidgetProperties.text(SWT.Modify).observe(txtContext);
    IObservableValue contextConfigObserveValue = BeanProperties.value("context").observe(config);
    bindingContext.bindValue(observeTextTxtContextObserveWidget, contextConfigObserveValue, null, null);
    //
    IObservableValue observeSelectionBtnDisableCachesslowsObserveWidget = WidgetProperties.selection()
        .observe(btnDisableCaches);
    IObservableValue disableCachesConfigObserveValue = BeanProperties.value("disableCaches").observe(config);
    bindingContext.bindValue(observeSelectionBtnDisableCachesslowsObserveWidget, disableCachesConfigObserveValue, null,
        null);
    //
    IObservableValue observeSelectionBtnIncludeTestScopeObserveWidget = WidgetProperties.selection()
        .observe(btnIncludeTestScope);
    IObservableValue includeTestScopeConfigObserveValue = BeanProperties.value("includeTestScope").observe(config);
    bindingContext.bindValue(observeSelectionBtnIncludeTestScopeObserveWidget, includeTestScopeConfigObserveValue, null,
        null);
    //
    IObservableValue observeSelectionBtnIncludeOptionalTransitiveObserveWidget = WidgetProperties.selection()
        .observe(btnIncludeOptionalTransitive);
    IObservableValue includeOptionalConfigObserveValue = BeanProperties.value("includeOptional").observe(config);
    bindingContext.bindValue(observeSelectionBtnIncludeOptionalTransitiveObserveWidget,
        includeOptionalConfigObserveValue, null, null);
    //
    IObservableValue observeSelectionBtnLatestVersionsObserveWidget = WidgetProperties.selection()
        .observe(btnLatestVersions);
    IObservableValue latestVersionsConfigObserveValue = BeanProperties.value("latestVersions").observe(config);
    bindingContext.bindValue(observeSelectionBtnLatestVersionsObserveWidget, latestVersionsConfigObserveValue, null,
        null);
    //
    return bindingContext;
  }
}
