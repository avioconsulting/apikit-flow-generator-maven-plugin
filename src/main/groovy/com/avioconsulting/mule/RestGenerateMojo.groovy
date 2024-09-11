package com.avioconsulting.mule

import com.avioconsulting.mule.anypoint.api.credentials.model.ConnectedAppCredential
import com.avioconsulting.mule.anypoint.api.credentials.model.Credential
import com.avioconsulting.mule.anypoint.api.credentials.model.UsernamePasswordCredential
import com.avioconsulting.mule.designcenter.DesignCenterDeployer
import com.avioconsulting.mule.designcenter.HttpClientWrapper
import groovy.json.JsonSlurper
import org.apache.commons.io.FileUtils
import org.apache.commons.lang.StringEscapeUtils
import org.apache.maven.artifact.repository.ArtifactRepository
import org.apache.maven.model.Dependency
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Component
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import org.apache.maven.plugins.annotations.ResolutionScope
import java.util.zip.ZipFile
import java.nio.file.Files
import java.nio.file.Paths

@Mojo(name = 'generateFlowRest',
        requiresDependencyResolution = ResolutionScope.COMPILE)
class RestGenerateMojo extends AbstractMojo implements FileUtil {
    @Parameter(property = 'api.name')
    private String apiName

    @Parameter(property = 'api.current.version')
    private String apiCurrentVersion

    @Parameter(property = 'anypointUsername')
    private String anypointUsername

    @Parameter(property = 'anypointPassword')
    private String anypointPassword

    @Parameter(property = 'anypointConnectedAppId')
    private String anypointConnectedAppId

    @Parameter(property = 'anypointConnectedAppSecret')
    private String anypointConnectedAppSecret

    @Parameter(property = 'anypointOrganizationName')
    private String anypointOrganizationName

    @Parameter(property = 'ramlDcProject')
    private String ramlDcProject

    @Parameter(property = 'ramlDcBranch', defaultValue = 'master')
    private String ramlDcBranch

    @Parameter(property = 'ramlFilename')
    private String ramlFilename

    @Parameter(property = 'ramlDirectory')
    private File ramlDirectory

    @Parameter(property = 'ramlGroupId')
    private String ramlGroupId

    @Parameter(property = 'ramlArtifactId')
    private String ramlArtifactId

    @Parameter(property = 'apikitgen.insert.api.name.in.listener.path',
            defaultValue = 'true')
    private boolean insertApiNameInListenerPath

    @Parameter(property = 'http.listener.config.name')
    private String httpListenerConfigName

    @Parameter(property = 'http.listener.base.path')
    private String httpListenerBasePath

    @Parameter(property = 'temp.file.of.xml.to.insert.before.router')
    private File tempFileOfXmlToInsertBeforeRouter

    @Parameter(property = 'temp.file.of.error.handler.xml.to.replace.stock.with')
    private File tempFileErrorHandlerXml

    @Parameter(property = 'temp.file.of.xml.http.response')
    private File tempFileOfHttpResponseXml

    @Parameter(property = 'temp.file.of.xml.http.error.response')
    private File tempFileOfHttpErrorResponseXml

    @Parameter(defaultValue = '${localRepository}', readonly = true, required = true)
    private ArtifactRepository local

    @Component
    protected MavenProject mavenProject

