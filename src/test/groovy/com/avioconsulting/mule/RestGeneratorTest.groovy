package com.avioconsulting.mule

import groovy.xml.Namespace
import groovy.xml.QName
import org.apache.commons.io.FileUtils
import org.junit.Before
import org.junit.Test

import static org.hamcrest.Matchers.*
import static org.junit.Assert.assertThat

@SuppressWarnings("GroovyAssignabilityCheck")
class RestGeneratorTest implements FileUtil {
    private File tempDir, appDir, mainDir, apiDir
    private static Namespace http = new Namespace(RestGenerator.http.URI)
    public static final Namespace apiKit = new Namespace(RestGenerator.apiKit.URI)
    public static final Namespace doc = new Namespace(RestGenerator.doc.URI)
    public static final Namespace ee = new Namespace(RestGenerator.ee.URI)

    @Before
    void setup() {
        tempDir = join new File('build'),
                       'tmp',
                       'test'
        if (tempDir.exists()) {
            tempDir.deleteDir()
        }
        tempDir.mkdirs()
        mainDir = join tempDir,
                       'src',
                       'main'
        mainDir.mkdirs()
        appDir = join mainDir,
                      'mule'
        appDir.mkdirs()
        apiDir = join mainDir,
                      'resources',
                      'api'
        apiDir.mkdirs()
        def testResources = join new File('src'),
                                 'test',
                                 'resources'
        [
                'api-stuff-v1.raml',
                'ref_type.raml',
        ].each { filename ->
            def sourceFile = join testResources,
                                  filename
            FileUtils.copyFileToDirectory(sourceFile,
                                          apiDir)
        }
        def sourceFile = join testResources,
                              'somedir/1.0.0/sometrait.raml'
        FileUtils.copyFileToDirectory(sourceFile,
                                      join(apiDir,
                                           'somedir',
                                           '1.0.0'))
        // scaffolder won't run without this
        FileUtils.copyFileToDirectory(join(testResources,
                                           'mule-artifact.json'),
                                      tempDir)
    }

    Node getXmlNode(String xmlPath) {
        def xmlFile = join appDir,
                           xmlPath
        assert xmlFile.exists()
        new XmlParser(false,
                      true).parse(xmlFile)
    }

    @Test
    void generates_Flow() {
        // arrange

        // act
        RestGenerator.generate(tempDir,
                               'api-stuff-v1.raml',
                               'stuff',
                               'v1',
                               false,
                               true,
                               'theProject',
                               '${http.listener.config}',
                               null,
                               null)

        // assert
        def xmlNode = getXmlNode('api-stuff-v1.xml')
        assertThat xmlNode.flow[0].@name,
                   is(equalTo('api-stuff-v1-main'))
    }

    @Test
    void generates_Flow_without_api_name() {
        // arrange

        // act
        RestGenerator.generate(tempDir,
                               'api-stuff-v1.raml',
                               'stuff',
                               'v1',
                               false,
                               false,
                               'theProject',
                               '${http.listener.config}',
                               null,
                               null)

        // assert
        def xmlNode = getXmlNode('api-stuff-v1.xml')
        def listeners = xmlNode.flow[http.listener].'@path'
        assertThat listeners,
                   is(equalTo(['/api/v1/*']))
    }

    @Test
    void regenerates_Flow() {
        // arrange
        RestGenerator.generate(tempDir,
                               'api-stuff-v1.raml',
                               'stuff',
                               'v1',
                               false,
                               true,
                               'theProject',
                               '${http.listener.config}',
                               null,
                               null)
        def flowXmlFile = join(appDir,
                               'api-stuff-v1.xml')
        assert flowXmlFile.exists()
        def existingFlowXmlContents = flowXmlFile.text

        // act
        RestGenerator.generate(tempDir,
                               'api-stuff-v1.raml',
                               'stuff',
                               'v1',
                               false,
                               true,
                               'theProject',
                               '${http.listener.config}',
                               null,
                               null)

        // assert
        assertThat flowXmlFile.text,
                   is(equalTo(existingFlowXmlContents))
    }

    @Test
    void fixes_Raml_No() {
        // arrange
        def raml = 'api-stuff-v1.raml'
        def ramlFile = join(apiDir,
                            raml)
        def origRamlText = ramlFile.text

        // act
        RestGenerator.generate(tempDir,
                               raml,
                               'stuff',
                               'v1',
                               false,
                               true,
                               'theProject',
                               '${http.listener.config}',
                               null,
                               null)

        // assert
        assertThat ramlFile.text,
                   is(equalTo(origRamlText))
    }

