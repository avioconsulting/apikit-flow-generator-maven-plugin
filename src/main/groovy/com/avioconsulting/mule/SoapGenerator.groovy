package com.avioconsulting.mule

import com.avioconsulting.mule.resources.SoapResources
import org.apache.commons.io.FilenameUtils

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
        def wsdlPathStr = wsdlPath.absolutePath
        def mainDir = join(baseDirectory,
                           'src',
                           'main')
        def wsdlDir = join(mainDir,
                           'resources',
                           'api')
        def appDir = join(mainDir,
                          'mule')
        def generatedFilenameOnly = "${FilenameUtils.getBaseName(wsdlPath.name)}_${version}.xml"
        def outputFile = new File(appDir,
                                  generatedFilenameOnly)
        if (outputFile.exists()) {
            // plugin currently duplicates existing files, so don't try and support this
            throw new Exception('You can only use this plugin to do the initial generation of flows from WSDL. Use Studio to perform updates!')
        }
        outputFile.text = SoapResources.HEADER +
                '\n' +
                SoapResources.MAIN_FLOW +
                '\n' +
                SoapResources.OPERATION_TEMPLATE +
                '\n' +
                SoapResources.FOOTER
        // don't want an absolute path in here, need it relative to src/main/wsdl
        // project will ensure wsdl directory is at root of ZIP
        def relativeWsdl = wsdlDir.toPath().relativize(wsdlPath.toPath()).toString()
        def fileXml = outputFile.text
        def newListenerPrefix = insertApiNameInListenerPath ? "/${apiName}/${version}" :
                "/${version}"
//            fileXml = fileXml.replaceAll(listenerPattern,
//                                         "<http:listener path=\"${newListenerPrefix}\$1\"")
//                    .replace('<apikit-soap:config',
//                             '<apikit-soap:config inboundValidationMessage="${validate.soap.requests}"')
//                    .replace(wsdlPath.absolutePath,
//                             relativeWsdl)
        if (insertXmlBeforeRouter) {
            fileXml = fileXml.replace('<apikit-soap:router',
                                      "${insertXmlBeforeRouter}\r\n    <apikit-soap:router")
        }
        outputFile.text = fileXml
    }
}
