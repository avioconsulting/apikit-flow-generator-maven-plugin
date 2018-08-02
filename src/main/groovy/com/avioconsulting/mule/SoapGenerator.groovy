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
        def outputFile = new File(join(baseDirectory, 'src', 'main', 'app'),
                                  "${FilenameUtils.getBaseName(wsdlPath.name)}_${version}.xml")
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
            outputFile.text = fileXml
        }
        finally {
            FileUtils.forceDelete(tempDomain)
        }
    }
}
