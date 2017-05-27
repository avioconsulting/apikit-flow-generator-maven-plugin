package com.avioconsulting.mule

import groovy.xml.Namespace
import org.codehaus.plexus.util.FileUtils
import org.mule.tools.apikit.ScaffolderAPI

class Generator implements FileUtil {
    private static final Namespace http = new Namespace('http://www.mulesoft.org/schema/mule/http')

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
        def xmlParser = new XmlParser(false, true)
        def flowNode = xmlParser.parse(flowPath)
        def httpListenerConfig = flowNode[http.'listener-config']
        httpListenerConfig[0].'@port' = '${http.port}'
        new XmlNodePrinter(new IndentPrinter(new FileWriter(flowPath))).print flowNode
    }
}
