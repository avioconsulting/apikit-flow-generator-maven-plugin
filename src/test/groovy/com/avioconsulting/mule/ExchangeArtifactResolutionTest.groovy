package com.avioconsulting.mule

import org.apache.maven.plugin.logging.SystemStreamLog
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.junit.Assert.*

class ExchangeArtifactResolutionTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder()

    private RestGenerator generator() {
        new RestGenerator(new SystemStreamLog(), 'api', 'v1', 'listener', null, null, true)
    }

    @Test void selectsExactRamlZipFromMixedClassifierCache() {
        File repo = temporary.newFolder('repository')
        File directory = new File(repo, 'com/example/orders/1.0.0')
        directory.mkdirs()
        ['oas', 'fat-raml', 'original-oas', 'raml'].each { classifier ->
            new File(directory, "orders-1.0.0-${classifier}.zip").text = classifier
        }
        assertEquals(new File(directory, 'orders-1.0.0-raml.zip'),
                generator().getArtifact(repo, 'com.example', 'orders', '1.0.0'))
    }

    @Test void missingRamlNeverFallsBackToAnotherZip() {
        File repo = temporary.newFolder('repository')
        File directory = new File(repo, 'org-id/orders/1.0.0')
        directory.mkdirs()
        new File(directory, 'orders-1.0.0-oas.zip').text = 'oas'
        try {
            generator().getArtifact(repo, 'org-id', 'orders', '1.0.0')
            fail('Expected exact classifier failure')
        } catch (IllegalArgumentException failure) {
            assertTrue(failure.message.contains('org-id:orders:zip:raml:1.0.0'))
            assertTrue(failure.message.contains('other ZIP classifiers are not substitutes'))
        }
    }
}
