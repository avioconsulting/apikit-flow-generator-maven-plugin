package com.avioconsulting.mule.designcenter.api.models.credentials

import groovy.transform.Immutable

/**
 * Connected App credentials for accessing Anypoint Platform.
 */
@Immutable
class ConnectedAppCredential extends Credential {

    /**
     * Connected App Id
     */
    final String id;

    /**
     * Connected App Secret
     */
    final String secret;

    @Override
    String getPrincipal() {
        return id
    }
}