package com.avioconsulting.mule


import com.avioconsulting.mule.anypoint.api.credentials.model.ConnectedAppCredential
import com.avioconsulting.mule.anypoint.api.credentials.model.Credential
import com.avioconsulting.mule.anypoint.api.credentials.model.UsernamePasswordCredential
import com.avioconsulting.mule.designcenter.DesignCenterDeployer
import com.avioconsulting.mule.designcenter.HttpClientWrapper
import org.apache.commons.io.FileUtils
import org.apache.commons.lang.StringEscapeUtils
import org.apache.maven.artifact.Artifact
import org.apache.maven.artifact.repository.ArtifactRepository
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Component
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import org.apache.maven.plugins.annotations.ResolutionScope;
import java.util.zip.ZipFile;

@Mojo(name = 'generateFlowRest' ,
        requiresDependencyResolution = ResolutionScope.COMPILE )
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

    @Parameter(property = 'main.raml' , required = true )
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

    @Parameter( defaultValue = '${localRepository}', readonly = true, required = true )
    private ArtifactRepository local;

    @Component
    protected MavenProject mavenProject

    @Override
    void execute() throws MojoExecutionException, MojoFailureException {

        httpListenerBasePath = StringEscapeUtils.unescapeJava(httpListenerBasePath)
        def apiDirectory = join(mavenProject.basedir,
                                'src',
                                'main',
                                'resources',
                                'api')
        apiDirectory.mkdirs()
        if (localRamlDirectory) {
            log.info "Copying RAMLs from ${localRamlDirectory}"
            assert localRamlDirectory.exists()
            FileUtils.copyDirectory(localRamlDirectory,
                                    apiDirectory)
        } else if(ramlGroupId && ramlArtifactId){
            log.info "Copying RAMLs from  Exchange and the Group id is ${ramlGroupId} and Artifact id is  ${ramlArtifactId}"

            for( Artifact unresolvedArtifact : mavenProject.getDependencyArtifacts()) {

                //Find the artifact in the local repository.
                if(unresolvedArtifact.groupId == ramlGroupId && unresolvedArtifact.artifactId == ramlArtifactId && unresolvedArtifact.classifier =='raml' ) {
                    Artifact art = this.local.find(unresolvedArtifact);
                    File ramlZipFile = art.getFile();
                    def zipFile = new java.util.zip.ZipFile(ramlZipFile)
                    def localRepoRamlDirs = zipFile.entries()
                    log.info 'Fetched RAML file from local repository OK, now writing to disk'
                    // writing directories first
                    localRepoRamlDirs.findAll { it.directory }.each {
                        log.info "Creating Dir " + it.getName() +"..."
                       new File(apiDirectory,
                                it.getName()).mkdirs()
                    }
                    log.debug "Finished Creating Directories "
                    // writing files next 
                    def localRepoRamlFiles = zipFile.entries()
                    localRepoRamlFiles.findAll { !it.directory }.each {
                        log.info "Writing File " + it.getName() +"..."
                        //println zipFile.getInputStream(it).text
                        new File(apiDirectory,
                                it.getName()).text =  zipFile.getInputStream(it).text
                    }
                    log.debug "Finished Writing Files "

                }
            }
        }
        else {
            Credential credential = new UsernamePasswordCredential(this.anypointUsername, this.anypointPassword)
            if(this.anypointConnectedAppId != null && this.anypointConnectedAppSecret != null) {
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
        if (!mainRamlFileName) {
            def topLevelFiles = new FileNameFinder().getFileNames(apiDirectory.absolutePath,
                                                                  '*.raml')
            // we don't want the full path
            mainRamlFileName = new File(topLevelFiles[0]).name
            log.info "Assuming ${mainRamlFileName} is the top level RAML file"
        }
        if (!httpListenerConfigName) {
            log.info 'No http listener config specified, using default, parameterized value of ${http.listener.config}'
            httpListenerConfigName = '${http.listener.config}'
        }
        RestGenerator.generate(mavenProject.basedir,
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
}
