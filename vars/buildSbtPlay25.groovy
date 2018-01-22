/*
 * Toolform-compatible Jenkins 2 Pipeline build step for Play Framework 2.6 based components built using SBT
 */
// baseDir: "server",
// project: "source-ip",
// component: "public",
// buildNumber: buildNumber

def call(Map config) {
  final sbt = { cmd ->
    ansiColor('xterm') {
      dir(config.baseDir) {
        sh "sbt -batch -sbt-dir /home/jenkins/.sbt -Dsbt.repository.config=/home/jenkins/sbt.boot.properties -Dsbt.ivy.home=/home/jenkins/.ivy2/ -Divy.home=/home/jenkins/.ivy2/ -v \"${cmd}\""
      }
    }
  }

  def fullComponentName = "${config.project}-${config.component}"
  def buildVersion = config.buildNumber

  container('build-sbt-play25') {

    stage('Build Details') {
      sh "echo Project:   ${config.project}"
      sh "echo Component: ${config.component}"
      sh "echo BuildNumber: ${config.buildNumber}"
    }

    stage('Prepare environment') {
      writeFile(file: "/home/jenkins/sbt.boot.properties", 
        text: libraryResource('au/com/agiledigital/jenkins-pipelines/build-sbt-play25/sbt.boot.properties'))
    }

    stage('Fetch dependencies') {
      sh 'ls *.conf'
      sbt "update"
    }

    stage('Compile') {
      sbt "compile"
    }

    stage('Test') {
      sbt ";project ${config.component}; testOnly ** -- junitxml console"
      junit "${config.baseDir}/modules/**/target/test-reports/**/*.xml"
    }

    stage('Inject configuration') {
      // TODO: Allow ${SETTINGS_CONTEXT} to be overriden
      withEnv(["SUB_PATH=${config.baseDir}/modules/api/"]) {
        // From https://stash.agiledigital.com.au/projects/MCP/repos/docker-builder/browse/builders/play2-multi-build/build.sh
        sh '''
        |# Insert the project.conf, environment.conf, etc into the deployable.
        |cp *.conf "${SUB_PATH}conf"
        |
        |# Create the conf file that ties the application.conf and environment.conf together.
        |echo 'include "application.conf"' > "${SUB_PATH}conf/combined.conf"
        |echo 'include "environment.conf"' >> "${SUB_PATH}conf/combined.conf"
        |echo 'include "topology.conf"' >> "${SUB_PATH}conf/combined.conf"
        |
        |# Allow the application.context variable to be overridden.
        |echo 'play.http.context=/' >> "${SUB_PATH}conf/combined.conf"
        |echo 'play.http.context=${?APPLICATION_CONTEXT}' >> "${SUB_PATH}conf/combined.conf"
        |
        |# Allow cryto to be changed
        |echo 'play.crypto.secret=${?APPLICATION_SECRET}' >> "${SUB_PATH}conf/combined.conf"
        |
        |cat "${SUB_PATH}conf/combined.conf"
        |
        |'''.stripMargin()
      }
    }
    stage('Package') {
      sbt ";project ${config.component}; set name := \"${fullComponentName}\"; set version: \"${buildVersion}\"; dist"
    }
  }

  stage('Archive to Jenkins') {
    archiveArtifacts "${config.baseDir}/modules/${config.component}/target/universal/${fullComponentName}-${buildVersion}.zip"
  }
}