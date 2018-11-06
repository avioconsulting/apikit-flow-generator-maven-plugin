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
        def sourceFile = join testResources,
                              'api-stuff-v1.raml'
        FileUtils.copyFileToDirectory(sourceFile,
                                      apiDir)
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
                               '${http.listener.config}')

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
                               '${http.listener.config}')

        // assert
        def xmlNode = getXmlNode('api-stuff-v1.xml')
        def listeners = xmlNode.flow[http.listener].'@path'
        assertThat listeners,
                   is(equalTo(['/api/v1/*', '/console/v1/*']))
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
                               '${http.listener.config}')
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
                               '${http.listener.config}')

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
                               '${http.listener.config}')

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
                               '${http.listener.config}')

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
                               '${http.listener.config}')

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
                               'some-http-config')

        // assert
        def xmlNode = getXmlNode('api-stuff-v1.xml')
        def listeners = xmlNode.flow[http.listener].'@config-ref'
        assertThat listeners,
                   is(equalTo(['some-http-config',
                               'some-http-config']))
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
                               '${http.listener.config}')

        // assert
        def xmlNode = getXmlNode('api-stuff-v1.xml')
        def listeners = xmlNode.flow[http.listener].'@path'
        assertThat listeners,
                   is(equalTo(['/stuff/api/v1/*', '/stuff/console/v1/*']))
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
                               '${http.listener.config}')

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
                               '${http.listener.config}')

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
                               '${http.listener.config}')

        // assert
        def xmlNode = getXmlNode('api-stuff-v1.xml')
        def flowNode = xmlNode.flow.find { Node node ->
            node.'@name' == 'api-stuff-v1-console'
        }
        assert flowNode
        assertThat getChildNodeNames(flowNode),
                   is(equalTo([
                           'listener',
                           'choice',
                           'error-handler'
                   ]))
        def choice = flowNode.choice[0]
        assert choice
        def choiceWhen = choice.when[0] as Node
        assert choiceWhen
        assertThat choiceWhen.'@expression',
                   is(equalTo('${enable.apikit.console}'))
        assertThat getChildNodeNames(choiceWhen),
                   is(equalTo([
                           'console'
                   ]))
        def otherwise = choice.otherwise[0] as Node
        assert otherwise
        def payload = otherwise['set-payload'][0] as Node
        assert payload
        assertThat payload['@value'],
                   is(containsString('Resource not found'))
        assertThat payload.attribute(doc.name),
                   is(equalTo('Error message to caller'))
    }
}
