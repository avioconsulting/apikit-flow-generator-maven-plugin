package com.avioconsulting.mule

import org.apache.commons.lang.StringEscapeUtils
import org.apache.maven.artifact.repository.ArtifactRepository
import org.apache.maven.model.Dependency
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Component
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import org.apache.maven.plugins.annotations.ResolutionScope

@Mojo(name = 'generateFlowRest',
        requiresDependencyResolution = ResolutionScope.COMPILE)
class RestGenerateMojo extends AbstractMojo implements FileUtil {
    @Parameter(property = 'apiName', defaultValue = '${project.artifactId}')
    private String apiName

    @Parameter(property = 'apiVersion')
    private String apiVersion

    @Parameter(property = 'anypointUsername')
    private String anypointUsername

    @Parameter(property = 'anypointPassword')
    private String anypointPassword

    @Parameter(property = 'anypointConnectedAppId')
    private String anypointConnectedAppId

    @Parameter(property = 'anypointConnectedAppSecret')
    private String anypointConnectedAppSecret

    @Parameter(property = 'anypointOrganizationName')
    private String anypointOrganizationName

    @Parameter(property = 'specDcProject')
    private String specDcProject

    @Parameter(property = 'specDcBranch', defaultValue = 'master')
    private String specDcBranch

    @Parameter(property = 'specFilename')
    private String specFilename

    @Parameter(property = 'specDirectory')
    private File specDirectory

    @Parameter(property = 'specGroupId')
    private String specGroupId

    @Parameter(property = 'specArtifactId')
    private String specArtifactId

    @Parameter(property = 'insertApiNameInListenerPath',
            defaultValue = 'true')
    private boolean insertApiNameInListenerPath

    @Parameter(property = 'httpConfigName', defaultValue = 'cloudhub-https-listener')
    private String httpConfigName

    @Parameter(property = 'httpListenerBasePath')
    private String httpListenerBasePath

    @Parameter(property = 'httpListenerPath')
    private String httpListenerPath

//    // TODO: Do we need to keep this at all?  Will remove from refactored code initially
//    @Parameter(property = 'temp.file.of.xml.to.insert.before.router')
//    private File tempFileOfXmlToInsertBeforeRouter
//
//    // TODO: Do we need to keep this at all?  Will remove from refactored code initially
//    @Parameter(property = 'temp.file.of.error.handler.xml.to.replace.stock.with')
//    private File tempFileErrorHandlerXml
//
//    // TODO: Do we need to keep this at all?  Will remove from refactored code initially
//    @Parameter(property = 'temp.file.of.xml.http.response')
//    private File tempFileOfHttpResponseXml
//
//    // TODO: Do we need to keep this at all?  Will remove from refactored code initially
//    @Parameter(property = 'temp.file.of.xml.http.error.response')
//    private File tempFileOfHttpErrorResponseXml

    @Parameter(defaultValue = '${localRepository}', readonly = true, required = true)
    private ArtifactRepository local

    @Component
    protected MavenProject mavenProject

    @Override
    void execute() throws MojoExecutionException, MojoFailureException {

        // Maven will try and resolve the property if it is set on the annotation as default value
//        if (!httpConfigName) {
//            log.info 'No http listener config specified, using default, parameterized value of ${http.listener.config}'
//            httpConfigName = '${http.listener.config}'
//        }

        if (!apiVersion) {
            apiVersion = 'v' + mavenProject.version.split('\\.')[0]
        }
        // Unescape listener base path to support passing property references as part of the path ${}
        httpListenerPath = StringEscapeUtils.unescapeJava(httpListenerPath)

        RestGenerator generator = new RestGenerator(log,
                apiName,
                apiVersion,
                httpConfigName,
                httpListenerBasePath,
                httpListenerPath,
                insertApiNameInListenerPath)

        // Using Local RAML
        if (specDirectory) {
            log.info "Using local API files in directory: ${specDirectory.absolutePath}"
            assert specDirectory.exists(): "API directory does not exist: ${specDirectory.absolutePath}"
            // Validate that specFilename exists if it was explicitly provided
            if (specFilename) {
                def ramlFile = new File(specDirectory, specFilename)
                assert ramlFile.exists(): "RAML file does not exist: ${ramlFile.absolutePath}"
            }
            generator.generateFromLocal(mavenProject.basedir,
                    specDirectory,
                    specFilename)
        // Using RAML from Exchange
        } else if (specGroupId && specArtifactId) {
            log.info "Using API artifact from Exchange: ${specGroupId}:${specArtifactId}"
            def dependencies = mavenProject.getDependencies().findAll { Dependency dep ->
                dep.groupId == specGroupId && dep.artifactId == specArtifactId &&
                        dep.type == 'zip' && dep.classifier in ['raml', 'oas']
            }
            if (dependencies.size() != 1) {
                throw new MojoFailureException("Expected exactly one zip dependency with classifier raml or oas in pom.xml for ${specGroupId}:${specArtifactId}; found ${dependencies.size()}")
            }
            String ramlVersion = dependencies[0].version
            String classifier = dependencies[0].classifier
            def resolved = mavenProject.artifacts.findAll { artifact ->
                artifact.groupId == specGroupId && artifact.artifactId == specArtifactId &&
                        (artifact.version == ramlVersion || artifact.baseVersion == ramlVersion) &&
                        artifact.type == 'zip' && artifact.classifier == classifier
            }
            if (resolved.size() != 1 || !resolved[0].file?.isFile()) {
                throw new MojoFailureException("Maven did not resolve exactly one file for ${specGroupId}:${specArtifactId}:zip:${classifier}:${ramlVersion}")
            }

            generator.generateFromExchange(mavenProject.basedir,
                    specGroupId,
                    specArtifactId,
                    ramlVersion,
                    new File((new URL(local.url)).toURI()),
                    resolved[0].file,
                    classifier)
        // Using RAML from Design Center
        } else if(specDcProject && specDcBranch) {
            log.info "Using Design Center project ${specDcProject} and branch ${specDcBranch}"
            if (anypointUsername && anypointPassword) {
                generator.generateFromDesignCenterWithPassword(mavenProject.basedir,
                        specDcProject,
                        specDcBranch,
                        specFilename,
                        anypointOrganizationName,
                        anypointUsername,
                        anypointPassword)
            } else if (anypointConnectedAppId && anypointConnectedAppSecret) {
                generator.generateFromDesignCenter(mavenProject.basedir,
                        specDcProject,
                        specDcBranch,
                        specFilename,
                        anypointOrganizationName,
                        anypointConnectedAppId,
                        anypointConnectedAppSecret)
            } else {
                throw new MojoFailureException('Values must be provided for either anypointUser/anypointPassword or anypointConnectedAppId/anypointConnectedAppSecret')
            }
        } else {
            throw new MojoFailureException('Values must be provided for either specDirectory/specFilename, specGroupId/specArtifactId or specDcProject/specDcBranch/specFilename')
        }


    }
}