    @Override
    void execute() throws MojoExecutionException, MojoFailureException {



        File projectDir = mavenProject.basedir
        File tmpProject = File.createTempDir()
        log.info "Created temp project: ${tmpProject.getAbsolutePath()}"


//        def userHome = new File(System.getProperty('user.home'))
//        def mvnHome = new File(userHome, '.m2')
//        def mvnRepo = new File(mvnHome, 'repository')


        def apiDirectory = join(projectDir,
                'src',
                'main',
                'resources',
                'api')
        apiDirectory.mkdirs()

        def tmpApiDirectory = join(tmpProject,
                'src',
                'main',
                'resources',
                'api')
        tmpApiDirectory.mkdirs()

        // Using Local RAML
        if (ramlDirectory) {
            log.info "Copying RAMLs from ${ramlDirectory}"
            assert ramlDirectory.exists()

            // Copy RAML into Project
            FileUtils.copyDirectory(ramlDirectory, apiDirectory)

            // Copy project to tmp directory for scaffolding
            FileUtils.copyDirectory(projectDir, tmpProject)
        // Using RAML from Exchange
        } else if (ramlGroupId && ramlArtifactId) {
            log.info "Using RAML artifact from exchange: ${ramlGroupId}:${ramlArtifactId}"

            // Copy project to tmp directory for scaffolding
            FileUtils.copyDirectory(projectDir, tmpProject)

            def mvnRepo = new File((new URL(local.url)).toURI())
            log.info "Using local m2 repository: " + mvnRepo.absolutePath
            def ramlVersion
            mavenProject.getDependencies().each { Dependency dep ->
                if(dep.groupId == ramlGroupId && dep.artifactId == ramlArtifactId) {
                    ramlVersion = dep.getVersion()
                }
            }
            if(ramlVersion == null) {
                throw new MojoFailureException("No RAML dependency found in pom.xml for ${ramlGroupId}:${ramlArtifactId}")
            }

            // Find and expand RAML dependencies into tmp project for scaffolding
            File raml = getArtifact(mvnRepo, ramlGroupId, ramlArtifactId, ramlVersion)
            expandArtifact(raml, tmpApiDirectory)
            ramlFilename = getMainRaml(tmpApiDirectory)
            processDeps(tmpApiDirectory, mvnRepo)
        } else {
            // Using RAML from a Design Center project
            Credential credential = new UsernamePasswordCredential(this.anypointUsername, this.anypointPassword)
            if (this.anypointConnectedAppId != null && this.anypointConnectedAppSecret != null) {
                credential = new ConnectedAppCredential(this.anypointConnectedAppId, this.anypointConnectedAppSecret)
            }
            log.info 'Will fetch RAML contents from Design Center first'
            def clientWrapper = new HttpClientWrapper('https://anypoint.mulesoft.com',
                    credential,
                    this.log,
                    this.anypointOrganizationName)
            def designCenter = new DesignCenterDeployer(clientWrapper,
                    log)
            def existingRamlFiles = designCenter.getExistingDesignCenterFilesByProjectName(ramlDcProject,
                    ramlDcBranch)
            log.info 'Fetched RAML files OK, now writing to disk'
            // need our directories first
            def folders = existingRamlFiles.findAll { f -> f.type == 'FOLDER' }
            folders.each { folder ->
                new File(apiDirectory,
                        folder.fileName).mkdirs()
            }
            def noDirs = existingRamlFiles - folders
            noDirs.each { f ->
                log.info "Writing file ${f.fileName}..."
                new File(apiDirectory,
                        f.fileName).text = f.contents
            }
            FileUtils.copyDirectory(projectDir, tmpProject)
        }

        // Use first RAML file in the root directory as the main one if a specific one is not provided
        if (!ramlFilename || ramlFilename == 'NotUsed') {
            def topLevelFiles = new FileNameFinder().getFileNames(apiDirectory.absolutePath,
                    '*.raml')
            // we don't want the full path
            ramlFilename = new File(topLevelFiles[0]).name
            log.info "Assuming ${ramlFilename} is the top level RAML file"
        }

        // Set default http listener config name if not provided.
        // Maven will try and resolve the property if it is set on the annotation as default value
        if (!httpListenerConfigName) {
            log.info 'No http listener config specified, using default, parameterized value of ${http.listener.config}'
            httpListenerConfigName = '${http.listener.config}'
        }

        // Unescape listener base path to support passing property references as part of the path ${}
        httpListenerBasePath = StringEscapeUtils.unescapeJava(httpListenerBasePath)

        RestGenerator.generate(tmpProject,
                mavenProject.basedir,
                ramlFilename,
                apiName,
                apiCurrentVersion,
                false,
                insertApiNameInListenerPath,
                httpListenerBasePath,
                mavenProject.artifactId,
                httpListenerConfigName,
                this.tempFileOfXmlToInsertBeforeRouter?.text,
                this.tempFileErrorHandlerXml?.text,
                this.tempFileOfHttpResponseXml?.text,
                this.tempFileOfHttpErrorResponseXml?.text)
        replaceNewlinesRecursively(mavenProject.basedir.toURI())
    }


    def replaceNewlinesRecursively(path) {
        Files.walk(Paths.get(path)).forEach { filePath ->
            if (Files.isRegularFile(filePath)) {
                def content = new String(Files.readAllBytes(filePath))
                content = content.replaceAll("\r\n", "\n")
                Files.write(filePath, content.getBytes())
            }
        }
    }


    // New code

    static def getMainRaml(File ramlDirectory) {
        def js = new JsonSlurper()
        return js.parse(new File(ramlDirectory, 'exchange.json')).main
    }

    static def processDeps(File ramlDirectory, File mvnRepo) {
        // Get Exchange Info
        def exchangeInfo = getExchangeInfo(ramlDirectory)

        // If Has deps
        if(exchangeInfo.dependencies.size() > 0) {
            def modulesDir = new File(ramlDirectory, 'exchange_modules')
            modulesDir.mkdirs()
            exchangeInfo.dependencies.each { dep ->
                def artifact = getArtifact(mvnRepo, dep.groupId, dep.assetId, dep.version)
                def targetDir = new File(modulesDir, dep.groupId + File.separator + dep.assetId + File.separator + dep.version)
                targetDir.mkdirs()
                expandArtifact(artifact, targetDir)
            }
        }
    }


    static def getExchangeInfo(File ramlDirectory) {
        def js = new JsonSlurper()
        return js.parse(new File(ramlDirectory, 'exchange.json'))
    }

    static File getArtifact(File mvnRepo, String groupId, String artifactId, String version) {

        println "mvnRepo: " + mvnRepo.absolutePath
        println "groupId: " + groupId
        println "artifactId: " + artifactId
        println "version: " + version
        File artifactDir = new File(mvnRepo, groupId + File.separator + artifactId + File.separator + version)
        println artifactDir.absolutePath

        if (artifactDir.exists() && artifactDir.isDirectory()) {
            File artifactFile  = artifactDir.listFiles().find {
                it.name.endsWith('.zip')
            }
            return artifactFile
        } else {
            return null
        }
    }

    static def expandArtifact(File artifact, File target) {
        ZipFile zipFile = new ZipFile(artifact)

        zipFile.entries().findAll { it.directory }.each {
            println "Creating Dir " + it.name + "..."
            new File(target, it.name).mkdirs()
        }

        zipFile.entries().findAll { !it.directory }.each {
            println "Writing File " + it.name + "..."
            new File(target, it.name).text = zipFile.getInputStream(it).text
        }
    }
}
