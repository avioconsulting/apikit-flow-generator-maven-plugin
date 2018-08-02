package com.avioconsulting.mule

import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Component
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject

@Mojo(name = 'generateFlowSoap')
class SoapGenerateMojo extends AbstractMojo {
    @Parameter(property = 'api.current.version', required = true)
    private String apiVersion

    @Parameter(property = 'wsdl.path', required = true)
    private File wsdlPath

    @Parameter(property = 'wsdl.port', required = true)
    private String wsdlPort

    @Parameter(property = 'wsdl.service', required = true)
    private String wsdlService

    @Parameter(property = 'http.listener.config.name', required = true)
    private String httpListenerConfigName

    @Component
    protected MavenProject mavenProject

    @Override
    void execute() throws MojoExecutionException, MojoFailureException {
        SoapGenerator.generate(mavenProject.basedir,
                               wsdlPath,
                               apiVersion,
                               wsdlService,
                               wsdlPort,
                               httpListenerConfigName)
    }
}
