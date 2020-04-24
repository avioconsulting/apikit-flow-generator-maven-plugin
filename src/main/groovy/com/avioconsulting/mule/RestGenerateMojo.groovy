package com.avioconsulting.mule

import com.avioconsulting.mule.designcenter.DesignCenterDeployer
import com.avioconsulting.mule.designcenter.HttpClientWrapper
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Component
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject

@Mojo(name = 'generateFlowRest')
class RestGenerateMojo extends AbstractMojo implements FileUtil {
    @Parameter(property = 'api.name')
    private String apiName

    @Parameter(property = 'api.current.version')
    private String currentApiVersion

    @Parameter(property = 'anypoint.username', required = true)
    private String anypointUsername

    @Parameter(property = 'anypoint.password', required = true)
    private String anypointPassword

    @Parameter(property = 'anypoint.organizationName')
    private String anypointOrganizationName

    @Parameter(property = 'designCenter.project.name', required = true)
    private String designCenterProjectName

    @Parameter(property = 'main.raml')
    private String mainRamlFileName

    @Parameter(property = 'use.cloudHub', defaultValue = 'true')
    private boolean useCloudHub

    @Parameter(property = 'apikitgen.insert.api.name.in.listener.path',
            defaultValue = 'true')
    private boolean insertApiNameInListenerPath

    @Parameter(property = 'http.listener.config.name', required = true)
    private String httpListenerConfigName

    @Component
    protected MavenProject mavenProject

    @Override
    void execute() throws MojoExecutionException, MojoFailureException {
        log.info 'Will fetch RAML contents from Design Center first'
        def clientWrapper = new HttpClientWrapper('https://anypoint.mulesoft.com',
                                                  this.anypointUsername,
                                                  this.anypointPassword,
                                                  this.log,
                                                  this.anypointOrganizationName)
        def designCenter = new DesignCenterDeployer(clientWrapper,
                                                    log)
        def existingRamlFiles = designCenter.getExistingDesignCenterFilesByProjectName(designCenterProjectName)
        log.info 'Fetched RAML files OK, now writing to disk'
        def apiDirectory = join(mavenProject.basedir,
                                'src',
                                'main',
                                'resources',
                                'api')
        apiDirectory.mkdirs()
        // need our directories first
        def folders = existingRamlFiles.findAll { f -> f.type == 'FOLDER' }
        folders.each { folder ->
            new File(apiDirectory,
                     folder.fileName).mkdirs()
        }
        def noDirs = existingRamlFiles - folders
        noDirs.each { f ->
            new File(apiDirectory,
                     f.fileName).text = f.contents
        }
        if (!mainRamlFileName) {
            def topLevelFiles = new FileNameFinder().getFileNames(apiDirectory.absolutePath,
                                                                  '*.raml')
            // we don't want the full path
            mainRamlFileName = new File(topLevelFiles[0]).name
            log.info "Assuming ${mainRamlFileName} is the top level RAML file"
        }
        RestGenerator.generate(mavenProject.basedir,
                               mainRamlFileName,
                               apiName,
                               currentApiVersion,
                               useCloudHub,
                               insertApiNameInListenerPath,
                               mavenProject.artifactId,
                               httpListenerConfigName)
    }
}
