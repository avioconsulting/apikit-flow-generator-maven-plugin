package com.avioconsulting.mule

import groovy.xml.Namespace
import org.apache.commons.io.IOUtils
import org.codehaus.plexus.util.FileUtils
import org.mule.tools.apikit.ScaffolderAPI
import sun.security.tools.keytool.CertAndKeyGen
import sun.security.x509.*

import java.security.KeyStore
import java.security.cert.Certificate

class Generator implements FileUtil {
    private static final xmlParser = new XmlParser(false, true)
    public static final Namespace http = new Namespace('http://www.mulesoft.org/schema/mule/http')
    public static final Namespace tls = new Namespace('http://www.mulesoft.org/schema/mule/tls')
    public static final Namespace autoDiscovery = new Namespace('http://www.mulesoft.org/schema/mule/api-platform-gw')

    static generate(File baseDirectory,
                    String ramlPath,
                    String apiName,
                    String apiVersion,
                    boolean useCloudHub,
                    String mavenProjectName) {
        def apiBuilder = new ScaffolderAPI()
        def mainDir = join(baseDirectory, 'src', 'main')
        def ramlFile = join(mainDir, 'api', ramlPath)
        assert ramlFile.exists()
        def appDirectory = join(mainDir, 'app')
        apiBuilder.run([ramlFile],
                       appDirectory)
        if (useCloudHub) {
            def ramlText = ramlFile.text
            def baseUri = "https://${mavenProjectName}.cloudhub.io/${mavenProjectName}/api/{version}"
            def fixedRaml = ramlText.replaceAll(/baseUri: .*/,
                                                "baseUri: ${baseUri}")
            ramlFile.write fixedRaml
        }
        def baseName = FileUtils.basename(ramlPath, '.raml')
        def flowFileName = baseName + '.xml'
        def flowPath = new File(appDirectory, flowFileName)
        assert flowPath.exists()
        updateMuleDeployProperties(appDirectory)
        alterGeneratedFlow(flowPath,
                           apiName,
                           apiVersion)
        setupGlobalConfig(appDirectory)
        generateKeyStore(mainDir)
    }

    private static void generateKeyStore(File mainDir) {
        def keystoreDir = join(mainDir, 'resources', 'keystores')
        keystoreDir.mkdirs()
        def keystoreFile = join(keystoreDir, 'listener_keystore.jks')
        if (keystoreFile.exists()) {
            return
        }
        def keystore = KeyStore.getInstance('jks')
        def keystorePassword = 'developmentKeystorePassword'.toCharArray()
        keystore.load(null, keystorePassword)
        def keyGen = new CertAndKeyGen('RSA', 'SHA1WithRSA', null)
        keyGen.generate(1024)
        CertificateExtensions certificateExtensions = getCertExtensions()
        def cert = keyGen.getSelfCertificate(new X500Name('CN=ROOT'),
                                             new Date(),
                                             // 20 years
                                             (long) 20 * 365 * 24 * 3600,
                                             certificateExtensions)
        Certificate[] chain = [cert]
        keystore.setKeyEntry('selfsigned',
                             keyGen.privateKey,
                             keystorePassword,
                             chain)
        def stream = keystoreFile.newOutputStream()
        keystore.store(stream, keystorePassword)
        stream.close()
    }

    // Chrome no longer accepts certs without a subject name
    private static CertificateExtensions getCertExtensions() {
        def certificateExtensions = new CertificateExtensions()
        def names = new GeneralNames()
        names.add(new GeneralName(new DNSName('localhost')))
        def extension = new SubjectAlternativeNameExtension(names)
        certificateExtensions.set(SubjectAlternativeNameExtension.NAME,
                                  extension)
        certificateExtensions
    }

    private static void setupGlobalConfig(File appDirectory) {
        def globalXmlPath = join(appDirectory, 'global.xml')
        if (globalXmlPath.exists()) {
            return
        }
        def input = this.getResourceAsStream('/global_template.xml')
        assert input
        def stream = globalXmlPath.newOutputStream()
        IOUtils.copy(input, stream)
        stream.close()
        def xmlNode = xmlParser.parse(globalXmlPath)
        new XmlNodePrinter(new IndentPrinter(new FileWriter(globalXmlPath))).print xmlNode
    }

    private static void alterGeneratedFlow(File flowPath,
                                           String apiName,
                                           String apiVersion) {
        def flowNode = xmlParser.parse(flowPath)
        removeHttpListenerConfigs(flowNode)
        modifyHttpListeners(flowNode,
                            apiName,
                            apiVersion)
        new XmlNodePrinter(new IndentPrinter(new FileWriter(flowPath))).print flowNode
    }

    private static void modifyHttpListeners(Node flowNode,
                                            String apiName,
                                            String apiVersion) {
        def listeners = flowNode.flow[http.listener] as NodeList
        listeners.each { listener ->
            // supplied via properties to allow HTTP vs. HTTPS toggle at runtime
            listener.'@config-ref' = '${http.listener.config}'
            // want to be able to combine projects later, so be able to share a single listener config
            // by using paths
            def isConsole = (listener.@path as String).contains('console')
            def apiParts = [apiName]
            apiParts << (isConsole ? 'console' : 'api')
            apiParts += [apiVersion, '*']
            listener.@path = '/' + apiParts.join('/')
        }
    }

    private static void removeHttpListenerConfigs(Node flowNode) {
        NodeList httpListenerConfigs = flowNode[http.'listener-config']
        httpListenerConfigs.each { config ->
            flowNode.remove(config)
        }
    }

    private static void updateMuleDeployProperties(File appDirectory) {
        def muleDeployProperties = new Properties()
        def propsPath = join(appDirectory, 'mule-deploy.properties')
        muleDeployProperties.load(propsPath.newInputStream())
        String existingResourceString = muleDeployProperties['config.resources']
        def existingResources = existingResourceString.split(',').collect { String s -> s.trim() }
        if (existingResources.contains('global.xml')) {
            return
        }
        def newResources = ['global.xml'] + existingResources
        muleDeployProperties['config.resources'] = newResources.join(',')
        muleDeployProperties.store(propsPath.newOutputStream(),
                                   'Generated file')
    }
}
