package com.avioconsulting.mule

import groovy.xml.Namespace
import org.apache.commons.io.IOUtils
import org.codehaus.plexus.util.FileUtils
import org.mule.tools.apikit.ScaffolderAPI

class Generator implements FileUtil {
    private static final xmlParser = new XmlParser(false, true)
    public static final Namespace http = new Namespace('http://www.mulesoft.org/schema/mule/http')
    public static final Namespace tls = new Namespace('http://www.mulesoft.org/schema/mule/tls')
    public static final Namespace autoDiscovery = new Namespace('http://www.mulesoft.org/schema/mule/api-platform-gw')

    static generate(File baseDirectory,
                    String ramlPath,
                    String apiName,
                    String apiVersion) {
        def apiBuilder = new ScaffolderAPI()
        def mainDir = join(baseDirectory, 'src', 'main')
        def ramlFile = join(mainDir, 'api', ramlPath)
        assert ramlFile.exists()
        def appDirectory = join(mainDir, 'app')
        apiBuilder.run([ramlFile],
                       appDirectory)
        def baseName = FileUtils.basename(ramlPath, '.raml')
        def flowFileName = baseName + '.xml'
        def flowPath = new File(appDirectory, flowFileName)
        assert flowPath.exists()
        updateMuleDeployProperties(appDirectory)
        alterGeneratedFlow(flowPath)
        setupGlobalConfig(appDirectory,
                          baseName,
                          apiName,
                          apiVersion)
    }

    private static void setupGlobalConfig(File appDirectory,
                                          String baseName,
                                          String apiName,
                                          String apiVersion) {
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
        setupHttpListeners(xmlNode, baseName)
        setupAutoDiscovery(xmlNode, apiName, apiVersion, baseName)
        new XmlNodePrinter(new IndentPrinter(new FileWriter(globalXmlPath))).print xmlNode
    }

    private static void setupAutoDiscovery(Node xmlNode, String apiName, String apiVersion, String baseName) {
        def autoDiscoveryNode = xmlNode[autoDiscovery.'api'][0] as Node
        assert autoDiscoveryNode
        // deal with API Manager's lack of environment specific API Definitions
        autoDiscoveryNode.@apiName = "${apiName}\${api.env.suffix}"
        autoDiscoveryNode.@version = apiVersion
        // the naming convention auto discovery uses
        autoDiscoveryNode.@flowRef = "${baseName}-main"
        autoDiscoveryNode.@apikitRef = "${baseName}-config"
    }

    private static void setupHttpListeners(Node xmlNode, String baseName) {
        def httpListener = xmlNode[http.'listener-config'].find { Node n ->
            n.'@name' == 'http_replace_me'
        } as Node
        httpListener.@name = "${baseName}-httpListenerConfig"
        def httpsListener = xmlNode[http.'listener-config'].find { Node n ->
            n.'@name' == 'https_replace_me'
        } as Node
        httpsListener.@name = "${baseName}-httpsListenerConfig"
    }

    private static void alterGeneratedFlow(File flowPath) {
        def flowNode = xmlParser.parse(flowPath)
        removeHttpListenerConfigs(flowNode)
        parameterizeHttpListeners(flowNode)
        new XmlNodePrinter(new IndentPrinter(new FileWriter(flowPath))).print flowNode
    }

    private static void parameterizeHttpListeners(Node flowNode) {
        def listeners = flowNode.flow[http.listener] as NodeList
        listeners.each { listener ->
            // supplied via properties to allow HTTP vs. HTTPS toggle
            listener.'@config-ref' = '${http.listener.config}'
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
