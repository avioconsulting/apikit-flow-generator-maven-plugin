package com.avioconsulting.mule

import org.junit.Before
import org.junit.Test

import static org.hamcrest.Matchers.equalTo
import static org.hamcrest.Matchers.is
import static org.junit.Assert.assertThat

class SoapGeneratorTest implements FileUtil {
    private File tempDir, appDir, mainDir

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
    }

    @Test
    void newFile() {
        // arrange
        writeMuleDeployProps()

        // act
        SoapGenerator.generate(tempDir,
                               new File('src/test/resources/wsdl/input.wsdl'),
                               'v1',
                               'WeirdServiceName',
                               'WeirdPortName',
                               'theConfig')

        // assert
        def actual = new File(appDir, 'input_v1.xml')
        assert actual.exists()
        def expected = new File('src/test/resources/expectedInput.xml')
        assertThat actual.text,
                   is(equalTo(expected.text))
    }

    @Test
    void muleDeployProperties() {
        // arrange
        writeMuleDeployProps()

        // act
        SoapGenerator.generate(tempDir,
                               new File('src/test/resources/wsdl/input.wsdl'),
                               'v1',
                               'WeirdServiceName',
                               'WeirdPortName',
                               'theConfig')

        // assert
        def props = new Properties()
        def propsFile = new File(appDir, 'mule-deploy.properties')
        props.load(new FileInputStream(propsFile))
        assertThat props.getProperty('config.resources'),
                   is(equalTo('global.xml,input_v1.xml'))
    }

    private writeMuleDeployProps() {
        def props = new Properties()
        props.setProperty('config.resources', 'global.xml')
        def propsFile = new File(appDir, 'mule-deploy.properties')
        props.store(new FileOutputStream(propsFile), 'comments')
        [props, propsFile]
    }

}
