package com.avioconsulting.mule

import com.avioconsulting.mule.anypoint.api.credentials.model.ConnectedAppCredential
import com.avioconsulting.mule.anypoint.api.credentials.model.Credential
import com.avioconsulting.mule.anypoint.api.credentials.model.UsernamePasswordCredential
import com.avioconsulting.mule.designcenter.DesignCenterDeployer
import com.avioconsulting.mule.designcenter.HttpClientWrapper
import groovy.json.JsonSlurper
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
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

import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.ZipFile;

class RestGenerator implements FileUtil {
    public static final Namespace core = Namespace.getNamespace('http://www.mulesoft.org/schema/mule/core')
    public static final Namespace http = Namespace.getNamespace('http', 'http://www.mulesoft.org/schema/mule/http')
    public static final Namespace apiKit = Namespace.getNamespace('apikit', 'http://www.mulesoft.org/schema/mule/mule-apikit')
    public static final Namespace xsi = Namespace.getNamespace('xsi', 'http://www.w3.org/2001/XMLSchema-instance')
    public static final Namespace doc = Namespace.getNamespace('doc', 'http://www.mulesoft.org/schema/mule/documentation')
    public static final Namespace ee = Namespace.getNamespace('ee', 'http://www.mulesoft.org/schema/mule/ee/core')
    public static final Namespace apiGateway = Namespace.getNamespace('api-gateway', 'http://www.mulesoft.org/schema/mule/api-gateway')

    private Log log;
    private String apiName, apiVersion, httpConfigName, httpListenerBasePath, httpListenerPath
    private boolean insertApiNameInListenerPath

