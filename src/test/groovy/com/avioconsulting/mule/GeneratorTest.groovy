package com.avioconsulting.mule

import groovy.xml.Namespace
import org.apache.commons.io.FileUtils
import org.junit.Before
import org.junit.Test

import static org.hamcrest.Matchers.equalTo
import static org.hamcrest.Matchers.is
import static org.junit.Assert.assertThat

@SuppressWarnings("GroovyAssignabilityCheck")
class GeneratorTest implements FileUtil {
    private File tempDir, appDir
    private static Namespace http = Generator.http

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
        def httpListenerPort = xmlNode[http.'listener-config'][0].@port
        assertThat httpListenerPort,
                   is(equalTo('${http.port}'))
    }

    @Test
    void addsHttpsListener() {
        // arrange

        // act
        Generator.generate(tempDir, 'api-stuff-v1.raml')

        // assert
        def xmlNode = getXmlNode('api-stuff-v1.xml')
        def httpsListenerConfig = xmlNode[http.'listener-config'].find { node ->
            node.@protocol == 'HTTPS'
        }
        assert httpsListenerConfig
        assertThat httpsListenerConfig.@name,
                   is(equalTo('api-stuff-v1-httpsListenerConfig'))
        assertThat httpsListenerConfig.@port,
                   is(equalTo('${https.port}'))
        assertThat httpsListenerConfig.@host,
                   is(equalTo('0.0.0.0'))
        fail 'keystore'
    }

    @Test
    void keystoreSelfSigned() {
        // arrange

        // act

        // assert
        fail 'write this'
    }
}
