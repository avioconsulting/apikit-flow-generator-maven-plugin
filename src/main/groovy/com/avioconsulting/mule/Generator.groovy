package com.avioconsulting.mule

import groovy.xml.Namespace
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
                    String apiVersion,
                    boolean useCloudHub,
                    String mavenProjectName) {
        def apiBuilder = new ScaffolderAPI()
        def mainDir = join(baseDirectory, 'src', 'main')
        def ramlFile = join(mainDir, 'api', ramlPath)
        assert ramlFile.exists()
        def appDirectory = join(mainDir, 'app')
        def baseName = FileUtils.basename(ramlPath, '.raml')
        def flowFileName = baseName + '.xml'
        def flowFile = join(appDirectory, flowFileName)
        if (flowFile.exists()) {
            // utility works best with a clean file
            if (!flowFile.delete()) {
                assert flowFile.delete()
            }
        }
        apiBuilder.run([ramlFile],
                       appDirectory)
        if (useCloudHub) {
            adjustRamlBaseUri(ramlFile, mavenProjectName)
        }
        def flowPath = new File(appDirectory, flowFileName)
        assert flowPath.exists()
        alterGeneratedFlow(flowPath,
                           apiName,
                           apiVersion)
    }

    private static void adjustRamlBaseUri(File ramlFile, String mavenProjectName) {
        def ramlText = ramlFile.text
        def baseUri = "https://${mavenProjectName}.cloudhub.io/${mavenProjectName}/api/{version}"
        def fixedRaml = ramlText.replaceAll(/baseUri: .*/,
                                            "baseUri: ${baseUri}")
        ramlFile.write fixedRaml
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
}
