package com.avioconsulting.mule.resources

// would normally be in src/main/resources but Maven plugins do not resolve those resources
class SoapResources {
    static final String DOMAIN_TEMPLATE = '<?xml version="1.0" encoding="UTF-8"?>\n' +
            '<domain:mule-domain\n' +
            '        xmlns="http://www.mulesoft.org/schema/mule/core"\n' +
            '        xmlns:domain="http://www.mulesoft.org/schema/mule/ee/domain"\n' +
            '        xmlns:http="http://www.mulesoft.org/schema/mule/http"\n' +
            '        xmlns:tls="http://www.mulesoft.org/schema/mule/tls"\n' +
            '        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"\n' +
            '        xmlns:spring="http://www.springframework.org/schema/beans"\n' +
            '        xmlns:doc="http://www.mulesoft.org/schema/mule/documentation"\n' +
            '        xsi:schemaLocation="\n' +
            '               http://www.mulesoft.org/schema/mule/core http://www.mulesoft.org/schema/mule/core/current/mule.xsd\n' +
            '               http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans-current.xsd\n' +
            '               http://www.mulesoft.org/schema/mule/http http://www.mulesoft.org/schema/mule/http/current/mule-http.xsd\n' +
            '               http://www.mulesoft.org/schema/mule/tls http://www.mulesoft.org/schema/mule/tls/current/mule-tls.xsd\n' +
            '               http://www.mulesoft.org/schema/mule/ee/domain http://www.mulesoft.org/schema/mule/ee/domain/current/mule-domain-ee.xsd">\n' +
            '    <http:listener-config name="OUR_LISTENER_CONFIG" protocol="HTTPS" host="0.0.0.0" port="8443" doc:name="HTTP Listener Configuration">\n' +
            '        <tls:context>\n' +
            '            <tls:key-store type="jks"\n' +
            '                           path="some.jks"\n' +
            '                           keyPassword="selfSignedKeyStorePassword"\n' +
            '                           password="selfSignedKeyStorePassword"/>\n' +
            '        </tls:context>\n' +
            '    </http:listener-config>\n' +
            '</domain:mule-domain>'

    static final String SOAP_TEMPLATE = '<mule xmlns="http://www.mulesoft.org/schema/mule/core" xmlns:apikit-soap="http://www.mulesoft.org/schema/mule/apikit-soap" xmlns:doc="http://www.mulesoft.org/schema/mule/documentation" xmlns:http="http://www.mulesoft.org/schema/mule/http" xmlns:spring="http://www.springframework.org/schema/beans" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://www.mulesoft.org/schema/mule/core http://www.mulesoft.org/schema/mule/core/current/mule.xsd\n' +
            'http://www.mulesoft.org/schema/mule/http http://www.mulesoft.org/schema/mule/http/current/mule-http.xsd\n' +
            'http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans-3.1.xsd\n' +
            'http://www.mulesoft.org/schema/mule/apikit-soap http://www.mulesoft.org/schema/mule/apikit-soap/current/mule-apikit-soap.xsd">\n' +
            '</mule>'
}
