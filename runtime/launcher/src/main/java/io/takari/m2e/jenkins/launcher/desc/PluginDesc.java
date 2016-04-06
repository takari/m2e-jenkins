package io.takari.m2e.jenkins.launcher.desc;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

public class PluginDesc {

  private String id;
  private String location;
  private String pluginFile;
  private List<String> resources;

  @XmlElement
  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  @XmlElement
  public String getLocation() {
    return location;
  }

  public void setLocation(String location) {
    this.location = location;
  }

  @XmlElement
  public String getPluginFile() {
    return pluginFile;
  }

  public void setPluginFile(String pluginFile) {
    this.pluginFile = pluginFile;
  }

  @XmlElement(name = "resource")
  @XmlElementWrapper(name = "resources")
  public List<String> getResources() {
    return resources;
  }

  public void setResources(List<String> resources) {
    this.resources = resources;
  }

}
