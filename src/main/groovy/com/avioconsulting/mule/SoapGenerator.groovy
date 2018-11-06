package com.avioconsulting.mule

import com.avioconsulting.mule.resources.SoapResources
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.jdom2.output.Format
import org.jdom2.output.XMLOutputter
import org.mule.soapkit.scaffolder.Scaffolder

import java.util.regex.Pattern

import static com.avioconsulting.mule.MuleDeployPropsCleaner.cleanProps

class SoapGenerator implements FileUtil {
    private static final Pattern listenerPattern = Pattern.compile(/<http:listener path="(.*?)"/)

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
                           'wsdl')
        def appDir = join(mainDir,
                          'app')
        def generatedFilenameOnly = "${FilenameUtils.getBaseName(wsdlPath.name)}_${version}.xml"
        def outputFile = new File(appDir,
                                  generatedFilenameOnly)
        if (outputFile.exists()) {
            // plugin currently duplicates existing files, so don't try and support this
            throw new Exception('You can only use this plugin to do the initial generation of flows from WSDL. Use Studio to perform updates!')
        }
        outputFile.text = SoapResources.SOAP_TEMPLATE
        def tempDomain = File.createTempFile('muledomain',
                                             '.xml')
        try {
            def domainXmlText = SoapResources.DOMAIN_TEMPLATE
                    .replace('OUR_LISTENER_CONFIG',
                             httpListenerConfigName)
            tempDomain.text = domainXmlText
            def result = Scaffolder.instance.scaffold(outputFile,
                                                      wsdlPathStr,
                                                      null, // was config
                                                      tempDomain.absolutePath)
            def outputter = new XMLOutputter()
            outputter.format = Format.getPrettyFormat()
            outputter.output(result,
                             new FileWriter(outputFile))
            // don't want an absolute path in here, need it relative to src/main/wsdl
            // project will ensure wsdl directory is at root of ZIP
            def relativeWsdl = wsdlDir.toPath().relativize(wsdlPath.toPath()).toString()
            def fileXml = outputFile.text
            def newListenerPrefix = insertApiNameInListenerPath ? "/${apiName}/${version}" :
                    "/${version}"
            fileXml = fileXml.replaceAll(listenerPattern,
                                         "<http:listener path=\"${newListenerPrefix}\$1\"")
                    .replace('<apikit-soap:config',
                             '<apikit-soap:config inboundValidationMessage="${validate.soap.requests}"')
                    .replace(wsdlPath.absolutePath,
                             relativeWsdl)
            if (insertXmlBeforeRouter) {
                fileXml = fileXml.replace('<apikit-soap:router',
                                          "${insertXmlBeforeRouter}\r\n    <apikit-soap:router")
            }
            outputFile.text = fileXml
            def muleDeployProps = new Properties()
            def muleDeployPropsFile = new File(appDir,
                                               'mule-deploy.properties')
            muleDeployProps.load(new FileInputStream(muleDeployPropsFile))
            def configResources = muleDeployProps.getProperty('config.resources').split(',').collect { p ->
                p.trim()
            }
            configResources << generatedFilenameOnly.toString()
            muleDeployProps.setProperty('config.resources',
                                        configResources.join(','))
            muleDeployProps.store(new FileOutputStream(muleDeployPropsFile),
                                  'Updated by apikit flow generator plugin')
            cleanProps(muleDeployPropsFile)
        }
        finally {
            FileUtils.forceDelete(tempDomain)
        }
    }
}