    @Test
    void fixes_Raml_Yes() {
        // arrange
        def raml = 'api-stuff-v1.raml'
        def ramlFile = join(apiDir,
                            raml)
        def origRamlText = ramlFile.text

        // act
        RestGenerator.generate(tempDir,
                               raml,
                               'stuff',
                               'v1',
                               true,
                               true,
                               'theProject',
                               '${http.listener.config}',
                               null,
                               null)

        // assert
        assertThat ramlFile.text,
                   is(not(equalTo(origRamlText)))
        assertThat ramlFile.text,
                   is(containsString('baseUri: https://theProject.cloudhub.io/stuff/api/{version}'))
    }

    @Test
    void removesHttpConfig() {
        // arrange

        // act
        RestGenerator.generate(tempDir,
                               'api-stuff-v1.raml',
                               'stuff',
                               'v1',
                               false,
                               true,
                               'theProject',
                               '${http.listener.config}',
                               null,
                               null)

        // assert
        def xmlNode = getXmlNode('api-stuff-v1.xml')
        assertThat xmlNode[http.'listener-config'],
                   is(equalTo([]))
    }

    @Test
    void parameterizesListeners() {
        // arrange

        // act
        RestGenerator.generate(tempDir,
                               'api-stuff-v1.raml',
                               'stuff',
                               'v1',
                               false,
                               true,
                               'theProject',
                               'some-http-config',
                               null,
                               null)

        // assert
        def xmlNode = getXmlNode('api-stuff-v1.xml')
        def listeners = xmlNode.flow[http.listener].'@config-ref'
        assertThat listeners,
                   is(equalTo(['some-http-config']))
    }

    @Test
    void apiPaths() {
        // arrange

        // act
        RestGenerator.generate(tempDir,
                               'api-stuff-v1.raml',
                               'stuff',
                               'v1',
                               false,
                               true,
                               'theProject',
                               '${http.listener.config}',
                               null,
                               null)

        // assert
        def xmlNode = getXmlNode('api-stuff-v1.xml')
        def listeners = xmlNode.flow[http.listener].'@path'
        assertThat listeners,
                   is(equalTo(['/stuff/api/v1/*']))
    }

    @Test
    void apiKitConfig_Parameterized() {
        // arrange

        // act
        RestGenerator.generate(tempDir,
                               'api-stuff-v1.raml',
                               'stuff',
                               'v1',
                               false,
                               true,
                               'theProject',
                               '${http.listener.config}',
                               null,
                               null)

        // assert
        def xmlNode = getXmlNode('api-stuff-v1.xml')
        def apiKitConfig = xmlNode[apiKit.'config'][0]
        assert apiKitConfig
        assertThat apiKitConfig.'@disableValidations',
                   is(equalTo('${skip.apikit.validation}'))
    }

    def getChildNodeNames(Node node) {
        node.children()
                .collect { Node n -> n.name() as QName }
                .collect { name -> name.localPart }
    }

    @Test
    void changesBadRequestFlow() {
        // arrange

        // act
        RestGenerator.generate(tempDir,
                               'api-stuff-v1.raml',
                               'stuff',
                               'v1',
                               false,
                               true,
                               'theProject',
                               '${http.listener.config}',
                               null,
                               null)

        // assert
        def xmlNode = getXmlNode('api-stuff-v1.xml')
        def flowNode = xmlNode.flow.find { Node node ->
            node.'@name' == 'api-stuff-v1-main'
        }
        assert flowNode
        def badRequestNode = flowNode['error-handler']['on-error-propagate'].find { Node node ->
            node.'@type' == 'APIKIT:BAD_REQUEST'
        } as Node
        assert badRequestNode
        def badRequestWeavePayloadNode = badRequestNode[ee.'transform'][ee.'message'][ee.'set-payload'][0] as Node
        def dwContents = badRequestWeavePayloadNode.value()[0]
        assertThat dwContents,
                   is(equalTo('%dw 2.0\noutput application/json\n---\nif (p(\'return.validation.failures\')) {error_details: error.description} else {message: \"Bad request\"}'))
    }

