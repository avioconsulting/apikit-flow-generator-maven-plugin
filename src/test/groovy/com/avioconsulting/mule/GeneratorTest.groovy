package com.avioconsulting.mule

import groovy.xml.Namespace
import org.apache.commons.io.FileUtils
import org.junit.Before
import org.junit.Test

import static org.hamcrest.Matchers.*
import static org.junit.Assert.assertThat

@SuppressWarnings("GroovyAssignabilityCheck")
class GeneratorTest implements FileUtil {
    private File tempDir, appDir, mainDir, apiDir
    private static Namespace http = Generator.http
    private static Namespace tls = Generator.tls
    public static final Namespace autoDiscovery = Generator.autoDiscovery

    @Before
    void setup() {
        tempDir = join new File('build'), 'tmp', 'test'
        if (tempDir.exists()) {
            tempDir.deleteDir()
        }
        tempDir.mkdirs()
        mainDir = join tempDir, 'src', 'main'
        mainDir.mkdirs()
        appDir = join mainDir, 'app'
        appDir.mkdirs()
        apiDir = join mainDir, 'api'
        apiDir.mkdirs()
        def sourceFile = join new File('src'), 'test', 'resources', 'api-stuff-v1.raml'
        FileUtils.copyFileToDirectory(sourceFile, apiDir)
    }

    Node getXmlNode(String xmlPath) {
        def xmlFile = join appDir, xmlPath
        assert xmlFile.exists()
        new XmlParser().parse(xmlFile)
    }

    @Test
    void generates_Flow() {
        // arrange

        // act
        Generator.generate(tempDir,
                           'api-stuff-v1.raml',
                           'stuff',
                           'v1',
                           false,
                           'theProject')

        // assert
        def xmlNode = getXmlNode('api-stuff-v1.xml')
        assertThat xmlNode.flow[0].@name,
                   is(equalTo('api-stuff-v1-main'))
    }

    @Test
    void regenerates_Flow() {
        // arrange
        Generator.generate(tempDir,
                           'api-stuff-v1.raml',
                           'stuff',
                           'v1',
                           false,
                           'theProject')
        def flowXmlFile = join(appDir, 'api-stuff-v1.xml')
        assert flowXmlFile.exists()
        def existingFlowXmlContents = flowXmlFile.text

        // act
        Generator.generate(tempDir,
                           'api-stuff-v1.raml',
                           'stuff',
                           'v1',
                           false,
                           'theProject')

        // assert
        assertThat flowXmlFile.text,
                   is(equalTo(existingFlowXmlContents))
    }

    @Test
    void fixes_Raml_No() {
        // arrange
        def raml = 'api-stuff-v1.raml'
        def ramlFile = join(apiDir, raml)
        def origRamlText = ramlFile.text

        // act
        Generator.generate(tempDir,
                           raml,
                           'stuff',
                           'v1',
                           false,
                           'theProject')

        // assert
        assertThat ramlFile.text,
                   is(equalTo(origRamlText))
    }

    @Test
    void fixes_Raml_Yes() {
        // arrange
        def raml = 'api-stuff-v1.raml'
        def ramlFile = join(apiDir, raml)
        def origRamlText = ramlFile.text

        // act
        Generator.generate(tempDir,
                           raml,
                           'stuff',
                           'v1',
                           true,
                           'theProject')

        // assert
        assertThat ramlFile.text,
                   is(not(equalTo(origRamlText)))
        assertThat ramlFile.text,
                   is(containsString('baseUri: https://theProject.cloudhub.io/theProject/api/{version}'))
    }

    @Test
    void removesHttpConfig() {
        // arrange

        // act
        Generator.generate(tempDir,
                           'api-stuff-v1.raml',
                           'stuff',
                           'v1',
                           false, 'theProject')

        // assert
        def xmlNode = getXmlNode('api-stuff-v1.xml')
        assertThat xmlNode[http.'listener-config'],
                   is(equalTo([]))
    }

    @Test
    void parameterizesListeners() {
        // arrange

        // act
        Generator.generate(tempDir,
                           'api-stuff-v1.raml',
                           'stuff',
                           'v1',
                           false, 'theProject')

        // assert
        def xmlNode = getXmlNode('api-stuff-v1.xml')
        def listeners = xmlNode.flow[http.listener].'@config-ref'
        assertThat listeners,
                   is(equalTo(['${http.listener.config}',
                               '${http.listener.config}']))
    }

    @Test
    void apiPaths() {
        // arrange

        // act
        Generator.generate(tempDir,
                           'api-stuff-v1.raml',
                           'stuff',
                           'v22', false, 'theProject')

        // assert
        def xmlNode = getXmlNode('api-stuff-v1.xml')
        def listeners = xmlNode.flow[http.listener].'@path'
        assertThat listeners,
                   is(equalTo(['/stuff/api/v22/*', '/stuff/console/v22/*']))
    }
}
