package io.takari.m2e.jenkins.internal.ui;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.databinding.Binding;
import org.eclipse.core.databinding.DataBindingContext;
import org.eclipse.core.databinding.UpdateValueStrategy;
import org.eclipse.core.databinding.beans.BeanProperties;
import org.eclipse.core.databinding.observable.set.ISetChangeListener;
import org.eclipse.core.databinding.observable.set.SetChangeEvent;
import org.eclipse.core.databinding.observable.value.IObservableValue;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.jdt.debug.ui.launchConfigurations.JavaLaunchTab;
import org.eclipse.jface.databinding.swt.WidgetProperties;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import io.takari.m2e.jenkins.internal.JenkinsLaunchConfig;
import io.takari.m2e.jenkins.internal.JenkinsPluginProject;
import io.takari.m2e.jenkins.internal.ui.databinding.IntegerToStringConverter;
import io.takari.m2e.jenkins.internal.ui.databinding.IntegerValidator;
import io.takari.m2e.jenkins.internal.ui.databinding.SetSelectionObservable;
import io.takari.m2e.jenkins.internal.ui.databinding.StringToIntegerConverter;

public class JenkinsMainTab extends JavaLaunchTab {

  private JenkinsLaunchConfig config = new JenkinsLaunchConfig();

  private Text txtHost;
  private Text txtPort;

  private DataBindingContext bindingContext;
  private Text txtContext;
  private List<Button> pluginCheckButtons;

  private Combo cmbMainPlugin;

  private List<JenkinsPluginProject> projects;
  private Button btnIncludeTestScope;
  private Button btnIncludeOptionalTransitive;
  private Button btnDisableCaches;
  private Button btnLatestVersions;

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
    config.initializeFrom(configuration);
    bindingContext.updateModels();
  }

  public boolean isValid(ILaunchConfiguration config) {
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
    grpJetty.setLayout(new GridLayout(4, false));
    grpJetty.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 4, 1));

    Label lblHost = new Label(grpJetty, SWT.NONE);
    lblHost.setText("Host");

    txtHost = new Text(grpJetty, SWT.BORDER);
    GridData gd_txtHost = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
    gd_txtHost.widthHint = 150;
    txtHost.setLayoutData(gd_txtHost);

    Label lblPort = new Label(grpJetty, SWT.NONE);
    lblPort.setText("Port");

    txtPort = new Text(grpJetty, SWT.BORDER);
    GridData gd_txtPort = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
    gd_txtPort.widthHint = 50;
    txtPort.setLayoutData(gd_txtPort);
    txtPort.setTextLimit(5);

    Label lblContext = new Label(grpJetty, SWT.NONE);
    lblContext.setText("Context /");

    txtContext = new Text(grpJetty, SWT.BORDER);
    txtContext.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 3, 1));

    Group grpOptions = new Group(comp, SWT.NONE);
    grpOptions.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
    grpOptions.setText("Options");
    grpOptions.setLayout(new GridLayout(1, false));

    btnDisableCaches = new Button(grpOptions, SWT.CHECK);
    btnDisableCaches.setText("Disable caches (slows down responses)");

    Group grpPlugins = new Group(comp, SWT.NONE);
    grpPlugins.setText("Plugin projects");
    grpPlugins.setLayout(new GridLayout(2, false));
    grpPlugins.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 4, 1));

    Label lblMainPlugin = new Label(grpPlugins, SWT.NONE);
    lblMainPlugin.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
    lblMainPlugin.setText("Working plugin");

    cmbMainPlugin = new Combo(grpPlugins, SWT.READ_ONLY);
    cmbMainPlugin.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
    new Label(grpPlugins, SWT.NONE);

    Label lblMainDesc = new Label(grpPlugins, SWT.NONE);
    lblMainDesc.setText("Will be used to store webapp and temp folders");
    lblMainDesc.setFont(JFaceResources.getFontRegistry().getItalic(JFaceResources.DEFAULT_FONT));

    pluginCheckButtons = new ArrayList<>();

    for (JenkinsPluginProject jp : projects) {

      Button btnCheckButton = new Button(grpPlugins, SWT.CHECK);
      btnCheckButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
      btnCheckButton.setText(jp.getProject().getName());
      btnCheckButton.setData(jp);
      pluginCheckButtons.add(btnCheckButton);
    }

    Label lblDependencies = new Label(grpPlugins, SWT.NONE);
    lblDependencies.setText("Dependencies");

    btnIncludeTestScope = new Button(grpPlugins, SWT.CHECK);
    btnIncludeTestScope.setText("Include test scope");

    new Label(grpPlugins, SWT.NONE);
    btnIncludeOptionalTransitive = new Button(grpPlugins, SWT.CHECK);
    btnIncludeOptionalTransitive.setText("Include optional transitive plugins");
    new Label(grpPlugins, SWT.NONE);

    btnLatestVersions = new Button(grpPlugins, SWT.CHECK);
    btnLatestVersions.setText("Use latest available plugin versions");

    updateCombo();

    bindingContext = initDataBindings();

    initAdditionalBindings();

    config.addPropertyChangeListener(new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        updateLaunchConfigurationDialog();
      }
    });
  }

  private void updateCombo() {

    List<String> items = new ArrayList<>();
    for (JenkinsPluginProject jp : projects) {
      String name = jp.getProject().getName();
      if (config.getPlugins().contains(name)) {
        items.add(name);
      }
    }
    String mp = config.getMainPlugin();

    cmbMainPlugin.setItems(items.toArray(new String[items.size()]));
    cmbMainPlugin.select(items.indexOf(mp));
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
        updateCombo();
      }
    });
  }
  protected DataBindingContext initDataBindings() {
    DataBindingContext bindingContext = new DataBindingContext();
    //
    IObservableValue observeTextTxtHostObserveWidget = WidgetProperties.text(SWT.Modify).observe(txtHost);
    IObservableValue hostConfigObserveValue = BeanProperties.value("host").observe(config);
    bindingContext.bindValue(observeTextTxtHostObserveWidget, hostConfigObserveValue, null, null);
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
    IObservableValue observeSelectionCmbMainPluginObserveWidget = WidgetProperties.selection().observe(cmbMainPlugin);
    IObservableValue mainPluginConfigObserveValue = BeanProperties.value("mainPlugin").observe(config);
    bindingContext.bindValue(observeSelectionCmbMainPluginObserveWidget, mainPluginConfigObserveValue, null, null);
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
