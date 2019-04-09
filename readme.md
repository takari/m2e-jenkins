# M2E Jenkins Plugin Development Environment

## Motivation
Developing and especially running/debugging Jenkins plugin projects in Eclipse is [tedious](https://wiki.jenkins-ci.org/display/JENKINS/Eclipse+alternative+build+setup). One has to go through a number of manual steps.

This plugin aims to save plugin developers the hassle of going through those steps as well as to benefit from m2e project configuration and dependency resolution abilities.

## Installation
Point your eclipse to an update site at http://takari.github.io/m2e-jenkins/repo/

## Usage
This plugin provides m2e extension which configures jenkins plugin projects to be buildable within eclipse:
* Sets up m2e project for annotation processing using m2e-apt.
* Sezpoz and hudson.annotation_indexer indexes are regenerated from compiled classes using custom code during full and incremental builds.
* Localizer Messages java sources are generated from .properties files during full and incremental builds.
* Provides launch configuration for running multiple jenkins plugin projects similar to hpi:run.  
  Performs additional steps to decide which versions of transitive dependency plugins to include into the runtime.  
  Test-scoped and optional transitive dependency versions are taken into account (effectively switching from 'nearest' maven resolution strategy to 'highest version').  
  Optionally, launcher will check for latest plugin versions from update center.  
  One can also decide whether to include optional and test dependencies into the runtime.

## Limitations
Jenkins plugins should have at least version 2.0 of plugin parent pom. Versions of parent below 2.0 have a bogus m2e lifecycle mapping configuration which doesn't set up localizer output folder (Messages classes) correctly during m2e project configuration phase.

Annotations marked with @Indexed and @Indexable must be at least at RetentionPolicy.CLASS.

## Building
`mvn clean install -f runtime/pom.xml && mvn clean package`
