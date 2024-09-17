package com.avioconsulting.mule

import com.avioconsulting.mule.anypoint.api.credentials.model.ConnectedAppCredential
import com.avioconsulting.mule.anypoint.api.credentials.model.Credential
import com.avioconsulting.mule.anypoint.api.credentials.model.UsernamePasswordCredential
import com.avioconsulting.mule.designcenter.DesignCenterDeployer
import com.avioconsulting.mule.designcenter.HttpClientWrapper
import groovy.json.JsonSlurper
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
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
import org.apache.maven.plugin.logging.Log

import java.util.zip.ZipFile;

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

    private Log log;


    RestGenerator(Log log) {
        this.log = log
    }

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
    void generateFromLocal(File projectDirectory,
                                  String apiName,
                                  String apiVersion,
                                  String httpListenerBasePath,
                                  String httpListenerPath,
                                  boolean insertApiNameInListenerPath,
                                  File ramlDirectory,
                                  String ramlFilename) {
        // Copy local raml into project
        def apiDirectory = join(projectDirectory,
                'src',
                'main',
                'resources',
                'api')
        apiDirectory.mkdirs()

        // Copy RAML into Project
        FileUtils.copyDirectory(ramlDirectory, apiDirectory)

        // Create temp project
        File tmpDirectory = this.setupTempProject(projectDirectory)

        // Do the work

        // TODO : WORK

        // Copy temp project files back to real project
        this.finalizeProject(tmpDirectory, projectDirectory)
    }

    void generateFromDesignCenterWithPassword(File projectDirectory,
                                                     String apiName,
                                                     String apiVersion,
                                                     String httpListenerBasePath,
                                                     String httpListenerPath,
                                                     boolean insertApiNameInListenerPath,
                                                     String ramlDcProject,
                                                     String ramlDcBranch,
                                                     String ramlFilename,
                                                     String anypointOrganizationName,
                                                     String anypointUserName,
                                                     String anypointPassword) {
        generateFromDesignCenter(projectDirectory, apiName, apiVersion, httpListenerBasePath, httpListenerPath,
                insertApiNameInListenerPath, ramlDcProject, ramlDcBranch, ramlFilename, anypointOrganizationName,
                new UsernamePasswordCredential(anypointUserName, anypointPassword))
    }

    void generateFromDesignCenter(File projectDirectory,
                                         String apiName,
                                         String apiVersion,
                                         String httpListenerBasePath,
                                         String httpListenerPath,
                                         boolean insertApiNameInListenerPath,
                                         String ramlDcProject,
                                         String ramlDcBranch,
                                         String ramlFilename,
                                         String anypointOrganizationName,
                                         String anypointConnectedAppId,
                                         String anypointConnectedAppSecret) {
        generateFromDesignCenter(projectDirectory, apiName, apiVersion, httpListenerBasePath, httpListenerPath,
                insertApiNameInListenerPath, ramlDcProject, ramlDcBranch, ramlFilename, anypointOrganizationName,
                new ConnectedAppCredential(anypointConnectedAppId, anypointConnectedAppSecret))
    }

    void generateFromDesignCenter(File projectDirectory,
                                         String apiName,
                                         String apiVersion,
                                         String httpListenerBasePath,
                                         String httpListenerPath,
                                         boolean insertApiNameInListenerPath,
                                         String ramlDcProject,
                                         String ramlDcBranch,
                                         String ramlFilename,
                                         String anypointOrganizationName,
                                         Credential credential) {
        // Download raml from DC into project
        def apiDirectory = join(projectDirectory,
                'src',
                'main',
                'resources',
                'api')
        apiDirectory.mkdirs()

        log.info 'Will fetch RAML contents from Design Center first'
        def clientWrapper = new HttpClientWrapper('https://anypoint.mulesoft.com',
                credential,
                this.log,
                anypointOrganizationName)
        def dcClient = new DesignCenterDeployer(clientWrapper, log)
        def dcProjectFiles = dcClient.getExistingDesignCenterFilesByProjectName(ramlDcProject, ramlDcBranch)

        log.info 'Fetched RAML files OK, now writing to disk'

        // Find directories to create locally
        def directories = dcProjectFiles.findAll { f -> f.type == 'FOLDER' }
        directories.each { folder ->
            new File(apiDirectory, folder.fileName).mkdirs()
        }

        // Find all files (non directories) to write
        def notDirectories = dcProjectFiles - directories
        notDirectories.each { f ->
            log.info "Writing file ${f.fileName}..."
            new File(apiDirectory, f.fileName).text = f.contents
        }

        // Create temp project
        File tmpDirectory = this.setupTempProject(projectDirectory)
        // Do the work

        // TODO: WORK

        // Copy temp project files back to real project
        this.finalizeProject(tmpDirectory, projectDirectory)
    }

    void generateFromExchange(File projectDirectory,
                                     String apiName,
                                     String apiVersion,
                                     String httpListenerBasePath,
                                     String httpListenerPath,
                                     boolean insertApiNameInListenerPath,
                                     String ramlGroupId,
                                     String ramlArtifactId,
                                     String ramlVersion,
                                     File localRepository) {

        // Create temp project
        File tmpDirectory = this.setupTempProject(projectDirectory)
        def tmpApiDirectory = join(tmpDirectory,
                'src',
                'main',
                'resources',
                'api')
        tmpApiDirectory.mkdirs()

        // Find and expand RAML dependencies into temp project
        log.info "Using local repository: " + localRepository
        File raml = getArtifact(localRepository, ramlGroupId, ramlArtifactId, ramlVersion)
        expandArtifact(raml, tmpApiDirectory)
        processDeps(tmpApiDirectory, localRepository)

        String ramlFilename = getMainRaml(tmpApiDirectory)
        // do the work
        this.generate(tmpDirectory, apiName, apiVersion, ramlFilename, httpListenerBasePath, httpListenerPath, insertApiNameInListenerPath)
        // TODO: Update APIkit router w/ exchange URL to RAML
        // def referenceString = 'resource::' + ramlGroupId + ':' + ramlArtifactId + ':' + ramlVersion + ':raml:zip:' + artifactId + '.raml'

        // Copy only relevant temp project files back to real project
        this.finalizeProject(tmpDirectory, projectDirectory)
    }

    public File setupTempProject(File projectDirectory) {
        File tmpDirectory = File.createTempDir()
        tmpDirectory.deleteOnExit()
        File tmpApiDirectory = join(tmpDirectory,
                'src',
                'main',
                'resources',
                'api')
        tmpApiDirectory.mkdirs()
        FileUtils.copyDirectory(projectDirectory, tmpDirectory)

        // TODO: Need Logger
//        log.info "Created temp project: ${tmpProject.getAbsolutePath()}"
        return tmpDirectory
    }

    public void finalizeProject(File tempDirectory, File projectDirectory) {
        // copy all but src/main/resources/api
    }

    public void generate(File projectDirectory,
                         String apiName,
                         String apiVersion,
                         String ramlFilename,
                         String httpListenerBasePath,
                         String httpListenerPath,
                         boolean insertApiNameInListenerPath) {

        // Without runtime edition EE, we won't use weaves in the output
        def scaffolder = new MainAppScaffolder(new ScaffolderContext(RuntimeEdition.EE))

        // Generate the flows
        def ramlFile = join(projectDirectory,
                'src',
                'main',
                'resources',
                'api',
                ramlFilename)
        assert ramlFile.exists()

        // Scaffold the RAML
        def parseResult = new RamlParsingStrategy().parse(ApiReference.create(ramlFile.absolutePath))
        assert parseResult.errors == []
        def result = scaffolder.run(ScaffoldingConfiguration.builder()
                .withApi(parseResult.get())
                .withMuleConfigurations([])
                .build())
        assert result.errors == []
        assert result.generatedConfigs.size() > 0

        // Write out configuration file(s) in src/main/mule
        def appDirectory = join(projectDirectory,
                'src',
                'main',
                'mule')
        appDirectory.mkdirs()

        result.generatedConfigs.each { config ->
            new File(appDirectory, config.name).text = config.content.text
        }

        // Mule's generator will use the RAML filename by convention
        def apiBaseName = FilenameUtils.getBaseName(ramlFilename)

        def mainMuleConfig = new File(appDirectory, apiBaseName + '.xml')
        assert mainMuleConfig.exists()

        // Make updates to the generated base flow
        alterGeneratedFlow(mainMuleConfig,
                apiName,
                apiVersion,
                insertApiNameInListenerPath,
                httpListenerBasePath,
                httpListenerPath)

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

    // TODO: KK - new implementation - intelliJ wants this to be static...
    // TODO: should we return the modified Flow content instead of writing it? - this would make testing easier
    // Remove listener config (this is located in global-config)
    // Remove Console
    // Update http:listener
    // Add api.validation parameter to apikit:config
    // Write new flow content (XML Pretty)
    public void alterGeneratedFlow(File flowPath,
                                   String apiName,
                                   String apiVersion,
                                   boolean insertApiNameInListenerPath,
                                   String httpListenerBasePath,
                                   String httpListenerPath) {
        def builder = new SAXBuilder()
        def document = builder.build(flowPath)
        def rootElement = document.rootElement

        removeHttpListenerConfigs(rootElement)
        removeConsole(rootElement, apiName)

        modifyHttpListeners(rootElement,
                'cloudhub-https-listener', // TODO: httpConfigName - Did we decide if we are going to allow to change this?
                insertApiNameInListenerPath,
                apiName,
                apiVersion,
                httpListenerBasePath,
                httpListenerPath)

        // Adds api.validation parameter
        parameterizeApiKitConfig(rootElement)

        // Format XML
        // TODO: return the content, move this code to 'generate'
        def outputter = new XMLOutputter(Format.prettyFormat)
        outputter.output(document, new FileWriter(flowPath))
    }

    // TODO: Remove when done...
    private static void alterGeneratedFlow_old(File flowPath,
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

    // TODO: Remove, unless we have requirements for this
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

    // TODO: Remove, unless we have requirements for this
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
        // TODO: why is this called here?!
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

    // TODO: This was being called from removeConsole - shouldn't this logic just be in the global-config?
    // Gets the error-handler from the main flow,
    // looks for an on-error-propagate with type BAD_REQUEST
    // gets the transform element,
    // writes a new DWL
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

            if (httpListenerPath) {
                listenerPathAttribute.value = httpListenerPath
            } else {
                def apiParts = []
                if (httpListenerBasePath) {
                    apiParts << httpListenerBasePath
                }

                if (insertApiNameInListenerPath) {
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

    // TODO: Remove, unless we have requirements for this
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

    File getArtifact(File repo, String groupId, String artifactId, String version) {
        File artifactDir = new File(repo, groupId + File.separator + artifactId + File.separator + version)

        if (artifactDir.exists() && artifactDir.isDirectory()) {
            File artifactFile = artifactDir.listFiles().find {
                it.name.endsWith('.zip')
            }
            return artifactFile
        } else {
            return null
        }
    }

    void expandArtifact(File artifact, File target) {
        ZipFile zipFile = new ZipFile(artifact)

        zipFile.entries().findAll { it.directory }.each {
            log.debug "Creating Dir " + it.name
            new File(target, it.name).mkdirs()
        }

        zipFile.entries().findAll { !it.directory }.each {
            log.debug "Writing File " + it.name + "..."
            new File(target, it.name).text = zipFile.getInputStream(it).text
        }
    }

    def getExchangeInfo(File ramlDirectory) {
        def js = new JsonSlurper()
        return js.parse(new File(ramlDirectory, 'exchange.json'))
    }

    def getMainRaml(File ramlDirectory) {
        getExchangeInfo(ramlDirectory).main
    }

    void processDeps(File ramlDirectory, File localRepository) {
        // Get Exchange Info
        def exchangeInfo = getExchangeInfo(ramlDirectory)

        // If Has deps
        if (exchangeInfo.dependencies.size() > 0) {
            def modulesDir = new File(ramlDirectory, 'exchange_modules')
            modulesDir.mkdirs()
            exchangeInfo.dependencies.each { dep ->
                def artifact = getArtifact(localRepository, dep.groupId, dep.assetId, dep.version)
                def targetDir = new File(modulesDir, dep.groupId + File.separator + dep.assetId + File.separator + dep.version)
                targetDir.mkdirs()
                expandArtifact(artifact, targetDir)
            }
        }
    }
}
