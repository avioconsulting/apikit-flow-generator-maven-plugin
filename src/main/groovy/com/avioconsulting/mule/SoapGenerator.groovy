package com.avioconsulting.mule

import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.jdom2.output.Format
import org.jdom2.output.XMLOutputter
import org.mule.soapkit.xml.generator.Scaffolder
import org.mule.soapkit.xml.generator.model.buildables.SoapkitApiConfig

import java.util.regex.Pattern

class SoapGenerator implements FileUtil {
    private static final Pattern listenerPattern = Pattern.compile(/<http:listener path="(.*?)"/)

    static void generate(File baseDirectory,
                         File wsdlPath,
                         String version,
                         String service,
                         String port,
                         String httpListenerConfigName) {
        def wsdlPathStr = wsdlPath.absolutePath
        def config = new SoapkitApiConfig(wsdlPathStr,
                                          service,
                                          port)
        File appDir = join(baseDirectory, 'src', 'main', 'app')
        def generatedFilenameOnly = "${FilenameUtils.getBaseName(wsdlPath.name)}_${version}.xml"
        def outputFile = new File(appDir,
                                  generatedFilenameOnly)
        if (!outputFile.exists()) {
            def stream = getClass().getResourceAsStream('/soap_template.xml')
            outputFile.text = stream.text
        }
        def tempDomain = File.createTempFile('muledomain', '.xml')
        try {
            def domainXmlText = getClass().getResourceAsStream('/domain_template.xml')
                    .text
                    .replace('OUR_LISTENER_CONFIG', httpListenerConfigName)
            tempDomain.text = domainXmlText
            def result = Scaffolder.instance.scaffold(outputFile,
                                                      wsdlPathStr,
                                                      config,
                                                      tempDomain.absolutePath)
            def outputter = new XMLOutputter()
            outputter.format = Format.getPrettyFormat()
            outputter.output(result, new FileWriter(outputFile))
            def fileXml = outputFile.text
            fileXml = fileXml.replaceAll(listenerPattern,
                                         "<http:listener path=\"/${version}\$1\"")
                    .replace('<apikit-soap:config',
                             '<apikit-soap:config inboundValidationMessage="${validate.soap.requests}"')
            outputFile.text = fileXml
            def muleDeployProps = new Properties()
            def muleDeployPropsFile = new File(appDir, 'mule-deploy.properties')
            muleDeployProps.load(new FileInputStream(muleDeployPropsFile))
            def configResources = muleDeployProps.getProperty('config.resources').split(',').collect { p ->
                p.trim()
            }
            configResources << generatedFilenameOnly.toString()
            muleDeployProps.setProperty('config.resources', configResources.join(','))
            muleDeployProps.store(new FileOutputStream(muleDeployPropsFile), 'Updated by apikit flow generator plugin')
        }
        finally {
            FileUtils.forceDelete(tempDomain)
        }
    }
}
