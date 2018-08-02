package com.avioconsulting.mule

import org.apache.commons.io.FileUtils
import org.apache.maven.project.MavenProject
import org.junit.Before
import org.junit.Test

import static org.hamcrest.Matchers.equalTo
import static org.hamcrest.Matchers.is
import static org.junit.Assert.assertThat

class SoapGeneratorTest implements FileUtil {
    private File tempDir, appDir, mainDir, wsdlDir, newWsdlPath

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
        wsdlDir = join mainDir, 'wsdl'
        wsdlDir.mkdirs()
        FileUtils.copyFileToDirectory(new File('src/test/resources/wsdl/input.wsdl'),
                                      wsdlDir)
        FileUtils.copyFileToDirectory(new File('src/test/resources/wsdl/schema.xsd'),
                                      wsdlDir)
        newWsdlPath = join wsdlDir, 'input.wsdl'
    }

    @Test
    void newFile_explicit_svc() {
        // arrange
        writeMuleDeployProps()

        // act
        SoapGenerator.generate(tempDir,
                               newWsdlPath,
                               'v1',
                               'theConfig',
                               'WeirdServiceName',
                               'WeirdPortName')

        // assert
        def actual = new File(appDir, 'input_v1.xml')
        assert actual.exists()
        def expected = new File('src/test/resources/expectedInput.xml')
        assertThat actual.text,
                   is(equalTo(expected.text))
    }

    @Test
    void newFile_explicit_svc_insert_xml() {
        // arrange
        writeMuleDeployProps()

        // act
        SoapGenerator.generate(tempDir,
                               newWsdlPath,
                               'v1',
                               'theConfig',
                               'WeirdServiceName',
                               'WeirdPortName',
                               '<foobar/>')

        // assert
        def actual = new File(appDir, 'input_v1.xml')
        assert actual.exists()
        def expected = new File('src/test/resources/expectedInput_insertBeforeRouter.xml')
        assertThat actual.text,
                   is(equalTo(expected.text))
    }

    @Test
    void via_mojo_implicit() {
        // arrange
        writeMuleDeployProps()
        def mojo = new SoapGenerateMojo().with {
            it.apiVersion = 'v1'
            it.wsdlPath = newWsdlPath
            it.httpListenerConfigName = 'theConfig'
            it.mavenProject = [
                    getBasedir: {
                        tempDir
                    }
            ] as MavenProject
            it
        }

        // act
        mojo.execute()

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
                               newWsdlPath,
                               'v1',
                               'theConfig',
                               'WeirdServiceName',
                               'WeirdPortName')

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
