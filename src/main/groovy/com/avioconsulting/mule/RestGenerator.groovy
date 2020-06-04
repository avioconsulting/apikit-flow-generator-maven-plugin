package com.avioconsulting.mule

import org.apache.commons.io.FilenameUtils
import org.codehaus.plexus.util.FileUtils
import org.jdom2.CDATA
import org.jdom2.Element
import org.jdom2.Namespace
import org.jdom2.input.SAXBuilder
import org.jdom2.output.Format
import org.jdom2.output.XMLOutputter
import org.mule.apikit.model.api.ApiReference
import org.mule.parser.service.strategy.RamlParsingStrategy
import org.mule.tools.apikit.MainAppScaffolder
import org.mule.tools.apikit.model.RuntimeEdition
import org.mule.tools.apikit.model.ScaffolderContext
import org.mule.tools.apikit.model.ScaffoldingConfiguration

class RestGenerator implements FileUtil {
    public static final Namespace core = Namespace.getNamespace('http://www.mulesoft.org/schema/mule/core')
    public static final Namespace http = Namespace.getNamespace('http',
                                                                'http://www.mulesoft.org/schema/mule/http')
    public static final Namespace apiKit = Namespace.getNamespace('apikit',
                                                                  'http://www.mulesoft.org/schema/mule/mule-apikit')
    public static final Namespace xsi = Namespace.getNamespace('xsi',
                                                               'http://www.w3.org/2001/XMLSchema-instance')
    public static final Namespace doc = Namespace.getNamespace('doc',
                                                               'http://www.mulesoft.org/schema/mule/documentation')
    public static final Namespace ee = Namespace.getNamespace('ee',
                                                              'http://www.mulesoft.org/schema/mule/ee/core')

    static generate(File baseDirectory,
                    String ramlPath,
                    String apiName,
                    String apiVersion,
                    boolean useCloudHub,
                    boolean insertApiNameInListenerPath,
                    String mavenProjectName,
                    String httpListenerConfigName,
                    String insertXmlBeforeRouter,
                    String errorHandler) {
        // without runtime edition EE, we won't use weaves in the output
        def scaffolder = new MainAppScaffolder(new ScaffolderContext(RuntimeEdition.EE))
        def mainDir = join(baseDirectory,
                           'src',
                           'main')
        def ramlFile = join(mainDir,
                            'resources',
                            'api',
                            ramlPath)
        assert ramlFile.exists()
        def appDirectory = join(mainDir,
                                'mule')
        def baseName = FileUtils.basename(ramlPath,
                                          '.raml')
        def flowFileName = baseName + '.xml'
        def flowFile = join(appDirectory,
                            flowFileName)
        if (flowFile.exists()) {
            // utility works best with a clean file
            if (!flowFile.delete()) {
                // Windows
                System.gc()
                Thread.yield()
                assert flowFile.delete()
            }
        }
        // generates the flow
        def parseResult = new RamlParsingStrategy().parse(ApiReference.create(ramlFile.absolutePath))
        assert parseResult.errors == []
        def result = scaffolder.run(ScaffoldingConfiguration.builder()
                                            .withApi(parseResult.get())
                                            .withMuleConfigurations([])
                                            .build())
        assert result.errors == []
        assert result.generatedConfigs.size() > 0
        result.generatedConfigs.each { config ->
            new File(appDirectory,
                     config.name).text = config.content.text
        }
        if (useCloudHub) {
            adjustRamlBaseUri(ramlFile,
                              apiName,
                              mavenProjectName)
        }
        def flowPath = new File(appDirectory,
                                flowFileName)
        assert flowPath.exists()
        // Mule's generator will use the RAML filename by convention
        def apiBaseName = FilenameUtils.getBaseName(ramlPath)
        alterGeneratedFlow(flowPath,
                           apiName,
                           apiVersion,
                           apiBaseName,
                           insertApiNameInListenerPath,
                           httpListenerConfigName)
    }

    private static void adjustRamlBaseUri(File ramlFile,
                                          String apiName,
                                          String mavenProjectName) {
        def ramlText = ramlFile.text
        def baseUri = "https://${mavenProjectName}.cloudhub.io/${apiName}/api/{version}"
        def fixedRaml = ramlText.replaceAll(/baseUri: .*/,
                                            "baseUri: ${baseUri}")
        ramlFile.write fixedRaml
    }

