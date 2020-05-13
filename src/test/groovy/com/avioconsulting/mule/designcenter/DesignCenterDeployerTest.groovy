package com.avioconsulting.mule.designcenter

import groovy.json.JsonOutput
import groovy.test.GroovyAssert
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpServerRequest
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Before
import org.junit.Test

import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.equalTo
import static org.hamcrest.Matchers.is

class DesignCenterDeployerTest extends BaseTest {
    private DesignCenterDeployer deployer

    @Before
    void clean() {
        setupDeployer()
    }

    def setupDeployer() {
        deployer = new DesignCenterDeployer(clientWrapper,
                                            new TestLogger())
    }

    @Test
    void getDesignCenterProjectId_found() {
        // arrange
        String anypointOrgId = null
        String url = null
        String ownerGuid = null
        withHttpServer { HttpServerRequest request ->
            if (mockAuthenticationOk(request)) {
                return
            }
            anypointOrgId = request.getHeader('X-ORGANIZATION-ID')
            url = request.uri()
            ownerGuid = request.getHeader('X-OWNER-ID')
            request.response().with {
                statusCode = 200
                putHeader('Content-Type',
                          'application/json')
                end(JsonOutput.toJson([
                        [
                                id  : 'blah',
                                name: 'the project'
                        ],
                        [
                                id  : 'foo',
                                name: 'other project'
                        ]
                ]))
            }
        }

        // act
        def result = deployer.getDesignCenterProjectId('the project')

        // assert
        MatcherAssert.assertThat result,
                                 is(equalTo('blah'))
        MatcherAssert.assertThat url,
                                 is(equalTo('/designcenter/api-designer/projects'))
        MatcherAssert.assertThat anypointOrgId,
                                 is(equalTo('the-org-id'))
        MatcherAssert.assertThat 'Design center needs this',
                                 ownerGuid,
                                 is(equalTo('the_id'))
    }

    @Test
    void getDesignCenterProjectId_not_found() {
        // arrange
        withHttpServer { HttpServerRequest request ->
            if (mockAuthenticationOk(request)) {
                return
            }
            request.response().with {
                statusCode = 200
                putHeader('Content-Type',
                          'application/json')
                end(JsonOutput.toJson([
                        [
                                id  : 'blah',
                                name: 'the project'
                        ],
                        [
                                id  : 'foo',
                                name: 'other project'
                        ]
                ]))
            }
        }

        // act
        def exception = GroovyAssert.shouldFail {
            deployer.getDesignCenterProjectId('not found project')
        }

        // assert
        MatcherAssert.assertThat exception.message,
                                 is(Matchers.containsString("Unable to find ID for Design Center project 'not found project'"))
    }

    @Test
    void getExistingDesignCenterFiles() {
        // arrange
        List<String> urls = []
        withHttpServer { HttpServerRequest request ->
            if (mockAuthenticationOk(request)) {
                return
            }
            urls << request.uri()
            request.response().with {
                putHeader('Content-Type',
                          'application/json')
                if (request.absoluteURI().endsWith('archive')) {
                    def ant = new AntBuilder()
                    ant.zip(destfile: 'build/tmp/file.zip',
                            basedir: 'src/test/resources/samplezipdir')
                    statusCode = 200
                    end(Buffer.buffer(new File('build/tmp/file.zip').bytes))
                } else {
                    statusCode = 404
                    end('unknown')
                }
            }
        }

        // act
        def result = deployer.getExistingDesignCenterFiles('ourprojectId',
                                                           'master')

        // assert
        assertThat result,
                   is(equalTo([
                           new RamlFile('examples',
                                        null,
                                        'FOLDER'),
                           new RamlFile('howdy',
                                        null,
                                        'FOLDER'),
                           new RamlFile('examples/foo.raml',
                                        'the contents\n',
                                        'FILE'),
                           new RamlFile('stuff.raml',
                                        'the contents\n',
                                        'FILE')
                   ]))
        assertThat urls,
                   is(equalTo([
                           '/exchange/api/v1/organizations/the-org-id/projects/ourprojectId/refs/master/archive'
                   ]))
    }

    @Test
    void getExistingDesignCenterFiles_otherbranch() {
        // arrange
        List<String> urls = []
        withHttpServer { HttpServerRequest request ->
            if (mockAuthenticationOk(request)) {
                return
            }
            urls << request.uri()
            request.response().with {
                statusCode = 200
                putHeader('Content-Type',
                          'application/json')
                request.response().with {
                    putHeader('Content-Type',
                              'application/json')
                    if (request.absoluteURI().endsWith('archive')) {
                        def ant = new AntBuilder()
                        ant.zip(destfile: 'build/tmp/file.zip',
                                basedir: 'src/test/resources/samplezipdir')
                        statusCode = 200
                        end(Buffer.buffer(new File('build/tmp/file.zip').bytes))
                    } else {
                        statusCode = 404
                        end('unknown')
                    }
                }
            }
        }

        // act
        deployer.getExistingDesignCenterFiles('ourprojectId',
                                                           'otherbranch')

        // assert
        assertThat urls,
                   is(equalTo([
                           '/exchange/api/v1/organizations/the-org-id/projects/ourprojectId/refs/otherbranch/archive'
                   ]))
    }
}
