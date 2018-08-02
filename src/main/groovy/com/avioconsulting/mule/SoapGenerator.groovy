package com.avioconsulting.mule

import org.apache.commons.io.FilenameUtils
import org.jdom2.output.Format
import org.jdom2.output.XMLOutputter
import org.mule.soapkit.xml.generator.Scaffolder
import org.mule.soapkit.xml.generator.model.buildables.SoapkitApiConfig

class SoapGenerator implements FileUtil {
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
        def result = Scaffolder.instance.scaffold(outputFile,
                                                  wsdlPathStr,
                                                  config)
        def outputter = new XMLOutputter()
        outputter.format = Format.getPrettyFormat()
        outputter.output(result, new FileWriter(outputFile))
    }
}
