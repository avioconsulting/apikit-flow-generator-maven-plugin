package com.avioconsulting.mule

import org.apache.maven.plugin.logging.SystemStreamLog
import org.jdom2.input.SAXBuilder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

import static org.junit.Assert.*

@RunWith(Parameterized)
class RestContractGenerationTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder()

    @Parameterized.Parameters(name = '{0} from {1}')
    static Collection<Object[]> contracts() {
        [['oas', 'local'], ['raml', 'local'], ['oas', 'exchange'], ['raml', 'exchange']]
                .collect { it as Object[] }
    }

    private final String format
    private final String source

    RestContractGenerationTest(String format, String source) {
        this.format = format
        this.source = source
    }

    @Test void generatedFilesMatchContractAndAvioWiring() {
        File project = temporary.newFolder('project')
        File global = new File(project, 'src/main/mule/global/global-config.xml')
        global.parentFile.mkdirs()
        global.text = '''<mule xmlns="http://www.mulesoft.org/schema/mule/core"
          xmlns:api-gateway="http://www.mulesoft.org/schema/mule/api-gateway">
          <api-gateway:autodiscovery apiId="${api.id}" flowRef="old-main"/>
          <error-handler name="global-error-handler"/>
        </mule>'''
        File fixture = new File(getClass().getResource("/contracts/${format}").toURI())
        String entrypoint = format == 'oas' ? 'api.yaml' : 'api.raml'
        def generator = new RestGenerator(new SystemStreamLog(), 'orders', 'v1',
                'shared-listener', null, null, true)

        if (source == 'local') {
            generator.generateFromLocal(project, fixture, entrypoint)
            assertTrue(new File(project, "src/main/resources/api/${entrypoint}").isFile())
        } else {
            File archive = new File(temporary.root, "cached orders-1.0.0-${format}.zip")
            new ZipOutputStream(new FileOutputStream(archive)).withCloseable { zip ->
                // No directory entries: modular Exchange archives need not contain them.
                fixture.eachFileRecurse { file ->
                    if (file.isFile()) {
                        zip.putNextEntry(new ZipEntry(fixture.toPath().relativize(file.toPath()).toString().replace('\\', '/')))
                        zip.write(file.bytes)
                        zip.closeEntry()
                    }
                }
                zip.putNextEntry(new ZipEntry('exchange.json'))
                zip.write("""{"main":"${entrypoint}","classifier":"${format}"}""".getBytes('UTF-8'))
                zip.closeEntry()
            }
            generator.generateFromExchange(project, 'com.example', 'orders', '1.0.0',
                    temporary.newFolder('repository'), archive, format)
            assertFalse(new File(project, 'src/main/resources/api').exists())
        }

        def root = new SAXBuilder().build(new File(project, 'src/main/mule/api.xml')).rootElement
        def configs = root.getChildren('config', RestGenerator.apiKit)
        assertEquals(1, configs.size())
        def config = configs[0]
        assertEquals('api-config', config.getAttributeValue('name'))
        assertEquals('${apikit.validation.disabled}', config.getAttributeValue('disableValidations'))
        assertEquals('httpStatus', config.getAttributeValue('httpStatusVarName'))
        assertEquals('outboundHeaders', config.getAttributeValue('outboundHeadersMapName'))
        String expectedReference = source == 'exchange' ?
                "resource::com.example:orders:1.0.0:${format}:zip:${entrypoint}" : "api/${entrypoint}"
        assertEquals(expectedReference, config.getAttributeValue('api'))

        def flows = root.getChildren('flow', RestGenerator.core)
        assertEquals([
                'api-main',
                'get:\\health:api-config',
                'get:\\orders\\(id):api-config',
                'post:\\orders\\(id):application\\json:api-config',
                'post:\\orders\\(id):application\\xml:api-config'
        ] as Set, flows.collect { it.getAttributeValue('name') } as Set)
        def main = flows.find { it.getAttributeValue('name') == 'api-main' }
        assertEquals('api-config', main.getChild('router', RestGenerator.apiKit).getAttributeValue('config-ref'))
        def listener = main.getChild('listener', RestGenerator.http)
        assertEquals('shared-listener', listener.getAttributeValue('config-ref'))
        assertEquals('/orders/v1/*', listener.getAttributeValue('path'))
        assertNotNull(listener.getChild('response', RestGenerator.http))
        assertNotNull(listener.getChild('error-response', RestGenerator.http))
        assertEquals('global-error-handler', main.getChild('error-handler', RestGenerator.core).getAttributeValue('ref'))
        assertTrue(root.getChildren('listener-config', RestGenerator.http).isEmpty())
        assertEquals('api-main', new SAXBuilder().build(global).rootElement
                .getChild('autodiscovery', RestGenerator.apiGateway).getAttributeValue('flowRef'))
    }
}
