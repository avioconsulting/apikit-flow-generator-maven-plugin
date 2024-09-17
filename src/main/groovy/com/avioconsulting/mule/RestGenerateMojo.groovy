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
    @Parameter(property = 'apiName')
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

    @Parameter(property = 'ramlDcProject')
    private String ramlDcProject

    @Parameter(property = 'ramlDcBranch', defaultValue = 'master')
    private String ramlDcBranch

    @Parameter(property = 'ramlFilename')
    private String ramlFilename

    @Parameter(property = 'ramlDirectory')
    private File ramlDirectory

    @Parameter(property = 'ramlGroupId')
    private String ramlGroupId

    @Parameter(property = 'ramlArtifactId')
    private String ramlArtifactId

    @Parameter(property = 'insertApiNameInListenerPath',
            defaultValue = 'true')
    private boolean insertApiNameInListenerPath

    @Parameter(property = 'httpConfigName')
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
        if (!httpConfigName) {
            log.info 'No http listener config specified, using default, parameterized value of ${http.listener.config}'
            httpConfigName = '${http.listener.config}'
        }

        // Unescape listener base path to support passing property references as part of the path ${}
        httpListenerPath = StringEscapeUtils.unescapeJava(httpListenerPath)

        RestGenerator generator = new RestGenerator(log)

        // Using Local RAML
        if (ramlDirectory) {
            log.info "Using local RAML files in directory: ${ramlDirectory.absolutePath}"
            assert ramlDirectory.exists()
            assert new File(ramlDirectory, ramlFilename).exists()
            generator.generateFromLocal(mavenProject.basedir,
                    apiName,
                    apiVersion,
                    httpListenerBasePath,
                    httpListenerPath,
                    insertApiNameInListenerPath,
                    ramlDirectory,
                    ramlFilename)
        // Using RAML from Exchange
        } else if (ramlGroupId && ramlArtifactId) {
            log.info "Using RAML artifact from exchange: ${ramlGroupId}:${ramlArtifactId}"
            String ramlVersion = null
            mavenProject.getDependencies().each { Dependency dep ->
                if(dep.groupId == ramlGroupId && dep.artifactId == ramlArtifactId) {
                    ramlVersion = dep.getVersion()
                }
            }
            if(!ramlVersion) {
                throw new MojoFailureException("No RAML dependency found in pom.xml for ${ramlGroupId}:${ramlArtifactId}")
            }

            generator.generateFromExchange(mavenProject.basedir,
                    apiName,
                    apiVersion,
                    httpListenerBasePath,
                    httpListenerPath,
                    insertApiNameInListenerPath,
                    ramlGroupId,
                    ramlArtifactId,
                    ramlVersion,
                    new File((new URL(local.url)).toURI()))
        // Using RAML from Design Center
        } else if(ramlDcProject && ramlDcBranch) {
            log.info "Using Design Center project ${ramlDcBranch} and branch ${ramlDcBranch}"
            if (anypointUsername && anypointPassword) {
                generator.generateFromDesignCenterWithPassword(mavenProject.basedir,
                        apiName,
                        apiVersion,
                        httpListenerBasePath,
                        httpListenerPath,
                        insertApiNameInListenerPath,
                        ramlDcProject,
                        ramlDcBranch,
                        ramlFilename,
                        anypointOrganizationName,
                        anypointUsername,
                        anypointPassword)
            } else if (anypointConnectedAppId && anypointConnectedAppSecret) {
                generator.generateFromDesignCenter(mavenProject.basedir,
                        apiName,
                        apiVersion,
                        httpListenerBasePath,
                        httpListenerPath,
                        insertApiNameInListenerPath,
                        ramlDcProject,
                        ramlDcBranch,
                        ramlFilename,
                        anypointOrganizationName,
                        anypointConnectedAppId,
                        anypointConnectedAppSecret)
            } else {
                throw new MojoFailureException('Values must be provided for either anypointUser/anypointPassword or anypointConnectedAppId/anypointConnectedAppSecret')
            }
        } else {
            throw new MojoFailureException('Values must be provided for either ramlDirectory/ramlFilename, ramlGroupId/ramlArtifactId or ramlDcProject/ramlDcBranch/ramlFilename')
        }

        // TODO: This needs to be done in the generator for both local and DC
        // Use first RAML file in the root directory as the main one if a specific one is not provided
//        if (!ramlFilename || ramlFilename == 'NotUsed') {
//
//            def topLevelFiles = new FileNameFinder().getFileNames(apiDirectory.absolutePath,
//                    '*.raml')
//            // we don't want the full path
//            ramlFilename = new File(topLevelFiles[0]).name
//            log.info "Assuming ${ramlFilename} is the top level RAML file"
//        }


    }
}
