package com.avioconsulting.mule.designcenter.api.models.credentials;

/**
 * Abstract Base class for defining anypoint platform access credentials
 */
abstract class Credential {

    abstract String getPrincipal()
}