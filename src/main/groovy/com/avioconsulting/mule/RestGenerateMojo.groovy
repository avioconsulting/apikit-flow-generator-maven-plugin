package com.avioconsulting.mule

import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Component
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject

@Mojo(name = 'generateFlowRest')
class RestGenerateMojo extends AbstractMojo {
    @Parameter(property = 'api.name')
    private String apiName

    @Parameter(property = 'api.current.version')
    private String currentApiVersion

    @Parameter(property = 'raml.path', defaultValue = 'api-${api.name}-${api.current.version}.raml')
    private String ramlPath

    @Parameter(property = 'use.cloudHub', defaultValue = 'true')
    private boolean useCloudHub

    @Parameter(property = 'apikitgen.insert.api.name.in.listener.path',
            defaultValue = 'true')
    private boolean insertApiNameInListenerPath

    @Parameter(property = 'http.listener.config.name', required = true)
    private String httpListenerConfigName

    @Component
    protected MavenProject mavenProject

    @Override
    void execute() throws MojoExecutionException, MojoFailureException {
        RestGenerator.generate(mavenProject.basedir,
                               ramlPath,
                               apiName,
                               currentApiVersion,
                               useCloudHub,
                               insertApiNameInListenerPath,
                               mavenProject.artifactId,
                               httpListenerConfigName)
    }
}
