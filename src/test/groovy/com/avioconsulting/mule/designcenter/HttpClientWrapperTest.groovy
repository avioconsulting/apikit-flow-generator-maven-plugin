package com.avioconsulting.mule.designcenter

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.test.GroovyAssert
import io.vertx.core.http.HttpServerRequest
import org.apache.http.client.methods.HttpGet
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Test

import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.*

class HttpClientWrapperTest extends BaseTest {
    @Test
    void authenticate_correct_request() {
        // arrange
        String url = null
        String method = null
        String contentType = null
        Map sentJson = null
        String authHeader = null
        withHttpServer { HttpServerRequest request ->
            def payload = null
            if (request.uri() == '/accounts/login') {
                url = request.uri()
                method = request.method().name()
                contentType = request.getHeader('Content-Type')
                authHeader = request.getHeader('Authorization')
                request.bodyHandler { body ->
                    sentJson = new JsonSlurper().parseText(body.toString())
                }
                payload = [
                        access_token: 'the token'
                ]
            } else if (request.uri() == '/accounts/api/me') {
                payload = [
                        user: [
                                id                   : 'the_id',
                                username             : 'the_username',
                                memberOfOrganizations: [
                                        [
                                                name: 'the-org-name',
                                                id  : 'the-org-id'
                                        ]
                                ]
                        ]
                ]
            }
            request.response().with {
                statusCode = 200
                putHeader('Content-Type',
                          'application/json')
                end(JsonOutput.toJson(payload))
            }
        }

        // act
        clientWrapper.authenticate()

        // assert
        MatcherAssert.assertThat url,
                                 is(equalTo('/accounts/login'))
        MatcherAssert.assertThat method,
                                 is(equalTo('POST'))
        MatcherAssert.assertThat contentType,
                                 is(equalTo('application/json'))
        assertThat sentJson,
                   is(equalTo([
                           username: 'the user',
                           password: 'the password'
                   ]))
        MatcherAssert.assertThat 'We have not authenticated yet',
                                 authHeader,
                                 is(nullValue())
    }

    @Test
    void authenticate_succeeds() {
        // arrange
        def tokenOnNextRequest = null
        withHttpServer { HttpServerRequest request ->
            request.response().with {
                if (request.uri() != '/accounts/login') {
                    tokenOnNextRequest = request.getHeader('Authorization')
                }
                statusCode = 200
                putHeader('Content-Type',
                          'application/json')
                Map jsonPayload = null
                if (request.uri() == '/accounts/api/me') {
                    jsonPayload = [
                            user: [
                                    id                   : 'the_id',
                                    username             : 'the_username',
                                    memberOfOrganizations: [
                                            [
                                                    name: 'the-org-name',
                                                    id  : 'the-org-id'
                                            ]
                                    ]
                            ]
                    ]
                } else {
                    jsonPayload = [
                            access_token: 'the token'
                    ]
                }
                end(JsonOutput.toJson(jsonPayload))
            }
        }

        // act
        clientWrapper.authenticate()

        // assert
        MatcherAssert.assertThat tokenOnNextRequest,
                                 is(equalTo('Bearer the token'))
        MatcherAssert.assertThat 'Some APIs like Design Center need this',
                                 clientWrapper.ownerGuid,
                                 is(equalTo('the_id'))
    }

    @Test
    void only_one_auth_required() {
        // arrange
        def tokenFetches = 0
        withHttpServer { HttpServerRequest request ->
            request.response().with {
                if (request.uri() == '/accounts/login') {
                    tokenFetches++
                }
                if (mockAuthenticationOk(request)) {
                    return
                }
                statusCode = 200
                putHeader('Content-Type',
                          'application/json')
                end(JsonOutput.toJson([
                        access_token: 'the token'
                ]))
            }
        }

        // act
        def response = clientWrapper.execute(new HttpGet("${clientWrapper.baseUrl}/foobar"))
        response.close()
        response = clientWrapper.execute(new HttpGet("${clientWrapper.baseUrl}/foobar"))
        response.close()

        // assert
        assertThat tokenFetches,
                   is(equalTo(1))
    }

    @Test
    void authenticate_fails() {
        // arrange
        withHttpServer { HttpServerRequest request ->
            request.response().with {
                statusCode = 401
                end('Unauthorized')
            }
        }

        // act
        def exception = GroovyAssert.shouldFail {
            clientWrapper.authenticate()
        }

        // assert
        MatcherAssert.assertThat exception.message,
                                 is(equalTo("Unable to authenticate to Anypoint as 'the user', got an HTTP 401 with a response of 'Unauthorized'".toString()))
    }