    @Test
    void disablesApiKitConsole() {
        // arrange

        // act
        RestGenerator.generate(tempDir,
                               'api-stuff-v1.raml',
                               'stuff',
                               'v1',
                               false,
                               true,
                               'theProject',
                               '${http.listener.config}',
                               null,
                               null)

        // assert
        def xmlNode = getXmlNode('api-stuff-v1.xml')
        def flowNode = xmlNode.flow.find { Node node ->
            node.'@name' == 'api-stuff-v1-console'
        }
        assert !flowNode
    }

    @Test
    void xml_before_router() {
        // arrange
        def xmlBeforeRouter = """<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<logger xmlns="http://some/namespace" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://some/namespace http://some/namespace.xsd"/>"""

        // act
        RestGenerator.generate(tempDir,
                               'api-stuff-v1.raml',
                               'stuff',
                               'v1',
                               false,
                               true,
                               'theProject',
                               '${http.listener.config}',
                               xmlBeforeRouter,
                               null)

        // assert
        def xmlNode = getXmlNode('api-stuff-v1.xml')
        def flowNode = xmlNode.flow.find { Node node ->
            node.'@name' == 'api-stuff-v1-main'
        }
        assert flowNode
        def kids = flowNode.children().collect { node -> node.name().toString() as String }
        assertThat kids,
                   is(equalTo([
                           '{http://www.mulesoft.org/schema/mule/http}listener',
                           '{http://some/namespace}logger',
                           '{http://www.mulesoft.org/schema/mule/mule-apikit}router',
                           '{http://www.mulesoft.org/schema/mule/core}error-handler'
                   ]))
    }

    @Test
    void custom_error_handler() {
        // arrange
        def errorHandler = """<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<error-handler xmlns="http://www.mulesoft.org/schema/mule/core" ref="howdy"/>
"""

        // act
        RestGenerator.generate(tempDir,
                               'api-stuff-v1.raml',
                               'stuff',
                               'v1',
                               false,
                               true,
                               'theProject',
                               '${http.listener.config}',
                               null,
                               errorHandler)

        // assert
        def xmlNode = getXmlNode('api-stuff-v1.xml')
        def flowNode = xmlNode.flow.find { Node node ->
            node.'@name' == 'api-stuff-v1-main'
        } as Node
        assert flowNode
        def kidNames = flowNode.children().collect { node -> node.name().toString() as String }
        assertThat kidNames,
                   is(equalTo([
                           '{http://www.mulesoft.org/schema/mule/http}listener',
                           '{http://www.mulesoft.org/schema/mule/mule-apikit}router',
                           '{http://www.mulesoft.org/schema/mule/core}error-handler'
                   ]))
        def errorHandlerNode = flowNode.children().last() as Node
        assert errorHandlerNode
        assertThat errorHandlerNode.'@ref',
                   is(equalTo('howdy'))
    }

    @Test
    void both_xml_before_router_and_custom_error_handler() {
        // arrange
        def xmlBeforeRouter = """<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<logger xmlns="http://some/namespace" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://some/namespace http://some/namespace.xsd"/>"""
        def errorHandler = """<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<error-handler xmlns="http://www.mulesoft.org/schema/mule/core" ref="howdy"/>
"""

        // act
        RestGenerator.generate(tempDir,
                               'api-stuff-v1.raml',
                               'stuff',
                               'v1',
                               false,
                               true,
                               'theProject',
                               '${http.listener.config}',
                               xmlBeforeRouter,
                               errorHandler)

        // assert
        def xmlNode = getXmlNode('api-stuff-v1.xml')
        def flowNode = xmlNode.flow.find { Node node ->
            node.'@name' == 'api-stuff-v1-main'
        } as Node
        assert flowNode
        def kidNames = flowNode.children().collect { node -> node.name().toString() as String }
        assertThat kidNames,
                   is(equalTo([
                           '{http://www.mulesoft.org/schema/mule/http}listener',
                           '{http://some/namespace}logger',
                           '{http://www.mulesoft.org/schema/mule/mule-apikit}router',
                           '{http://www.mulesoft.org/schema/mule/core}error-handler'
                   ]))
        def errorHandlerNode = flowNode.children().last() as Node
        assert errorHandlerNode
        assertThat errorHandlerNode.'@ref',
                   is(equalTo('howdy'))
    }
}
