package com.avioconsulting.mule

import groovy.xml.Namespace
import org.apache.commons.io.FileUtils
import org.junit.Before
import org.junit.Test

import static org.hamcrest.Matchers.*
import static org.junit.Assert.assertThat

@SuppressWarnings("GroovyAssignabilityCheck")
class GeneratorTest implements FileUtil {
    private File tempDir, appDir
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
        def mainDir = join tempDir, 'src', 'main'
        mainDir.mkdirs()
        appDir = join mainDir, 'app'
        appDir.mkdirs()
        def apiDir = join mainDir, 'api'
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
                           'v1')

        // assert
        def xmlNode = getXmlNode('api-stuff-v1.xml')
        assertThat xmlNode.flow[0].@name,
                   is(equalTo('api-stuff-v1-main'))
    }

    @Test
    void removesHttpConfig() {
        // arrange

        // act
        Generator.generate(tempDir,
                           'api-stuff-v1.raml',
                           'stuff',
                           'v1')

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
                           'v1')

        // assert
        def xmlNode = getXmlNode('api-stuff-v1.xml')
        def listeners = xmlNode.flow[http.listener].'@config-ref'
        assertThat listeners,
                   is(equalTo(['${http.listener.config}',
                               '${http.listener.config}']))
    }

    @Test
    void generatesMuleDeployProperties() {
        // arrange

        // act
        Generator.generate(tempDir,
                           'api-stuff-v1.raml',
                           'stuff',
                           'v1')

        // assert
        def props = new Properties()
        def propsPath = join(appDir, 'mule-deploy.properties')
        props.load(propsPath.newInputStream())
        assertThat props.'config.resources',
                   is(equalTo('global.xml,api-stuff-v1.xml'))
    }

    @Test
    void updatesMuleDeployProperties() {
        // arrange
        def props = new Properties()
        appDir.mkdirs()
        def propsPath = join(appDir, 'mule-deploy.properties')
        props.put('config.resources', 'existing.xml')
        props.store(propsPath.newOutputStream(), 'Foo')

        // act
        Generator.generate(tempDir,
                           'api-stuff-v1.raml',
                           'stuff',
                           'v1')

        // assert
        props = new Properties()
        props.load(propsPath.newInputStream())
        assertThat props.'config.resources',
                   is(equalTo('global.xml,existing.xml,api-stuff-v1.xml'))
    }

    @Test
    void updatesMuleDeployProperties_alreadyThere() {
        // arrange
        def props = new Properties()
        appDir.mkdirs()
        def propsPath = join(appDir, 'mule-deploy.properties')
        props.put('config.resources', 'existing.xml')
        props.store(propsPath.newOutputStream(), 'Foo')
        Generator.generate(tempDir,
                           'api-stuff-v1.raml',
                           'stuff',
                           'v1')

        // act
        Generator.generate(tempDir,
                           'api-stuff-v1.raml',
                           'stuff',
                           'v1')

        // assert
        props = new Properties()
        props.load(propsPath.newInputStream())
        assertThat props.'config.resources',
                   is(equalTo('existing.xml,global.xml,api-stuff-v1.xml'))
    }

    @Test
    void globalXml_Http_Port() {
        // arrange

        // act
        Generator.generate(tempDir,
                           'api-stuff-v1.raml',
                           'stuff',
                           'v1')

        // assert
        def xmlNode = getXmlNode('global.xml')
        def httpsListenerConfig = xmlNode[http.'listener-config'][0]
        assertThat httpsListenerConfig.@port,
                   is(equalTo('${http.port}'))
        assertThat httpsListenerConfig.@name,
                   is(equalTo('global-http-listener-config'))
    }

    @Test
    void globalXml_Https_Port() {
        // arrange

        // act
        Generator.generate(tempDir,
                           'api-stuff-v1.raml',
                           'stuff',
                           'v1')

        // assert
        def xmlPath = 'global.xml'
        def xmlNode = getXmlNode(xmlPath)
        def httpsListenerConfig = xmlNode[http.'listener-config'].find { node ->
            node.@protocol == 'HTTPS'
        }
        assert httpsListenerConfig
        assertThat httpsListenerConfig.@name,
                   is(equalTo('global-https-listener-config'))
        assertThat httpsListenerConfig.@port,
                   is(equalTo('${https.port}'))
        assertThat httpsListenerConfig.@host,
                   is(equalTo('0.0.0.0'))
        def tlsContext = httpsListenerConfig[tls.'context'][0]
        assert tlsContext
        def tlsKeystore = tlsContext[tls.'key-store'][0]
        assert tlsKeystore
        assertThat tlsKeystore.@type,
                   is(equalTo('jks'))
        assertThat tlsKeystore.@path,
                   is(equalTo('keystores/listener_keystore.jks'))
        assertThat tlsKeystore.@alias,
                   is(equalTo('selfsigned'))
        assertThat tlsKeystore.@keyPassword,
                   is(equalTo('changeit'))
        assertThat tlsKeystore.@password,
                   is(equalTo('changeit'))
    }

    @Test
    void globalXmlAlreadyExists() {
        // arrange
        Generator.generate(tempDir,
                           'api-stuff-v1.raml',
                           'stuff',
                           'v1')
        def globalXmlPath = join appDir, 'global.xml'
        globalXmlPath.write "${globalXmlPath.text} <!-- foo -->"

        // act
        Generator.generate(tempDir,
                           'api-stuff-v1.raml',
                           'stuff',
                           'v1')

        // assert
        assertThat globalXmlPath.text,
                   is(containsString('<!-- foo -->'))
    }

    @Test
    void apiPaths() {
        // arrange

        // act

        // assert
        fail 'write this'
    }

    @Test
    void keystoreSelfSigned() {
        // arrange

        // act

        // assert
        fail 'write this'
    }

    @Test
    void apiAutoDiscovery() {
        // arrange

        // act
        Generator.generate(tempDir,
                           'api-stuff-v1.raml',
                           'stuff',
                           'v1')

        // assert
        def xmlPath = 'global.xml'
        def xmlNode = getXmlNode(xmlPath)
        def autoDiscoveryNode = xmlNode[autoDiscovery.'api'][0] as Node
        assert autoDiscoveryNode
        assertThat autoDiscoveryNode.@apiName,
                   is(equalTo('stuff${api.env.suffix}'))
        assertThat autoDiscoveryNode.@version,
                   is(equalTo('v1'))
        assertThat autoDiscoveryNode.@flowRef,
                   is(equalTo('api-stuff-v1-main'))
        assertThat autoDiscoveryNode.@apikitRef,
                   is(equalTo('api-stuff-v1-config'))
    }
}
