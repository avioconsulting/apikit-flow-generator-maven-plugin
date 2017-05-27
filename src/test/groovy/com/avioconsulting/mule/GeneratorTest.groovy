package com.avioconsulting.mule

import org.apache.commons.io.FileUtils
import org.junit.Before
import org.junit.Test

import static org.hamcrest.Matchers.equalTo
import static org.hamcrest.Matchers.is
import static org.junit.Assert.assertThat

@SuppressWarnings("GroovyAssignabilityCheck")
class GeneratorTest implements FileUtil {
    private File tempDir, appDir

    @Before
    void setup() {
        tempDir = join new File('build'), 'tmp', 'test'
        if (tempDir.exists()) {
            tempDir.deleteDir()
        }
        tempDir.mkdirs()
        def mainDir = join tempDir, 'src', 'main'
        mainDir.mkdirs()
        appDir = join mainDir, 'app'
        appDir.mkdirs()
        def apiDir = join mainDir, 'api'
        apiDir.mkdirs()
        def sourceFile = join new File('src'), 'test', 'resources', 'api-stuff-v1.raml'
        FileUtils.copyFileToDirectory(sourceFile, apiDir)
    }

    def getXmlNode(String xmlPath) {
        def xmlFile = join appDir, xmlPath
        assert xmlFile.exists()
        new XmlParser().parse(xmlFile)
    }

    @Test
    void generates_Flow() {
        // arrange

        // act
        Generator.generate(tempDir, 'api-stuff-v1.raml')

        // assert
        def xmlNode = getXmlNode('api-stuff-v1.xml')
        assertThat xmlNode.flow[0].@name,
                   is(equalTo('api-stuff-v1-main'))
    }

    @Test
    void updatesHttpPort() {
        // arrange

        // act
        Generator.generate(tempDir, 'api-stuff-v1.raml')

        // assert
        def xmlNode = getXmlNode('api-stuff-v1.xml')
        def httpListenerPort = xmlNode.'http:listener-config'[0].@port
        assertThat httpListenerPort,
                   is(equalTo('${http.port}'))
    }
}
