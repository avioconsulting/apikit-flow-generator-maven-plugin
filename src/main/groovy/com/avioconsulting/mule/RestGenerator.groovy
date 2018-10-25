package com.avioconsulting.mule

import org.apache.commons.io.FilenameUtils
import org.codehaus.plexus.util.FileUtils
import org.jdom2.CDATA
import org.jdom2.Element
import org.jdom2.Namespace
import org.jdom2.input.SAXBuilder
import org.jdom2.output.Format
import org.jdom2.output.XMLOutputter
import org.mule.tools.apikit.ScaffolderAPI

import static com.avioconsulting.mule.MuleDeployPropsCleaner.cleanProps

class RestGenerator implements FileUtil {
    public static final Namespace core = Namespace.getNamespace('http://www.mulesoft.org/schema/mule/core')
    public static final Namespace http = Namespace.getNamespace('http',
                                                                'http://www.mulesoft.org/schema/mule/http')
    public static final Namespace apiKit = Namespace.getNamespace('apikit',
                                                                  'http://www.mulesoft.org/schema/mule/apikit')
    public static final Namespace xsi = Namespace.getNamespace('xsi',
                                                               'http://www.w3.org/2001/XMLSchema-instance')
    public static final Namespace scripting = Namespace.getNamespace('scripting',
                                                                     'http://www.mulesoft.org/schema/mule/scripting')

    public static final Namespace doc = Namespace.getNamespace('doc',
                                                               'http://www.mulesoft.org/schema/mule/documentation')

    public static final Namespace json = Namespace.getNamespace('json',
                                                                'http://www.mulesoft.org/schema/mule/json')

    static generate(File baseDirectory,
                    String ramlPath,
                    String apiName,
                    String apiVersion,
                    boolean useCloudHub,
                    boolean insertApiNameInListenerPath,
                    String mavenProjectName) {
        def apiBuilder = new ScaffolderAPI()
        def mainDir = join(baseDirectory,
                           'src',
                           'main')
        def ramlFile = join(mainDir,
                            'api',
                            ramlPath)
        assert ramlFile.exists()
        def appDirectory = join(mainDir,
                                'app')
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
        apiBuilder.run([ramlFile],
                       appDirectory)
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
                           insertApiNameInListenerPath)
        def muleDeployProps = new Properties()
        def muleDeployPropsFile = new File(appDirectory,
                                           'mule-deploy.properties')
        muleDeployProps.load(new FileInputStream(muleDeployPropsFile))
        muleDeployProps.store(new FileOutputStream(muleDeployPropsFile),
                              'Updated by apikit flow generator plugin')
        cleanProps(muleDeployPropsFile)
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
                                           boolean insertApiNameInListenerPath) {
        def builder = new SAXBuilder()
        def document = builder.build(flowPath)
        def rootElement = document.rootElement
        removeHttpListenerConfigs(rootElement)
        modifyHttpListeners(rootElement,
                            apiName,
                            apiVersion,
                            insertApiNameInListenerPath)
        parameterizeApiKitConfig(rootElement)
        addChoiceRouting(rootElement,
                         apiBaseName)
        def outputter = new XMLOutputter(Format.prettyFormat)
        outputter.output(document,
                         new FileWriter(flowPath))
    }

    private static void addChoiceRouting(Element rootElement,
                                         String apiBaseName) {
        def schemaLocation = rootElement.getAttribute('schemaLocation',
                                                      xsi)
        def existingSchemaLocations = schemaLocation.value.split(' ')
        existingSchemaLocations += [
                'http://www.mulesoft.org/schema/mule/json',
                'http://www.mulesoft.org/schema/mule/json/current/mule-json.xsd',
                'http://www.mulesoft.org/schema/mule/scripting',
                'http://www.mulesoft.org/schema/mule/scripting/current/mule-scripting.xsd'
        ]
        schemaLocation.value = existingSchemaLocations.join(' ')
        allowDetailedValidationInfo(rootElement)
        def lookFor = "${apiBaseName}-console"
        def consoleFlow = rootElement.getChildren('flow',
                                                  core).find { element ->
            element.getAttribute('name').value == lookFor
        }
        assert consoleFlow: "Was looking for flow ${lookFor}"
        def consoleElement = consoleFlow.getChild('console',
                                                  apiKit)
        consoleFlow.removeContent(consoleElement)
        setupChoice(consoleFlow,
                    '${enable.apikit.console}') { Element when, Element otherwise ->
            when.addContent(consoleElement)
            def payload = new Element('set-payload',
                                      core)
            otherwise.addContent(payload)
            payload.setAttribute('value',
                                 'Resource not found')
            payload.setAttribute('name',
                                 'Error message to caller',
                                 doc)
        }
    }

    private static void allowDetailedValidationInfo(Element rootElement) {
        def mappingStrategy = rootElement.getChild('mapping-exception-strategy',
                                                   apiKit)
        assert mappingStrategy
        def badRequestMapping = mappingStrategy.getChildren('mapping',
                                                            apiKit).find { node ->
            node.getAttribute('statusCode').value == '400'
        }
        assert badRequestMapping
        def badRequestPayload = badRequestMapping.getChild('set-payload',
                                                           core)
        badRequestMapping.removeContent(badRequestPayload)
        setupChoice(badRequestMapping,
                    '${return.validation.failures}') { Element when, Element otherwise ->
            def scriptingTransformer = new Element('transformer',
                                                   scripting)
            when.addContent(scriptingTransformer)
            scriptingTransformer.setAttribute('name',
                                              'Error Message Map',
                                              doc)
            def script = new Element('script',
                                     scripting)
            scriptingTransformer.addContent(script)
            script.setAttribute('engine',
                                'Groovy')
            script.addContent(new CDATA('[error_details: exception.message]'))
            def json = new Element('object-to-json-transformer',
                                   json)
            when.addContent(json)
            json.setAttribute('name',
                              'Map to JSON',
                              doc)

            otherwise.addContent(badRequestPayload)
            badRequestPayload.setAttribute('name',
                                           'Obfuscate error',
                                           doc)
        }
    }

    static def setupChoice(Element root,
                           String expression,
                           Closure closure) {
        def choiceElement = new Element('choice',
                                        core)
        root.addContent(choiceElement)
        def whenElement = new Element('when',
                                      core)
        choiceElement.addContent(whenElement)
        whenElement.setAttribute('expression',
                                 expression)
        def otherwise = new Element('otherwise',
                                    core)
        choiceElement.addContent(otherwise)
        closure(whenElement,
                otherwise)
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
                                            boolean insertApiNameInListenerPath) {
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
            configRefAttribute.value = '${http.listener.config}'
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
