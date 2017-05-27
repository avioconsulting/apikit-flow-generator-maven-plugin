package com.avioconsulting.mule

import groovy.xml.Namespace
import org.apache.commons.io.IOUtils
import org.codehaus.plexus.util.FileUtils
import org.mule.tools.apikit.ScaffolderAPI

class Generator implements FileUtil {
    private static final xmlParser = new XmlParser(false, true)
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
        def input = this.getResourceAsStream('/global_template.xml')
        assert input
        def stream = globalXmlPath.newOutputStream()
        IOUtils.copy(input, stream)
        stream.close()
        def xmlNode = xmlParser.parse(globalXmlPath)
        Node httpListener = xmlNode[http.'listener-config'].find { Node n ->
            n.'@name' == 'http_replace_me'
        }
        httpListener.@name = "${baseName}-httpListenerConfig"
        Node httpsListener = xmlNode[http.'listener-config'].find { Node n ->
            n.'@name' == 'https_replace_me'
        }
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
