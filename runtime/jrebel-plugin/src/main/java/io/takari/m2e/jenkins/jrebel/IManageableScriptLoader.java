package io.takari.m2e.jenkins.jrebel;

import org.kohsuke.stapler.MetaClass;

public interface IManageableScriptLoader {

  ScriptCacheManager __getCacheManager();

  MetaClass getOwner();

}
