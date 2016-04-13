package io.takari.m2e.jenkins.launcher.desc;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class Descriptor {

  private String host;
  private int port;
  private String context;
  private String jenkinsWar;
  private List<PluginDesc> plugins;
  private boolean disableCaches;

  @XmlElement
  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  @XmlElement
  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  @XmlElement
  public String getContext() {
    return context;
  }

  public void setContext(String context) {
    this.context = context;
  }

  @XmlElement
  public String getJenkinsWar() {
    return jenkinsWar;
  }

  public void setJenkinsWar(String jenkinsWar) {
    this.jenkinsWar = jenkinsWar;
  }

  @XmlElement(name = "plugin")
  @XmlElementWrapper(name = "plugins")
  public List<PluginDesc> getPlugins() {
    return plugins;
  }

  public void setPlugins(List<PluginDesc> plugins) {
    this.plugins = plugins;
  }

  @XmlElement(name = "disableCaches")
  public boolean isDisableCaches() {
    return disableCaches;
  }

  public void setDisableCaches(boolean disableCaches) {
    this.disableCaches = disableCaches;
  }

  public static Descriptor read(File f) {
    try (InputStream in = new FileInputStream(f)) {
      JAXBContext jaxb = JAXBContext.newInstance(Descriptor.class);
      return (Descriptor) jaxb.createUnmarshaller().unmarshal(in);
    } catch (IOException | JAXBException e) {
      throw new IllegalStateException(e);
    }
  }

  public void write(File f) {
    try (OutputStream out = new FileOutputStream(f)) {
      JAXBContext jaxb = JAXBContext.newInstance(Descriptor.class);
      jaxb.createMarshaller().marshal(this, out);
    } catch (IOException | JAXBException e) {
      throw new IllegalStateException(e);
    }
  }
}
