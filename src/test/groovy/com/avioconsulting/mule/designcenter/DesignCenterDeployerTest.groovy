package com.avioconsulting.mule.designcenter


import groovy.json.JsonOutput
import groovy.test.GroovyAssert
import io.vertx.core.http.HttpMethod
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
        String anypointOrgId = null
        List<String> urls = []
        String ownerGuid = null
        withHttpServer { HttpServerRequest request ->
            if (mockAuthenticationOk(request)) {
                return
            }
            anypointOrgId = request.getHeader('X-ORGANIZATION-ID')
            urls << request.uri()
            ownerGuid = request.getHeader('X-OWNER-ID')
            request.response().with {
                statusCode = 200
                putHeader('Content-Type',
                          'application/json')
                def jsonResult
                if (request.absoluteURI().endsWith('files')) {
                    jsonResult = [
                            [
                                    path: '.gitignore',
                                    type: 'FILE'
                            ],
                            [
                                    path: 'stuff.raml',
                                    type: 'FILE'
                            ],
                            [
                                    path: 'examples/foo.raml',
                                    type: 'FILE'
                            ],
                            [
                                    path: 'howdy',
                                    type: 'FOLDER'
                            ]
                    ]
                } else {
                    jsonResult = 'the contents'
                }
                end(JsonOutput.toJson(jsonResult))
            }
        }

        // act
        def result = deployer.getExistingDesignCenterFiles('ourprojectId')

        // assert
        assertThat result,
                   is(equalTo([
                           new RamlFile('stuff.raml',
                                        'the contents',
                                        'FILE'),
                           new RamlFile('examples/foo.raml',
                                        'the contents',
                                        'FILE'),
                           new RamlFile('howdy',
                                        null,
                                        'FOLDER')
                   ]))
        assertThat urls,
                   is(equalTo([
                           '/designcenter/api-designer/projects/ourprojectId/branches/master/files',
                           '/designcenter/api-designer/projects/ourprojectId/branches/master/files/stuff.raml',
                           '/designcenter/api-designer/projects/ourprojectId/branches/master/files/examples%2Ffoo.raml'
                   ]))
        MatcherAssert.assertThat anypointOrgId,
                                 is(equalTo('the-org-id'))
        MatcherAssert.assertThat 'Design center needs this',
                                 ownerGuid,
                                 is(equalTo('the_id'))
    }

    static def mockDesignCenterProjectId(HttpServerRequest request,
                                         String projectName,
                                         String projectId) {
        def mocked = false
        if (request.uri() == '/designcenter/api-designer/projects') {
            mocked = true
            request.response().with {
                statusCode = 200
                putHeader('Content-Type',
                          'application/json')
                end(JsonOutput.toJson([
                        [
                                id  : projectId,
                                name: projectName
                        ]
                ]))
            }
        }
        mocked
    }

    static def mockAcquireLock(HttpServerRequest request,
                               String projectId) {
        def mocked = false
        if (request.uri() == "/designcenter/api-designer/projects/${projectId}/branches/master/acquireLock"
                && request.method() == HttpMethod.POST) {
            mocked = true
            request.response().with {
                statusCode = 200
                end()
            }
        }
        mocked
    }

    static def mockReleaseLock(HttpServerRequest request,
                               String projectId) {
        def mocked = false
        if (request.uri() == "/designcenter/api-designer/projects/${projectId}/branches/master/releaseLock"
                && request.method() == HttpMethod.POST) {
            mocked = true
            request.response().with {
                statusCode = 200
                end()
            }
        }
        mocked
    }

    static def mockGetExistingFiles(HttpServerRequest request,
                                    String projectId,
                                    Map<String, String> filesAndContents) {
        def mocked = false
        def filenames = filesAndContents.keySet()
        if (request.uri() == "/designcenter/api-designer/projects/${projectId}/branches/master/files") {
            mocked = true
            def responsePayload = filenames.collect { fileName ->
                [
                        path: fileName,
                        type: 'FILE'
                ]
            }
            request.response().with {
                statusCode = 200
                putHeader('Content-Type',
                          'application/json')
                end(JsonOutput.toJson(responsePayload))
            }
        }
        filenames.each { fileName ->
            if (request.uri() == "/designcenter/api-designer/projects/${projectId}/branches/master/files/${fileName}" &&
                    request.method() == HttpMethod.GET) {
                mocked = true
                request.response().with {
                    statusCode = 200
                    putHeader('Content-Type',
                              'application/json')
                    end(JsonOutput.toJson(filesAndContents[fileName]))
                }
            }
        }
        mocked
    }

    static def mockDeleteFile(HttpServerRequest request,
                              String projectId,
                              String fileName) {
        def mocked = false
        if (request.uri() == "/designcenter/api-designer/projects/${projectId}/branches/master/files/${fileName}"
                && request.method() == HttpMethod.DELETE) {
            mocked = true
            request.response().with {
                statusCode = 204
                end()
            }
        }
        mocked
    }
}
