package com.avioconsulting.mule

import org.apache.commons.io.FileUtils
import org.apache.maven.project.MavenProject
import org.junit.Before
import org.junit.Test

import static groovy.test.GroovyAssert.shouldFail
import static org.hamcrest.Matchers.*
import static org.junit.Assert.assertThat

class SoapGeneratorTest implements FileUtil {
    private File tempDir, appDir, mainDir, wsdlDir, newWsdlPath

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
        wsdlDir = join mainDir,
                       'resources',
                       'api'
        wsdlDir.mkdirs()
        FileUtils.copyFileToDirectory(new File('src/test/resources/wsdl/input.wsdl'),
                                      wsdlDir)
        FileUtils.copyFileToDirectory(new File('src/test/resources/wsdl/schema.xsd'),
                                      wsdlDir)
        newWsdlPath = join wsdlDir,
                           'input.wsdl'
    }

    @Test
    void newFile_explicit_svc() {
        // arrange

        // act
        SoapGenerator.generate(tempDir,
                               newWsdlPath,
                               'foobar',
                               'v1',
                               'theConfig',
                               'WeirdServiceName',
                               'WeirdPortName',
                               true)

        // assert
        def actual = new File(appDir,
                              'input_v1.xml')
        assert actual.exists()
        def expected = new File('src/test/resources/expectedInput.xml')
        assertThat actual.text.replace('\r',
                                       ''),
                   is(equalTo(expected.text))
    }

    @Test
    void newFile_noApiName_In_Listener() {
        // arrange

        // act
        SoapGenerator.generate(tempDir,
                               newWsdlPath,
                               'foobar',
                               'v1',
                               'theConfig',
                               'WeirdServiceName',
                               'WeirdPortName',
                               false)

        // assert
        def actual = new File(appDir,
                              'input_v1.xml')
        assert actual.exists()
        def expected = new File('src/test/resources/expectedInput_No_ApiNameInPath.xml')
        assertThat actual.text.replace('\r',
                                       ''),
                   is(equalTo(expected.text.trim()))
    }

    @Test
    void newFile_explicit_svc_insert_xml() {
        // arrange

        // act
        SoapGenerator.generate(tempDir,
                               newWsdlPath,
                               'foobar',
                               'v1',
                               'theConfig',
                               'WeirdServiceName',
                               'WeirdPortName',
                               true,
                               '<foobar/>',)

        // assert
        def actual = new File(appDir,
                              'input_v1.xml')
        assert actual.exists()
        def expected = new File('src/test/resources/expectedInput_insertBeforeRouter.xml')
        assertThat actual.text.replace('\r',
                                       ''),
                   is(equalTo(expected.text))
    }

    @Test
    void existing() {
        // arrange
        // do our first generation, then we'll do it again and results should be the same
        SoapGenerator.generate(tempDir,
                               newWsdlPath,
                               'foobar',
                               'v1',
                               'theConfig',
                               'WeirdServiceName',
                               'WeirdPortName',
                               true,
                               '<foobar/>')

        // act
        def exception = shouldFail {
            SoapGenerator.generate(tempDir,
                                   newWsdlPath,
                                   'foobar',
                                   'v1',
                                   'theConfig',
                                   'WeirdServiceName',
                                   'WeirdPortName',
                                   true,
                                   '<foobar/>')
        }

        // assert
        assertThat exception.message,
                   is(containsString('You can only use this plugin to do the initial generation of flows from WSDL. Use Studio to perform updates!'))
    }

    @Test
    void via_mojo_implicit() {
        // arrange
        def mojo = new SoapGenerateMojo().with {
            it.apiVersion = 'v1'
            it.apiName = 'foobar'
            it.wsdlPath = newWsdlPath
            it.httpListenerConfigName = 'theConfig'
            it.insertApiNameInListenerPath = true
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
        def actual = new File(appDir,
                              'input_v1.xml')
        assert actual.exists()
        def expected = new File('src/test/resources/expectedInput.xml')
        assertThat actual.text.replace('\r',
                                       ''),
                   is(equalTo(expected.text))
    }
}
