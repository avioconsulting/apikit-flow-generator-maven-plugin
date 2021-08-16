package com.avioconsulting.mule.designcenter

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.apache.http.HttpException
import org.apache.http.HttpRequest
import org.apache.http.HttpRequestInterceptor
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.protocol.HttpContext
import org.apache.maven.plugin.logging.Log

class HttpClientWrapper implements HttpRequestInterceptor {
    private final String username
    private final String password
    private String accessToken
    private String ownerGuid
    private final Log logger
    final String baseUrl
    private final CloseableHttpClient httpClient
    private final String anypointOrganizationName
    private String anypointOrganizationId

    HttpClientWrapper(String baseUrl,
                      String username,
                      String password,
                      Log logger,
                      String anypointOrganizationName = null) {
        this.anypointOrganizationName = anypointOrganizationName
        this.password = password
        this.username = username
        this.logger = logger
        this.baseUrl = baseUrl
        this.httpClient = HttpClients.custom()
                .addInterceptorFirst(this)
                .disableCookieManagement()
                .build()
    }

    String getAnypointOrganizationId() {
        // might need to auth to get this
        authenticate()
        return anypointOrganizationId
    }

    private def authenticate() {
        if (!this.accessToken) {
            fetchAccessToken()
            fetchUserInfo()
        }
    }

    private def fetchUserInfo() {
        logger.println('Fetching user information')
        def request = new HttpGet("${baseUrl}/accounts/api/me")
        executeWithSuccessfulCloseableResponse(request,
                                               'fetch user info') { result ->
            def user = result.user
            this.ownerGuid = user.id
            def memberOrgs = user.memberOfOrganizations
            if (!this.anypointOrganizationName) {
                def organizationPreferences = user.organizationPreferences
                if (organizationPreferences) {
                    this.anypointOrganizationId = organizationPreferences.keySet().first()
                } else {
                    assert memberOrgs.size() >= 1: "Expected at least 1 member org in ${user}!"
                    this.anypointOrganizationId = memberOrgs.first().id
                }
                def name = memberOrgs.find { org ->
                    org.id == this.anypointOrganizationId
                }?.name
                if (name) {
                    logger.println("Using default organization for ${username} of '${name}'")
                } else {
                    throw new Exception('No Anypoint org was specified and was unable to find a default one! This should not happen!')
                }
            } else {
                this.anypointOrganizationId = memberOrgs.find { org ->
                    org.name == this.anypointOrganizationName
                }?.id
                if (!this.anypointOrganizationId) {
                    def options = memberOrgs.collect { org -> org.name }
                    throw new Exception("You specified Anypoint organization '${this.anypointOrganizationName}' but that organization was not found. Options are ${options}")
                }
            }
        }
    }

    private def fetchAccessToken() {
        logger.println "Authenticating to Anypoint as user '${username}'"
        def payload = [
                username: username,
                password: password
        ]
        def request = new HttpPost("${baseUrl}/accounts/login").with {
            setEntity(new StringEntity(JsonOutput.toJson(payload)))
            addHeader('Content-Type',
                      'application/json')
            it
        }
        httpClient.execute(request).with { response ->
            def result = assertSuccessfulResponseAndReturnJson(response,
                                                               "authenticate to Anypoint as '${username}'")
            if (result.url && responseHasCookie(response, "mulesoft.vaas.sess")) {
                throw new Exception("Unable to authenticate to Anypoint as '${username}'. User requires multi-factored authentication.")
            }
            logger.println 'Successfully authenticated'
            accessToken = result.access_token
        }
    }
    private def responseHasCookie(CloseableHttpResponse response, String name){
        def hasCookie = response.getHeaders("Set-Cookie").any {h -> h.value.split("=")[0].equalsIgnoreCase(name)}
        hasCookie
    }
    static def assertSuccessfulResponse(CloseableHttpResponse response,
                                        String failureContext) {
        def status = response.statusLine.statusCode
        if (status < 200 || status > 299) {
            throw new Exception("Unable to ${failureContext}, got an HTTP ${status} with a response of '${response.entity.content.text}'")
        }
    }

    static def assertSuccessfulResponseAndReturnJson(CloseableHttpResponse response,
                                                     String failureContext) {
        assertSuccessfulResponse(response,
                                 failureContext)
        def contentType = response.getFirstHeader('Content-Type')
        assert contentType?.value?.contains('application/json'): "Expected a JSON response but got ${contentType}!"
        new JsonSlurper().parse(response.entity.content)
    }

    def executeWithSuccessfulCloseableResponse(HttpUriRequest request,
                                               String failureContext,
                                               Closure closure = null) {
        execute(request).withCloseable { response ->
            if (closure) {
                def parsed = assertSuccessfulResponseAndReturnJson(response,
                                                                   failureContext)
                return closure(parsed)
            }
            assertSuccessfulResponse(response,
                                     failureContext)
        }
    }

    def close() {
        httpClient.close()
    }

    CloseableHttpResponse execute(HttpUriRequest request) {
        if (!accessToken) {
            authenticate()
        }
        httpClient.execute(request)
    }

    String getOwnerGuid() {
        // need to auth to get this
        authenticate()
        this.ownerGuid
    }

    @Override
    void process(HttpRequest httpRequest,
                 HttpContext httpContext) throws HttpException, IOException {
        if (accessToken) {
            httpRequest.setHeader('Authorization',
                                  "Bearer ${accessToken}")
        }
    }
}
