package com.avioconsulting.mule.designcenter


import org.apache.http.client.methods.HttpPost
import org.apache.maven.plugin.logging.Log

class DesignCenterLock implements Closeable, DesignCenterHttpFunctionality {
    private final HttpClientWrapper clientWrapper
    private final Log logger
    private final String projectId
    private final String masterUrl

    DesignCenterLock(HttpClientWrapper clientWrapper,
                     Log logger,
                     String projectId) {
        this.projectId = projectId
        this.logger = logger
        this.clientWrapper = clientWrapper
        masterUrl = getMasterUrl(clientWrapper,
                                 projectId)
        logger.println 'Acquiring Design Center Lock'
        executeDesignCenterRequest(clientWrapper,
                                   new HttpPost("${masterUrl}/acquireLock"),
                                   'Acquire design center lock')
    }

    @Override
    void close() throws IOException {
        logger.println 'Releasing Design Center Lock'
        executeDesignCenterRequest(clientWrapper,
                                   new HttpPost("${masterUrl}/releaseLock"),
                                   'Release design center lock')
    }
}
