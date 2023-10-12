package com.avioconsulting.mule.designcenter

import com.avioconsulting.mule.designcenter.api.models.credentials.ConnectedAppCredential
import com.avioconsulting.mule.designcenter.api.models.credentials.UsernamePasswordCredential
import groovy.json.JsonOutput
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServer
import io.vertx.core.http.HttpServerRequest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TestRule
import org.junit.rules.TestWatcher
import org.junit.runner.Description

import java.util.concurrent.CompletableFuture

class BaseTest {
    protected HttpServer httpServer
    protected HttpClientWrapper clientWrapper
    protected HttpClientWrapper clientWrapperConnectedApp
    protected Handler<HttpServerRequest> closure

    @Rule
    public TestRule watcher = new TestWatcher() {
        @Override
        protected void starting(Description description) {
            println "*** Starting test ${description.methodName} ***"
        }

        @Override
        protected void finished(Description description) {
            println "*** Finishing test ${description.methodName} ***"
        }
    }

    @Before
    void startServer() {
        httpServer = createServer()
        closure = null
        clientWrapper = new HttpClientWrapper("http://localhost:${httpServer.actualPort()}",
                                              new UsernamePasswordCredential('the user',
                                              'the password'),
                                              new TestLogger(),
                                              'the-org-name')
        clientWrapperConnectedApp = new HttpClientWrapper("http://localhost:${httpServer.actualPort()}",
                new ConnectedAppCredential(
                'the client',
                'the secret'),
                new TestLogger(),
                'the-org-name')
    }

    @After
    void stopServer() {
        try {
            if (clientWrapper) {
                clientWrapper.close()
                clientWrapper = null
            }
        }
        catch (e) {
            println "could not close ${e}"
        }
        if (!httpServer) {
            return
        }
        try {
            def future = new CompletableFuture()
            println 'Closing mock web server'
            // closing is async
            httpServer.close {
                future.complete('done')
            }
            println 'Waiting for web server to close'
            future.get()
            println 'Web server closed'
            httpServer = null
        }
        catch (e) {
            println "could not close ${e}"
        }
    }

    def withHttpServer(Handler<HttpServerRequest> closure) {
        this.closure = closure
    }

    HttpServer createServer() {
        // lets us switch our handler in each test
        def closureForClosure = { HttpServerRequest request ->
            if (!closure) {
                request.response().with {
                    statusCode = 500
                    end('You did not set this.closure in your test!')
                }
                return
            }
            closure.handle(request)
        }
        def future = new CompletableFuture()
        // 0 means any available port
        def server = Vertx.vertx()
                .createHttpServer()
                .requestHandler(closureForClosure)
                .listen(0,
                        {
                            // listen is an async operation but we can/will block until it's up
                            future.complete('done')
                        })
        println "Waiting for server to come up"
        future.get()
        def port = server.actualPort()
        println "Server is supposedly up on port ${port}"
        server
    }

    def mockAuthenticationOk(HttpServerRequest request) {
        def mocked = false
        if (request.uri() == '/accounts/login') {
            def response = request.response()
            response.statusCode = 200
            response.putHeader('Content-Type',
                               'application/json')
            response.end(JsonOutput.toJson([
                    access_token: 'the token'
            ]))
            mocked = true
        } else if (request.uri() == '/accounts/api/me') {
            def response = request.response()
            response.statusCode = 200
            response.putHeader('Content-Type',
                               'application/json')
            response.end(JsonOutput.toJson([
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
            ]))
            mocked = true
        }
        return mocked
    }

    List<String> capturedStandardHeaders(HttpServerRequest request) {
        ['Authorization',
         'X-ANYPNT-ORG-ID',
         'X-ANYPNT-ENV-ID'].collect { header ->
            request.getHeader(header)
        }
    }
}
