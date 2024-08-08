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
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Component
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import org.apache.maven.plugins.annotations.ResolutionScope
import java.util.zip.ZipFile

@Mojo(name = 'generateFlowRest',
        requiresDependencyResolution = ResolutionScope.COMPILE)
class RestGenerateMojo extends AbstractMojo implements FileUtil {
    @Parameter(property = 'api.name')
    private String apiName

    @Parameter(property = 'api.current.version')
    private String currentApiVersion

    @Parameter(property = 'anypoint.username')
    private String anypointUsername

    @Parameter(property = 'anypoint.password')
    private String anypointPassword

    @Parameter(property = 'anypoint.connected-app.id')
    private String anypointConnectedAppId

    @Parameter(property = 'anypoint.connected-app.secret')
    private String anypointConnectedAppSecret

    @Parameter(property = 'anypoint.organizationName')
    private String anypointOrganizationName

    @Parameter(property = 'designCenter.project.name')
    private String designCenterProjectName

    @Parameter(property = 'designCenter.branch.name', defaultValue = 'master')
    private String designCenterBranchName

    @Parameter(property = 'main.raml')
    private String mainRamlFileName

    @Parameter(property = 'local.raml.directory')
    private File localRamlDirectory

    @Parameter(property = 'raml.group.id')
    private String ramlGroupId

    @Parameter(property = 'raml.artifact.id')
    private String ramlArtifactId

    @Parameter(property = 'use.cloudHub', defaultValue = 'true')
    private boolean useCloudHub

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

        File tmpProject = File.createTempDir()
        File mvnProject = mavenProject.basedir
        FileUtils.copyDirectory(mvnProject, tmpProject)
        println "Creating temp project: ${tmpProject.getAbsolutePath()}"

        println "repo: " + local.url
                def localRepo = new File(local.url)
        println "repo file: " + localRepo.absolutePath
        def userHome = new File(System.getProperty('user.home'))
        def mvnHome = new File(userHome, '.m2')
        def mvnRepo = new File(mvnHome, 'repository')
        def ramlVersion = 	"1.1.6"



        def apiDirectory = join(tmpProject,
                'src',
                'main',
                'resources',
                'api')
        apiDirectory.mkdirs()

        // Using Local RAML
        if (localRamlDirectory) {
            log.info "Copying RAMLs from ${localRamlDirectory}"
            assert localRamlDirectory.exists()
            FileUtils.copyDirectory(localRamlDirectory,
                    apiDirectory)

        // Using RAML from Exchange
        } else if (ramlGroupId && ramlArtifactId) {
            File raml = getArtifact(mvnRepo, ramlGroupId, ramlArtifactId, ramlVersion)
            expandArtifact(raml, apiDirectory)
            mainRamlFileName = getMainRaml(apiDirectory)
            processDeps(apiDirectory, mvnRepo)
        // Using RAML from a Design Center project
        } else {
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
            def existingRamlFiles = designCenter.getExistingDesignCenterFilesByProjectName(designCenterProjectName,
                    designCenterBranchName)
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
        }

        // Use first RAML file in the root directory as the main one if a specific one is not provided
        if (!mainRamlFileName || mainRamlFileName == 'NotUsed') {
            def topLevelFiles = new FileNameFinder().getFileNames(apiDirectory.absolutePath,
                    '*.raml')
            // we don't want the full path
            mainRamlFileName = new File(topLevelFiles[0]).name
            log.info "Assuming ${mainRamlFileName} is the top level RAML file"
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
                mainRamlFileName,
                apiName,
                currentApiVersion,
                useCloudHub,
                insertApiNameInListenerPath,
                httpListenerBasePath,
                mavenProject.artifactId,
                httpListenerConfigName,
                this.tempFileOfXmlToInsertBeforeRouter?.text,
                this.tempFileErrorHandlerXml?.text,
                this.tempFileOfHttpResponseXml?.text,
                this.tempFileOfHttpErrorResponseXml?.text)
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
        File artifactDir = new File(mvnRepo, groupId.replaceAll('\\.', File.separator) + File.separator + artifactId + File.separator + version)
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
