package com.avioconsulting.mule.anypoint.api.credentials.model;

/**
 * Abstract Base class for defining anypoint platform access credentials
 */
abstract class Credential {

    abstract String getPrincipal()
}