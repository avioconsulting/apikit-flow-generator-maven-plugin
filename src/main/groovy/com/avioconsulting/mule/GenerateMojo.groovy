package com.avioconsulting.mule

import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Component
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import org.mule.tools.apikit.ScaffolderAPI

@Mojo(name = 'generateFlow')
class GenerateMojo extends AbstractMojo {
    @Parameter(property = 'api.name')
    private String apiName

    @Parameter(property = 'api.version')
    private String apiVersion

    @Parameter(property = 'raml.path', defaultValue = 'api-${api.name}-${api.version}.raml')
    private String ramlPath

    @Component
    protected MavenProject mavenProject

    protected static File join(File parent, String... parts) {
        def separator = System.getProperty 'file.separator'
        new File(parent, parts.join(separator))
    }

    @Override
    void execute() throws MojoExecutionException, MojoFailureException {
        def apiBuilder = new ScaffolderAPI()
        def mainDir = join(mavenProject.basedir, 'src', 'main')
        def ramlFile = join(mainDir, 'api', ramlPath)
        assert ramlFile.exists()
        def appDirectory = join(mainDir, 'app')
        apiBuilder.run([ramlFile],
                       appDirectory)
    }
}
