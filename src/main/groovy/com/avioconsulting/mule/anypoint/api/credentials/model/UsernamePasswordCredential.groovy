package com.avioconsulting.mule.anypoint.api.credentials.model

import groovy.transform.Immutable;


/**
 * Basic username and password credentials for accessing Anypoint Platform.
 */
@Immutable
class UsernamePasswordCredential extends Credential {

    /**
     * Anypoint Username
     */
    final String username;

    /**
     * Anypoint Password
     */
    final String password;

    @Override
    String getPrincipal() {
        return username
    }
}