    RestGenerator(Log log, String apiName, String apiVersion, String httpConfigName, String httpListenerBasePath, String httpListenerPath, boolean insertApiNameInListenerPath) {
        this.log = log
        this.apiName = apiName
        this.apiVersion = apiVersion
        this.httpConfigName = httpConfigName
        this.httpListenerBasePath = httpListenerBasePath
        this.httpListenerPath = httpListenerPath
        this.insertApiNameInListenerPath = insertApiNameInListenerPath
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
 * @param httpConfigName The name of the HTTP configuration to be referenced by the HTTP listeners
 */
    void generateFromLocal(File projectDirectory,
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
        ramlFilename = getDefaultRamlFilename(ramlFilename, apiDirectory)
        generate(tmpDirectory, ramlFilename)

        // Copy temp project files back to real project
        this.finalizeProject(tmpDirectory, projectDirectory)
    }

    void generateFromDesignCenterWithPassword(File projectDirectory,
                                                     String ramlDcProject,
                                                     String ramlDcBranch,
                                                     String ramlFilename,
                                                     String anypointOrganizationName,
                                                     String anypointUserName,
                                                     String anypointPassword) {
        generateFromDesignCenter(projectDirectory, ramlDcProject, ramlDcBranch, ramlFilename, anypointOrganizationName,
                new UsernamePasswordCredential(anypointUserName, anypointPassword))
    }

    void generateFromDesignCenter(File projectDirectory,
                                         String ramlDcProject,
                                         String ramlDcBranch,
                                         String ramlFilename,
                                         String anypointOrganizationName,
                                         String anypointConnectedAppId,
                                         String anypointConnectedAppSecret) {
        generateFromDesignCenter(projectDirectory, ramlDcProject, ramlDcBranch, ramlFilename, anypointOrganizationName,
                new ConnectedAppCredential(anypointConnectedAppId, anypointConnectedAppSecret))
    }

    void generateFromDesignCenter(File projectDirectory,
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

        ramlFilename = getDefaultRamlFilename(ramlFilename, apiDirectory)
        generate(tmpDirectory, ramlFilename)

        // Copy temp project files back to real project
        this.finalizeProject(tmpDirectory, projectDirectory)
    }

    void generateFromExchange(File projectDirectory,
                                     String ramlGroupId,
                                     String ramlArtifactId,
                                     String ramlVersion,
                                     File localRepository) {

        // Create temp project
        File tmpDirectory = this.setupTempProject(projectDirectory)
        File tmpApiDirectory = join(tmpDirectory,
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

        // do the work
        String ramlFilename = getMainRaml(tmpApiDirectory)
        this.generate(tmpDirectory, ramlFilename)

        // Update APIkit router w/ exchange URL to RAML
        def apiBaseName = FilenameUtils.getBaseName(ramlFilename)
        File mainConfigFile = join(tmpDirectory,
                                    'src',
                                    'main',
                                    'mule',
                                    apiBaseName + '.xml')
        def apiReferenceString = 'resource::' + ramlGroupId + ':' + ramlArtifactId + ':' + ramlVersion + ':raml:zip:' + ramlFilename
        log.info "Updating apikit api reference to " + apiReferenceString
        mainConfigFile.text = updateApikitConfig(mainConfigFile, apiReferenceString)

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

        log.info "Created temp project: ${tmpDirectory.getAbsolutePath()}"
        return tmpDirectory
    }

    public void finalizeProject(File tempDirectory, File projectDirectory) {
        File tmpApiDirectory = join(tempDirectory, 'src', 'main', 'resources', 'api')
        log.info "Deleting API resources from tmp project before copying back to main project directory: ${tmpApiDirectory.absolutePath}"
        FileUtils.deleteDirectory(tmpApiDirectory)
        log.info "Copying tmp project back to main project directory"
        FileUtils.copyDirectory(tempDirectory, projectDirectory)
        log.info "Replacing windows newlines with unix newlines in project directory"
        replaceNewlinesRecursively(projectDirectory)
    }

    public void generate(File projectDirectory, String ramlFilename) {

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
        def content = alterMainConfig(mainMuleConfig, apiBaseName)
        mainMuleConfig.text = content

        def globalConfig = join(appDirectory, 'global', 'global-config.xml')
        if ( globalConfig.exists()) {
            globalConfig.text = alterGlobalConfig(globalConfig, apiBaseName)
        }

    }

    /**
     * Intended to make updates to the global-config.xml file
     * Changes include:
     *   Update api-gateway autodiscovery flowRef to apiBaseName + '-main'
     * @param flowPath        File object for the global config configuration file
     * @param apiBaseName     API base name
     * @return
     */
    public String alterGlobalConfig(File flowPath, String apiBaseName){
        def builder = new SAXBuilder()
        def document = builder.build(flowPath)
        def rootElement = document.rootElement

        modifyApiAutodiscovery(rootElement, apiBaseName)
        
        // Format XML
        // Create custom Format - This at least keeps the new line spaces...
        def format = Format.getPrettyFormat()
        format.setIndent("  ")  // Set general indentation
        format.setLineSeparator('\n')
        format.setTextMode(Format.TextMode.TRIM)

        def writer = new StringWriter()
        def output = new XMLOutputter(format) //Format.prettyFormat)
        output.output(document, writer)
        return writer.toString()
    }

    /**
     * Intended to alter the main configuration file to meet AVIO standards
     * This includes:
     *   Remove generated listener config (this is expected to already exist in global-config.xml)
     *   Remove console
     *   Update http:listener - Base path and http config reference
     *   Add an api.validation parameter to apikit:config
     *   Replace standard error handler with reference to 'global-error-handler'
     * @param flowPath        File object for the main configuration file
     * @param apiBaseName     API base name
     * @return
     */
    public String alterMainConfig(File flowPath, String apiBaseName) {
        def builder = new SAXBuilder()
        def document = builder.build(flowPath)
        def rootElement = document.rootElement

        removeHttpListenerConfigs(rootElement)
        removeConsole(rootElement, apiBaseName)
        modifyHttpListeners(rootElement)

        // Adds api.validation parameter
        parameterizeApiKitConfig(rootElement)

        // Remove standard error handler, add reference to global-error-handler
        def mainFlow = getMainFlow(rootElement, apiBaseName)
        removeErrorHandler(mainFlow)
        addDefaultErrorHandler(mainFlow)

        // Format XML
        def writer = new StringWriter()
        def output = new XMLOutputter(Format.prettyFormat)
        output.output(document, writer)
        return writer.toString()
    }

    private Element getMainFlow(Element rootElement, String apiBaseName) {
        def lookFor = "${apiBaseName}-main"
        Element mainFlow = rootElement.getChildren('flow',
                core).find { element ->
            element.getAttribute('name').value == lookFor
        }
        assert mainFlow: "Was looking for flow ${lookFor}"
        return mainFlow
    }

    private void removeConsole(Element rootElement, String apiBaseName) {
        def lookFor = "${apiBaseName}-console"
        def consoleFlow = rootElement.getChildren('flow',
                core).find { element ->
            element.getAttribute('name').value == lookFor
        }
        assert consoleFlow: "Was looking for flow ${lookFor}"
        rootElement.removeContent(consoleFlow)
    }

    private void removeErrorHandler(Element mainFlow) {
        def errorHandler = mainFlow.getChild('error-handler', core)
        mainFlow.removeContent(errorHandler)
    }

    private void addDefaultErrorHandler(Element mainFlow) {
        // adds '<error-handler ref="global-error-handler"/>' to main flow
        def defaultErrorHandler = new Element('error-handler', core)
        defaultErrorHandler.setAttribute('ref', 'global-error-handler')
        mainFlow.addContent(defaultErrorHandler)
    }

    private void modifyApiAutodiscovery(Element rootElement, String apiBaseName) {
        rootElement.getChild('autodiscovery', apiGateway).setAttribute('flowRef', apiBaseName + '-main')
    }

    private boolean removeHttpListenerConfigs(Element rootElement) {
        rootElement.removeChildren('listener-config', http)
    }

    private void parameterizeApiKitConfig(Element flowNode) {
        def apiKitConfig = flowNode.getChild('config', apiKit)
        assert apiKitConfig
        // allow projects to control this via properties
        apiKitConfig.setAttribute('disableValidations', '${api.validation}')
    }

    private void modifyHttpListeners(Element flowNode) {
        def listeners = flowNode.getChildren('flow', core).collect { flow ->
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

                listenerPathAttribute.value = '/' + apiParts.join('/')
            }
        }
    }

    String updateApikitConfig(File flowPath, String ramlRef) {
        def builder = new SAXBuilder()
        def document = builder.build(flowPath)
        def rootElement = document.rootElement

        // this assumes just a single apikit:config, and updates the first
        rootElement.getChild('config', apiKit).setAttribute('api', ramlRef)

        // Format XML
        def writer = new StringWriter()
        def output = new XMLOutputter(Format.prettyFormat)
        output.output(document, writer)
        return writer.toString()
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

    String getDefaultRamlFilename(String ramlFilename, File apiDirectory) {
        // Use first RAML file in the root directory as the main one if a specific one is not provided
        if (!ramlFilename) {
            def topLevelFiles = new FileNameFinder().getFileNames(apiDirectory.absolutePath, '*.raml')
            ramlFilename = new File(topLevelFiles[0]).name
            log.info "Assuming ${ramlFilename} is the top level RAML file"
        }

        return ramlFilename
    }

    void replaceNewlinesRecursively(File path) {
        Files.walk(Paths.get(path.toURI())).forEach { filePath ->
            if(!Files.isDirectory(filePath) && isText(filePath)){
                def content = new String(Files.readAllBytes(filePath))
                content = content.replaceAll("\r\n", "\n")
                Files.write(filePath, content.getBytes())
            }
        }
    }
}
