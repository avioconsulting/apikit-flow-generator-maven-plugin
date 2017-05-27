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
        removeHttpListenerConfig(flowPath)
        setupGlobalConfig(appDirectory, baseName)
    }

    private static void setupGlobalConfig(File appDirectory, String baseName) {
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
        def httpListener = xmlNode[http.'listener-config'].find { Node n ->
            n.'@name' == 'http_replace_me'
        } as Node
        httpListener.@name = "${baseName}-httpListenerConfig"
        def httpsListener = xmlNode[http.'listener-config'].find { Node n ->
            n.'@name' == 'https_replace_me'
        } as Node
        httpsListener.@name = "${baseName}-httpsListenerConfig"
        new XmlNodePrinter(new IndentPrinter(new FileWriter(globalXmlPath))).print xmlNode
    }

    private static void removeHttpListenerConfig(File flowPath) {
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
}
