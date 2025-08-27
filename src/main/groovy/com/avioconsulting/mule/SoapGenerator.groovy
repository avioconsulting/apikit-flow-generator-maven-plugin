package com.avioconsulting.mule

import com.avioconsulting.mule.resources.SoapResources
import org.apache.commons.io.FilenameUtils

import javax.wsdl.factory.WSDLFactory

// Implemented this by hand because the Studio 7.x/Mule 4.x SOAP generator scaffolds have heavy Eclipse dependencies
class SoapGenerator implements FileUtil {
    
    /**
     * Generates SOAP configuration content from WSDL file and returns as a Map of filename to content strings.
     * This method performs the core generation logic without writing files to disk.
     * 
     * @param wsdlPath WSDL file to process
     * @param apiName API name for path generation
     * @param version API version for path generation
     * @param httpListenerConfigName HTTP listener configuration reference
     * @param service WSDL service name
     * @param port WSDL port name
     * @param insertApiNameInListenerPath Whether to include API name in listener path
     * @param insertXmlBeforeRouter Optional XML to insert before router
     * @param insertXmlAfterRouter Optional XML to insert after router
     * @return Map where key is the config filename and value is the generated XML content as string
     */
    static Map<String, String> generateConfigs(File wsdlPath,
                                               String apiName,
                                               String version,
                                               String httpListenerConfigName,
                                               String service,
                                               String port,
                                               boolean insertApiNameInListenerPath,
                                               String insertXmlBeforeRouter = null,
                                               String insertXmlAfterRouter = null) {
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
        
        def generatedContent = SoapResources.HEADER +
                '\n' +
                config +
                '\n' +
                mainFlow +
                '\n' +
                operationFlows +
                SoapResources.FOOTER
        
        // Apply optional XML insertions
        if (insertXmlBeforeRouter) {
            generatedContent = generatedContent.replace('<apikit-soap:router',
                                      "${insertXmlBeforeRouter}\r\n    <apikit-soap:router")
        }
        if (insertXmlAfterRouter) {
            generatedContent = generatedContent.replace('</apikit-soap:router>',
                                      "</apikit-soap:router>\r\n    ${insertXmlAfterRouter}")
        }
        
        def generatedFilenameOnly = "${FilenameUtils.getBaseName(wsdlPath.name)}_${version}.xml"
        return [(generatedFilenameOnly): generatedContent]
    }
    
    static void generate(File baseDirectory,
                         File wsdlPath,
                         String apiName,
                         String version,
                         String httpListenerConfigName,
                         String service,
                         String port,
                         boolean insertApiNameInListenerPath,
                         String insertXmlBeforeRouter = null,
                         String insertXmlAfterRouter = null) {
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
        
        // Generate configs using the new method
        def configMap = generateConfigs(wsdlPath, apiName, version, httpListenerConfigName,
                                        service, port, insertApiNameInListenerPath,
                                        insertXmlBeforeRouter, insertXmlAfterRouter)
        
        // Write the generated content to file
        configMap.each { filename, content ->
            new File(appDir, filename).text = content
        }
    }
}
