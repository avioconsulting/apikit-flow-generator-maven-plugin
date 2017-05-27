package com.avioconsulting.mule

import org.apache.commons.io.FileUtils
import org.junit.Test

import static org.hamcrest.Matchers.equalTo
import static org.hamcrest.Matchers.is
import static org.junit.Assert.assertThat

@SuppressWarnings("GroovyAssignabilityCheck")
class GeneratorTest implements FileUtil {
    @Test
    void generates_Flow() {
        // arrange
        def tempDir = join new File('build'), 'tmp', 'test'
        if (tempDir.exists()) {
            tempDir.deleteDir()
        }
        tempDir.mkdirs()
        def mainDir = join tempDir, 'src', 'main'
        mainDir.mkdirs()
        def appDir = join mainDir, 'app'
        appDir.mkdirs()
        def apiDir = join mainDir, 'api'
        apiDir.mkdirs()
        def sourceFile = join new File('src'), 'test', 'resources', 'api-stuff-v1.raml'
        FileUtils.copyFileToDirectory(sourceFile, apiDir)

        // act
        Generator.generate(tempDir, 'api-stuff-v1.raml')

        // assert
        def xmlFile = join appDir, 'api-stuff-v1.xml'
        assert xmlFile.exists()
        def xmlNode = new XmlParser().parse(xmlFile)
        assertThat xmlNode.flow[0].@name,
                   is(equalTo('api-stuff-v1-main'))
    }
}
