package com.avioconsulting.mule

import org.jdom2.output.Format
import org.jdom2.output.XMLOutputter
import org.mule.soapkit.xml.generator.Scaffolder
import org.mule.soapkit.xml.generator.model.buildables.SoapkitApiConfig

class SoapGenerator {
    static void main(String[] args) {
        def wsdlPath = 'src/test/resources/wsdl/input.wsdl'
        def config = new SoapkitApiConfig(wsdlPath,
                                          'WeirdServiceName',
                                          'WeirdPortName')
        def outputFile = new File('mule.xml')
        if (!outputFile.exists()) {
            def stream = getClass().getResourceAsStream('/soap_template.xml')
            outputFile.text = stream.text
        }
        def result = Scaffolder.instance.scaffold(outputFile,
                                                  wsdlPath,
                                                  config)
        def outputter = new XMLOutputter()
        outputter.format = Format.getPrettyFormat()
        outputter.output(result, new FileWriter(outputFile))
    }
}
