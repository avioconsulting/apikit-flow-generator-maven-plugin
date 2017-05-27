package com.avioconsulting.mule

import groovy.xml.Namespace
import org.codehaus.plexus.util.FileUtils
import org.mule.tools.apikit.ScaffolderAPI

class Generator implements FileUtil {
    public static final Namespace http = new Namespace('http://www.mulesoft.org/schema/mule/http')

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
        fixHttpListenerConfigs(flowPath)
    }

    private static void fixHttpListenerConfigs(File flowPath) {
        def xmlParser = new XmlParser(false, true)
        def flowNode = xmlParser.parse(flowPath)
        Node httpListenerConfig = flowNode[http.'listener-config'][0]
        // Can be populated via a property this way
        httpListenerConfig.'@port' = '${http.port}'
        String existingConfigName = httpListenerConfig.@name
        def httpsListenerConfig = flowNode.appendNode(http.'listener-config')
        httpsListenerConfig.@protocol = 'HTTPS'
        httpsListenerConfig.@name = existingConfigName.replace('httpListenerConfig',
                                                               'httpsListenerConfig')
        new XmlNodePrinter(new IndentPrinter(new FileWriter(flowPath))).print flowNode
    }
}
