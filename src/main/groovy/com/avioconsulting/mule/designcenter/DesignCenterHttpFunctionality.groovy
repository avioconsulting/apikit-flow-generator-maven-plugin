package com.avioconsulting.mule.designcenter


import org.apache.http.client.methods.HttpUriRequest

trait DesignCenterHttpFunctionality {
    def executeDesignCenterRequest(HttpClientWrapper clientWrapper,
                                   HttpUriRequest request,
                                   String failureContext,
                                   Closure resultHandler = null) {
        request.with {
            setHeader('X-ORGANIZATION-ID',
                      clientWrapper.anypointOrganizationId)
            setHeader('cache-control',
                      'no-cache')
            setHeader('X-OWNER-ID',
                      clientWrapper.ownerGuid)
        }
        return clientWrapper.executeWithSuccessfulCloseableResponse(request,
                                                                    failureContext,
                                                                    resultHandler)
    }


}
