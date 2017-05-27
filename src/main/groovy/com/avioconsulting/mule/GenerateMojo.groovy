package com.avioconsulting.mule

import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Component
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject

@Mojo(name = 'generateFlow')
class GenerateMojo extends AbstractMojo {
    @Parameter(property = 'api.name')
    private String apiName

    @Parameter(property = 'api.version')
    private String apiVersion

    @Parameter(property = 'raml.path', defaultValue = 'api-${api.name}-${api.version}.raml')
    private String ramlPath

    @Component
    protected MavenProject mavenProject

    @Override
    void execute() throws MojoExecutionException, MojoFailureException {
        Generator.generate(mavenProject.basedir,
                           ramlPath,
                           apiName,
                           apiVersion)
    }
}
