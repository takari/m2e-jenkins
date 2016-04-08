# M2E Jenkins Plugin Development Environment

## Motivation
Developing and especially running/debugging Jenkins plugin projects in Eclipse is [problematic](https://wiki.jenkins-ci.org/display/JENKINS/Eclipse+alternative+build+setup). One has to go through a number of manual steps.

This plugin aims to save plugin developers the hassle of going through those steps as well as to benefit from m2e project configuration and dependency resolution abilities.

## Installation
Point your eclipse to an update site at https://repository.takari.io/content/sites/m2e.extras/jenkins-dev/0.1.0/N/LATEST/

## Usage
This plugin provides m2e extension which configures jenkins plugin projects to be buildable within eclipse:
* Sets up m2e project for annotation processing using m2e-apt
* Configures apt for sezpoz library to be run in 'batch mode'
* Provides launch configuration for running multiple jenkins plugin projects similar to hpi:run.

## Limitations
Jenkins plugins should have at least version 2.0 of plugin parent pom. Versions of parent below 2.0 have a bogus m2e lifecycle mapping configuration which doesn't set up localizer output folder (Messages class) correctly during m2e project configuration phase.

## Building
`mvn clean install -f runtime/pom.xml && mvn clean package`