    private static void alterGeneratedFlow(File flowPath,
                                           String apiName,
                                           String apiVersion,
                                           String apiBaseName,
                                           boolean insertApiNameInListenerPath,
                                           String httpListenerConfigName) {
        def builder = new SAXBuilder()
        def document = builder.build(flowPath)
        def rootElement = document.rootElement
        removeHttpListenerConfigs(rootElement)
        modifyHttpListeners(rootElement,
                            apiName,
                            apiVersion,
                            insertApiNameInListenerPath,
                            httpListenerConfigName)
        parameterizeApiKitConfig(rootElement)
        removeConsole(rootElement,
                      apiBaseName)
        def outputter = new XMLOutputter(Format.prettyFormat)
        outputter.output(document,
                         new FileWriter(flowPath))
    }

    private static void removeConsole(Element rootElement,
                                      String apiBaseName) {
        allowDetailedValidationInfo(rootElement,
                                    apiBaseName)
        def lookFor = "${apiBaseName}-console"
        def consoleFlow = rootElement.getChildren('flow',
                                                  core).find { element ->
            element.getAttribute('name').value == lookFor
        }
        assert consoleFlow: "Was looking for flow ${lookFor}"
        rootElement.removeContent(consoleFlow)
    }

    private static void allowDetailedValidationInfo(Element rootElement,
                                                    String apiBaseName) {
        def lookFor = "${apiBaseName}-main"
        def routerFlow = rootElement.getChildren('flow',
                                                 core).find { element ->
            element.getAttribute('name').value == lookFor
        }
        assert routerFlow: "Was looking for flow ${lookFor}"
        def errorHandler = routerFlow.getChild('error-handler',
                                               core)
        assert errorHandler
        def badRequestHandler = errorHandler.getChildren('on-error-propagate',
                                                         core).find { element ->
            element.getAttribute('type').value == 'APIKIT:BAD_REQUEST'
        }
        assert badRequestHandler
        def badRequestWeave = badRequestHandler.getChild('transform',
                                                         ee)
        assert badRequestWeave
        def message = badRequestWeave.getChild('message',
                                               ee)
        assert message
        def setPayload = message.getChild('set-payload',
                                          ee)
        assert setPayload
        def dwLines = [
                '%dw 2.0',
                'output application/json',
                '---',
                "if (p('return.validation.failures')) {error_details: error.description} else {message: \"Bad request\"}"
        ]
        setPayload.setContent(new CDATA(dwLines.join('\n')))
    }

    private static boolean removeHttpListenerConfigs(Element rootElement) {
        rootElement.removeChildren('listener-config',
                                   http)
    }

    private static void parameterizeApiKitConfig(Element flowNode) {
        def apiKitConfig = flowNode.getChild('config',
                                             apiKit)
        assert apiKitConfig
        // allow projects to control this via properties
        apiKitConfig.setAttribute('disableValidations',
                                  '${skip.apikit.validation}')
    }

    private static void modifyHttpListeners(Element flowNode,
                                            String apiName,
                                            String apiVersion,
                                            boolean insertApiNameInListenerPath,
                                            String httpListenerConfigName) {
        def listeners = flowNode.getChildren('flow',
                                             core)
                .collect { flow ->
                    flow.getChildren('listener',
                                     http)
                }.flatten()
        listeners.each { Element listener ->
            // supplied via properties to allow HTTP vs. HTTPS toggle at runtime
            def configRefAttribute = listener.getAttribute('config-ref')
            assert configRefAttribute
            configRefAttribute.value = httpListenerConfigName
            // want to be able to combine projects later, so be able to share a single listener config
            // by using paths
            def listenerPathAttribute = listener.getAttribute('path')
            assert listenerPathAttribute
            def isConsole = listenerPathAttribute.value.contains('console')
            def apiParts = []
            if (insertApiNameInListenerPath) {
                apiParts << apiName
            }
            apiParts << (isConsole ? 'console' : 'api')
            apiParts += [apiVersion, '*']
            listenerPathAttribute.value = '/' + apiParts.join('/')
        }
    }
}