    @Test
    void no_cookies() {
        // arrange
        String cookieValue = null
        withHttpServer { HttpServerRequest request ->
            if (mockAuthenticationOk(request)) {
                return
            }
            cookieValue = request.getCookie('FOO')?.value
            request.response().with {
                statusCode = 200
                putHeader('Set-Cookie',
                          'FOO=BAR')
                end('Howdy')
            }
        }

        // act
        // server tries to set the cookie
        clientWrapper.execute(new HttpGet("${clientWrapper.baseUrl}/hello")).withCloseable {}
        // send another request that should not have the cookie
        clientWrapper.execute(new HttpGet("${clientWrapper.baseUrl}/hello")).withCloseable {}

        // assert
        MatcherAssert.assertThat cookieValue,
                                 is(nullValue())
    }

    @Test
    void getAnypointOrganizationId_default_org_prefs_exist() {
        // arrange
        clientWrapper = new HttpClientWrapper("http://localhost:${httpServer.actualPort()}",
                                              'the user',
                                              'the password',
                                              new TestLogger())
        withHttpServer { HttpServerRequest request ->
            request.response().with {
                statusCode = 200
                putHeader('Content-Type',
                          'application/json')
                Map jsonPayload
                if (request.uri() == '/accounts/api/me') {
                    jsonPayload = [
                            user: [
                                    id                     : 'the_id',
                                    username               : 'the_username',
                                    organizationPreferences: [
                                            'the-org-id-2': [
                                                    defaultEnvironment: 'the_id'
                                            ]
                                    ],
                                    memberOfOrganizations  : [
                                            [
                                                    name: 'the-org-name-2',
                                                    id  : 'the-org-id-2'
                                            ]
                                    ]
                            ]
                    ]
                } else {
                    jsonPayload = [
                            access_token: 'the token'
                    ]
                }
                end(JsonOutput.toJson(jsonPayload))
            }
        }
        clientWrapper.authenticate()

        // act
        def result = clientWrapper.anypointOrganizationId

        // assert
        MatcherAssert.assertThat result,
                                 is(equalTo('the-org-id-2'))
    }

    @Test
    void getAnypointOrganizationId_default_no_org_prefs_exist() {
        // arrange
        clientWrapper = new HttpClientWrapper("http://localhost:${httpServer.actualPort()}",
                                              'the user',
                                              'the password',
                                              new TestLogger())
        withHttpServer { HttpServerRequest request ->
            request.response().with {
                statusCode = 200
                putHeader('Content-Type',
                          'application/json')
                Map jsonPayload
                if (request.uri() == '/accounts/api/me') {
                    jsonPayload = [
                            user: [
                                    id                     : 'the_id',
                                    username               : 'the_username',
                                    organizationPreferences: [:],
                                    memberOfOrganizations  : [
                                            [
                                                    name: 'the-org-name-2',
                                                    id  : 'the-org-id-2'
                                            ]
                                    ]
                            ]
                    ]
                } else {
                    jsonPayload = [
                            access_token: 'the token'
                    ]
                }
                end(JsonOutput.toJson(jsonPayload))
            }
        }
        clientWrapper.authenticate()

        // act
        def result = clientWrapper.anypointOrganizationId

        // assert
        MatcherAssert.assertThat result,
                                 is(equalTo('the-org-id-2'))
    }

    @Test
    void getAnypointOrganizationId_specified_org_does_not_exist() {
        // arrange
        withHttpServer { HttpServerRequest request ->
            request.response().with {
                statusCode = 200
                putHeader('Content-Type',
                          'application/json')
                Map jsonPayload
                if (request.uri() == '/accounts/api/me') {
                    jsonPayload = [
                            user: [
                                    id                   : 'the_id',
                                    username             : 'the_username',
                                    memberOfOrganizations: [
                                            [
                                                    name: 'the-other-org-name',
                                                    id  : 'the-org-id'
                                            ]
                                    ]
                            ]
                    ]
                } else {
                    jsonPayload = [
                            access_token: 'the token'
                    ]
                }
                end(JsonOutput.toJson(jsonPayload))
            }
        }

        // act
        def exception = GroovyAssert.shouldFail {
            clientWrapper.authenticate()
        }

        // assert
        MatcherAssert.assertThat exception.message,
                                 is(Matchers.containsString("You specified Anypoint organization 'the-org-name' but that organization was not found. Options are [the-other-org-name]"))
    }

    @Test
    void getAnypointOrganizationId_specified_org_does_exist() {
        // arrange
        withHttpServer { HttpServerRequest request ->
            request.response().with {
                statusCode = 200
                putHeader('Content-Type',
                          'application/json')
                Map jsonPayload
                if (request.uri() == '/accounts/api/me') {
                    jsonPayload = [
                            user: [
                                    id                   : 'the_id',
                                    username             : 'the_username',
                                    memberOfOrganizations: [
                                            [
                                                    name: 'the-org-name',
                                                    id  : 'the-org-id'
                                            ]
                                    ]
                            ]
                    ]
                } else {
                    jsonPayload = [
                            access_token: 'the token'
                    ]
                }
                end(JsonOutput.toJson(jsonPayload))
            }
        }
        clientWrapper.authenticate()

        // act
        def result = clientWrapper.anypointOrganizationId

        // assert
        MatcherAssert.assertThat result,
                                 is(equalTo('the-org-id'))
    }
}
