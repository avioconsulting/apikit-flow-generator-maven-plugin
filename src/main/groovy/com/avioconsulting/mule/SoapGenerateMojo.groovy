package com.avioconsulting.mule

import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Component
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject

import javax.wsdl.Port
import javax.wsdl.Service
import javax.wsdl.factory.WSDLFactory

@Mojo(name = 'generateFlowSoap')
class SoapGenerateMojo extends AbstractMojo {
    @Parameter(property = 'api.current.version', required = true)
    private String apiVersion

    @Parameter(property = 'api.name', defaultValue = '${project.artifactId}')
    private String apiName

    @Parameter(property = 'wsdl.path', required = true)
    private File wsdlPath

    @Parameter(property = 'wsdl.port')
    private String wsdlPort

    @Parameter(property = 'wsdl.service')
    private String wsdlService

    @Parameter(property = 'http.listener.config.name', required = true)
    private String httpListenerConfigName

    @Parameter(property = 'apikitgen.mule.xml.insert.before.router')
    private String insertXmlBeforeRouter

    @Parameter(property = 'apikitgen.insert.api.name.in.listener.path',
            defaultValue = 'true')
    private boolean insertApiNameInListenerPath

    @Component
    protected MavenProject mavenProject

    @Override
    void execute() throws MojoExecutionException, MojoFailureException {
        assert wsdlPath.exists()
        def wsdlDef = WSDLFactory.newInstance().newWSDLReader().readWSDL(wsdlPath.absolutePath)
        Service serviceObject = null
        if (!wsdlService) {
            assert wsdlDef.services.size() > 0
            serviceObject = wsdlDef.services.values()[0] as Service
            wsdlService = serviceObject.QName.localPart
        }
        if (!wsdlPort) {
            assert serviceObject
            assert serviceObject.ports.size() > 0
            def portObj = serviceObject.ports.values()[0] as Port
            wsdlPort = portObj.name
        }
        log.info("Generating flows using WSDL at ${wsdlPath}, version ${apiVersion}, service ${wsdlService}, port ${wsdlPort}, and using HTTP listener ${httpListenerConfigName}")
        if (this.insertXmlBeforeRouter) {
            log.info("Adding ${insertXmlBeforeRouter} before generated router...")
        }
        SoapGenerator.generate(mavenProject.basedir,
                               wsdlPath,
                               apiName,
                               apiVersion,
                               httpListenerConfigName,
                               wsdlService,
                               wsdlPort,
                               insertApiNameInListenerPath,
                               insertXmlBeforeRouter)
    }
}
