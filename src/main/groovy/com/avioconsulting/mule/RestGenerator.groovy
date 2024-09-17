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


/**
 * <description>
 * @param projectDirectory The project directory where we will be generating the Mule flows
 * @param ramlPath Path within src/main/resources/api to the RAML to be scaffolded  // THIS SHOULD GO AWAY NOW
 * @param apiName The API name to optionally be used in the HTTP listener path depending on the value of insertApiNameInListenerPath
 * @param apiVersion The API version to be used in the HTTP listener path unless httpListenerPath is provided
 * @param insertApiNameInListenerPath a boolean, true if apiName should be inserted in the HTTP listener path before the version
 * @param httpListenerBasePath A base path to be used in the HTTP listener path before the apiName or apiVersion
 * @param httpListenerPath The full HTTP listener path to be used
 * @param httpConfigName The name of the HTTP configuration to be referenced by the HTTP listeners // TODO: should this be optional?
 * @param insertXmlBeforeRouter a String representing the XML to be inserted before router // TODO: REMOVED
 * @param errorHandler a String representing the error handler // TODO: an XML representation of a new error-handler, remove
 * @param httpResponse a String representing the HTTP response // TODO: Was used to replace default http response, remove
 * @param httpErrorResponse a String representing the HTTP error response // TODO: Was used to replace default error response, remove
 */
    public void generateFromLocal(File projectDirectory,
                                  String apiName,
                                  String apiVersion,
                                  String httpListenerBasePath,
                                  String httpListenerPath,
                                  boolean insertApiNameInListenerPath,
                                  String ramlDirectory,
                                  String ramlFilename) {
        // Using local, we will determine RAML Path (assuming we need it)
        // ramlDirectory and ramlFilename are the required properties

        // Copy local raml into project
        // Create temp project
        // Do the work
        // Copy temp project files back to real project
    }

    public void generateFromDesignCenter(File projectDirectory,
                                         String apiName,
                                         String apiVersion,
                                         String httpListenerBasePath,
                                         String httpListenerPath,
                                         boolean insertApiNameInListenerPath,
                                         String ramlDcProject,
                                         String ramlDcBranch,
                                         String ramlFilename) {
        // Download raml from DC into project
        // Create temp project
        // Do the work
        // Copy temp project files back to real project
    }

    public void generateFromExchange(File projectDirectory,
                                     String apiName,
                                     String apiVersion,
                                     String httpListenerBasePath,
                                     String httpListenerPath,
                                     boolean insertApiNameInListenerPath,
                                     String ramlGroupId,
                                     String ramlArtifactId) {

        // Create temp project
        // Find and expand RAML dependencies into temp project
        // do the work
        // Copy only relevant temp project files back to real project
    }

    public File setupTempProject(File projectDirectory) {

        return File.createTempDir()
    }

    public void generate(File projectDirectory,
                         String apiName,
                         String apiVersion,
                         String httpListenerBasePath,
                         String httpListenerPath,
                         boolean insertApiNameInListenerPath) {

    }

    public void finalizeProject(File tempDirectory, File projectDirectory) {
        // copy all but src/main/resources/api
    }





    static generate(File tempDirectory,
                    File baseDirectory,
                    String ramlPath,
                    String apiName,
                    String apiVersion,
                    boolean useCloudHub,
                    boolean insertApiNameInListenerPath,
                    String httpListenerBasePath,
                    String mavenProjectName,
                    String httpListenerConfigName,
                    String insertXmlBeforeRouter,
                    String errorHandler,
                    String httpResponse,
                    String httpErrorResponse) {
        // Without runtime edition EE, we won't use weaves in the output
        def scaffolder = new MainAppScaffolder(new ScaffolderContext(RuntimeEdition.EE))

        // Setup directories and target XML config file
        def tmpMainDir = join(tempDirectory,
                'src',
                'main')
        tmpMainDir.mkdirs()
        def tmpAppDirectory = join(tmpMainDir,
                'mule')
        tmpAppDirectory.mkdirs()


        // Setup directories and target XML config file
        def mainDir = join(baseDirectory,
                'src',
                'main')
        mainDir.mkdirs()
        def appDirectory = join(mainDir,
                'mule')
        appDirectory.mkdirs()

        // Validate RAML Path
        def ramlFile = join(tmpMainDir,
                'resources',
                'api',
                ramlPath)
        assert ramlFile.exists()

        // Remove Existing main flow file if exists
        def baseName = FileUtils.basename(ramlPath,
                '.raml')
        def flowFileName = baseName + '.xml'
        def flowFile = join(tmpAppDirectory,
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

        // Generate the flows
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

        def flowPath = new File(appDirectory,
                flowFileName)
        assert flowPath.exists()

        if (useCloudHub) {
            adjustRamlBaseUri(ramlFile,
                    apiName,
                    mavenProjectName)
        }

        // Mule's generator will use the RAML filename by convention
        def apiBaseName = FilenameUtils.getBaseName(ramlPath)
        alterGeneratedFlow(flowPath,
                apiName,
                apiVersion,
                apiBaseName,
                insertApiNameInListenerPath,
                httpListenerBasePath,
                httpListenerConfigName,
                insertXmlBeforeRouter,
                errorHandler,
                httpResponse,
                httpErrorResponse)
    }

    // TODO: TO BE REMOVED
//    private static void adjustRamlBaseUri(File ramlFile,
//                                          String apiName,
//                                          String mavenProjectName) {
//        def ramlText = ramlFile.text
//        def baseUri = "https://${mavenProjectName}.cloudhub.io/${apiName}/{version}"
//        def fixedRaml = ramlText.replaceAll(/baseUri: .*/,
//                "baseUri: ${baseUri}")
//        ramlFile.write fixedRaml
//    }

    private static void alterGeneratedFlow(File flowPath,
                                           String apiName,
                                           String apiVersion,
                                           String apiBaseName,
                                           boolean insertApiNameInListenerPath,
                                           String httpListenerBasePath,
                                           String httpListenerConfigName,
                                           String insertXmlBeforeRouter,
                                           String errorHandler,
                                           String httpResponse,
                                           String httpErrorResponse) {
        def builder = new SAXBuilder()
        def document = builder.build(flowPath)
        def rootElement = document.rootElement
        def schemaLocation = rootElement.getAttribute('schemaLocation',
                xsi)
        // for some reason, the EE schema location is not being included automatically, even when the DWs
        // are generated code from the scaffolder
        // 9-27-22: Removing this after apikit version updates.  EE namespaces were being duplicated.
        // schemaLocation.value = schemaLocation.value + ee.URI + ' ' + 'http://www.mulesoft.org/schema/mule/ee/core/current/mule-ee.xsd'
        removeHttpListenerConfigs(rootElement)
        removeConsole(rootElement,
                apiBaseName)
        modifyHttpListeners(rootElement,
                apiName,
                apiVersion,
                insertApiNameInListenerPath,
                httpListenerBasePath,
                httpListenerConfigName,
                httpResponse,
                httpErrorResponse)
        parameterizeApiKitConfig(rootElement)
        def mainFlow = getMainFlow(rootElement,
                apiBaseName)
        if (insertXmlBeforeRouter) {
            doInsertXmlBeforeRouter(mainFlow,
                    insertXmlBeforeRouter)
        }
        if (errorHandler) {
            replaceErrorHandler(mainFlow,
                    errorHandler)
        }
        def outputter = new XMLOutputter(Format.prettyFormat)
        outputter.output(document,
                new FileWriter(flowPath))
    }

    private static Element getMainFlow(Element rootElement,
                                       String apiBaseName) {
        def lookFor = "${apiBaseName}-main"
        Element mainFlow = rootElement.getChildren('flow',
                core).find { element ->
            element.getAttribute('name').value == lookFor
        }
        assert mainFlow: "Was looking for flow ${lookFor}"
        return mainFlow
    }

    private static void doInsertXmlBeforeRouter(Element mainFlow,
                                                String insertXmlBeforeRouter) {
        def router = mainFlow.getChild('router',
                Namespace.getNamespace('http://www.mulesoft.org/schema/mule/mule-apikit'))
        assert router
        def routerIndex = mainFlow.indexOf(router)
        def builder = new SAXBuilder()
        def newXmlDocument = builder.build(new StringReader(insertXmlBeforeRouter))
        // we're "moving" the element from 1 doc to another so have to detach it
        def elementToInsert = newXmlDocument.detachRootElement()
        mainFlow.addContent(routerIndex,
                elementToInsert)
    }

    private static void replaceErrorHandler(Element mainFlow,
                                            String errorHandlerXml) {
        def existingErrorHandler = mainFlow.getChild('error-handler',
                core)
        assert existingErrorHandler
        mainFlow.removeContent(existingErrorHandler)
        def builder = new SAXBuilder()
        def newXmlDocument = builder.build(new StringReader(errorHandlerXml))
        // we're "moving" the element from 1 doc to another so have to detach it
        def elementToInsert = newXmlDocument.detachRootElement()
        mainFlow.addContent(elementToInsert)
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
                '${api.validation}')
    }

    private static void modifyHttpListeners(Element flowNode,
                                            String httpConfigName,
                                            boolean insertApiNameInListenerPath,
                                            String apiName,
                                            String apiVersion,
                                            String httpListenerBasePath,
                                            String httpListenerPath) {
        def listeners = flowNode.getChildren('flow',
                core)
                .collect { flow ->
                    flow.getChildren('listener',
                            http)
                }.flatten()
        listeners.each { Element listener ->

            // Replace config-ref with reference to correct HTTP Configuration
            def configRefAttribute = listener.getAttribute('config-ref')
            assert configRefAttribute
            configRefAttribute.value = httpConfigName

            // Use httpListenerPath if provided, otherwise build conditional path based on arguments
            def listenerPathAttribute = listener.getAttribute('path')
            assert listenerPathAttribute

            if(httpListenerPath) {
                listenerPathAttribute.value = httpListenerPath
            } else {
                def apiParts = []
                if (httpListenerBasePath) {
                    apiParts << httpListenerBasePath
                }

                if(insertApiNameInListenerPath) {
                    apiParts << apiName
                }

                apiParts += [apiVersion, '*']

                listenerPathAttribute.value = apiParts.join('/')
            }
//            if (httpResponse) {
//                replaceResponse(listener,
//                        'response',
//                        httpResponse)
//            }
//            if (httpErrorResponse) {
//                replaceResponse(listener,
//                        'error-response',
//                        httpErrorResponse)
//            }
        }
    }

    private static void replaceResponse(Element listener,
                                        String responseType,
                                        String newResponse) {
        def existingResponse = listener.getChild(responseType,
                http)
        listener.removeContent(existingResponse)
        def builder = new SAXBuilder()
        def newXmlDocument = builder.build(new StringReader(newResponse))
        // we're "moving" the element from 1 doc to another so have to detach it
        def elementToInsert = newXmlDocument.detachRootElement()
        listener.addContent(elementToInsert)
    }
}
