package com.avioconsulting.mule

import com.avioconsulting.mule.resources.SoapResources
import org.apache.commons.io.FilenameUtils

import javax.wsdl.factory.WSDLFactory

class SoapGenerator implements FileUtil {
    static void generate(File baseDirectory,
                         File wsdlPath,
                         String apiName,
                         String version,
                         String httpListenerConfigName,
                         String service,
                         String port,
                         boolean insertApiNameInListenerPath,
                         String insertXmlBeforeRouter = null) {
        def mainDir = join(baseDirectory,
                           'src',
                           'main')
        def appDir = join(mainDir,
                          'mule')
        def generatedFilenameOnly = "${FilenameUtils.getBaseName(wsdlPath.name)}_${version}.xml"
        def outputFile = new File(appDir,
                                  generatedFilenameOnly)
        if (outputFile.exists()) {
            // plugin currently duplicates existing files, so don't try and support this
            throw new Exception('You can only use this plugin to do the initial generation of flows from WSDL. Use Studio to perform updates!')
        }
        def wsdlFactory = WSDLFactory.newInstance()
        def reader = wsdlFactory.newWSDLReader()
        def definition = reader.readWSDL(wsdlPath.absolutePath)
        def bindings = definition.bindings.values()
        def operationNames = bindings.collect { binding ->
            binding.bindingOperations
        }.flatten()
                .collect { o ->
            o.name as String
        }
        def operationFlows = operationNames.collect { operationName ->
            SoapResources.OPERATION_TEMPLATE.replaceAll('OPERATION_NAME',
                                                        operationName)
        }.join('\n')
        def newListenerPrefix = insertApiNameInListenerPath ? "/${apiName}/${version}" :
                "/${version}"
        def config = SoapResources.APIKIT_CONFIG
                .replaceAll('PORT_NAME',
                            port)
                .replaceAll('SERVICE_NAME',
                            service)
                .replaceAll('WSDL_LOCATION',
                            wsdlPath.name)
        def mainFlow = SoapResources.MAIN_FLOW
                .replaceAll('THE_LISTENER_CONFIG',
                            httpListenerConfigName)
                .replaceAll('THE_LISTENER_PATH',
                            "${newListenerPrefix}/${service}/${port}")
        outputFile.text = SoapResources.HEADER +
                '\n' +
                config +
                '\n' +
                mainFlow +
                '\n' +
                operationFlows +
                SoapResources.FOOTER
        def fileXml = outputFile.text
        if (insertXmlBeforeRouter) {
            fileXml = fileXml.replace('<apikit-soap:router',
                                      "${insertXmlBeforeRouter}\r\n    <apikit-soap:router")
        }
        outputFile.text = fileXml
    }
}
