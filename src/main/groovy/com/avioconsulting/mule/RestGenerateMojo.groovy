package com.avioconsulting.mule

import com.avioconsulting.mule.designcenter.DesignCenterDeployer
import com.avioconsulting.mule.designcenter.HttpClientWrapper
import org.apache.commons.io.FileUtils
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

    @Parameter(property = 'designCenter.branch.name', defaultValue = 'master')
    private String designCenterBranchName

    @Parameter(property = 'main.raml')
    private String mainRamlFileName

    @Parameter(property = 'local.raml.directory')
    private File localRamlDirectory

    @Parameter(property = 'use.cloudHub', defaultValue = 'true')
    private boolean useCloudHub

    @Parameter(property = 'apikitgen.insert.api.name.in.listener.path',
            defaultValue = 'true')
    private boolean insertApiNameInListenerPath

    @Parameter(property = 'http.listener.config.name')
    private String httpListenerConfigName

    @Component
    protected MavenProject mavenProject

    @Override
    void execute() throws MojoExecutionException, MojoFailureException {
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
        } else {
            log.info 'Will fetch RAML contents from Design Center first'
            def clientWrapper = new HttpClientWrapper('https://anypoint.mulesoft.com',
                                                      this.anypointUsername,
                                                      this.anypointPassword,
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
                               mavenProject.artifactId,
                               httpListenerConfigName)
    }
}
