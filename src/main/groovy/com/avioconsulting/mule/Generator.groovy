package com.avioconsulting.mule

import groovy.xml.Namespace
import org.codehaus.plexus.util.FileUtils
import org.mule.tools.apikit.ScaffolderAPI

class Generator implements FileUtil {
    public static final Namespace http = new Namespace('http://www.mulesoft.org/schema/mule/http')
    public static final Namespace tls = new Namespace('http://www.mulesoft.org/schema/mule/tls')

    static generate(File baseDirectory,
                    String ramlPath) {
        def apiBuilder = new ScaffolderAPI()
        def mainDir = join(baseDirectory, 'src', 'main')
        def ramlFile = join(mainDir, 'api', ramlPath)
        assert ramlFile.exists()
        def appDirectory = join(mainDir, 'app')
        apiBuilder.run([ramlFile],
                       appDirectory)
        def flowFileName = FileUtils.basename(ramlPath) + 'xml'
        def flowPath = new File(appDirectory, flowFileName)
        assert flowPath.exists()
        updateMuleDeployProperties(appDirectory)
        removeHttpListenerConfig(flowPath)
    }

    private static void removeHttpListenerConfig(File flowPath) {
        def xmlParser = new XmlParser(false, true)
        def flowNode = xmlParser.parse(flowPath)
        NodeList httpListenerConfigs = flowNode[http.'listener-config']
        httpListenerConfigs.each { config ->
            flowNode.remove(config)
        }
        new XmlNodePrinter(new IndentPrinter(new FileWriter(flowPath))).print flowNode
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

    private static void fixHttpListenerConfigs(File flowPath) {
        def xmlParser = new XmlParser(false, true)
        def flowNode = xmlParser.parse(flowPath)
        Node httpListenerConfig = flowNode[http.'listener-config'][0]
        // Can be populated via a property this way
        httpListenerConfig.'@port' = '${http.port}'
        String existingConfigName = httpListenerConfig.@name
        Node httpsListenerConfig = flowNode.appendNode(http.'listener-config')
        httpsListenerConfig.@protocol = 'HTTPS'
        httpsListenerConfig.'@port' = '${https.port}'
        httpsListenerConfig.'@host' = '0.0.0.0'
        httpsListenerConfig.@name = existingConfigName.replace('httpListenerConfig',
                                                               'httpsListenerConfig')
        def tlsContext = httpsListenerConfig.appendNode(tls.'context')
        def tlsKeystore = tlsContext.appendNode(tls.'key-store')
        tlsKeystore.'@type' = 'jks'
        tlsKeystore.'@path' = 'keystores/listener_keystore.jks'
        tlsKeystore.'@alias' = 'selfsigned'
        tlsKeystore.'@keyPassword' = 'changeit'
        tlsKeystore.'@password' = 'changeit'
        new XmlNodePrinter(new IndentPrinter(new FileWriter(flowPath))).print flowNode
        // easiest way to update the namespace
        //def xmlText = flowPath.text
        //xmlText.replace('xsi:schemaLocation="')
    }
}
