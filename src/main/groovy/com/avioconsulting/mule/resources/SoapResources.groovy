package com.avioconsulting.mule.resources

// would normally be in src/main/resources but Maven plugins do not resolve those resources
class SoapResources {
    static final String HEADER = '<?xml version="1.0" encoding="UTF-8" standalone="no"?>\n' +
            '<mule xmlns="http://www.mulesoft.org/schema/mule/core" xmlns:apikit-soap="http://www.mulesoft.org/schema/mule/apikit-soap" xmlns:doc="http://www.mulesoft.org/schema/mule/documentation" xmlns:ee="http://www.mulesoft.org/schema/mule/ee/core" xmlns:http="http://www.mulesoft.org/schema/mule/http" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://www.mulesoft.org/schema/mule/core http://www.mulesoft.org/schema/mule/core/current/mule.xsd http://www.mulesoft.org/schema/mule/http http://www.mulesoft.org/schema/mule/http/current/mule-http.xsd http://www.mulesoft.org/schema/mule/apikit-soap http://www.mulesoft.org/schema/mule/apikit-soap/current/mule-apikit-soap.xsd http://www.mulesoft.org/schema/mule/ee/core http://www.mulesoft.org/schema/mule/ee/core/current/mule-ee.xsd">'

    static final String MAIN_FLOW = '<flow name="api-main">\n' +
            '        <http:listener config-ref="THE_LISTENER_CONFIG" path="THE_LISTENER_PATH">\n' +
            '            <http:response statusCode="#[vars.httpStatus default 200]">\n' +
            '                <http:body>#[payload]</http:body>\n' +
            '                <http:headers>#[attributes.protocolHeaders default {}]</http:headers>\n' +
            '            </http:response>\n' +
            '            <http:error-response>\n' +
            '                <http:body>#[payload]</http:body>\n' +
            '                <http:headers>#[attributes.protocolHeaders default {}]</http:headers>\n' +
            '            </http:error-response>\n' +
            '        </http:listener>\n' +
            '        <apikit-soap:router config-ref="soapkit-config">\n' +
            '            <apikit-soap:message>#[payload]</apikit-soap:message>\n' +
            '            <apikit-soap:attributes>#[\n' +
            '              %dw 2.0\n' +
            '              output application/java\n' +
            '              ---\n' +
            '              {\n' +
            '                  headers: attributes.headers,\n' +
            '                  method: attributes.method,\n' +
            '                  queryString: attributes.queryString\n' +
            '            }]</apikit-soap:attributes>\n' +
            '        </apikit-soap:router>\n' +
            '        <ee:transform doc:name="Fault Detect">\n' +
            '           <ee:variables>\n' +
            '              <ee:set-variable variableName="httpStatus" ><![CDATA[%dw 2.0\n'+
            'output application/java\n' +
            '---\n' +
            '// Mule 4 SOAP services seem to mostly handle SOAP faults OK but they are not returning a 500 when they return a fault\n' +
            'if (payload.soap#Envelope.soap#Body.soap#Fault != null) 500 else 200]]></ee:set-variable>\n' +
            '           </ee:variables>\n' +
            '        </ee:transform>\n' +
            '    </flow>'

    static final String APIKIT_CONFIG = '<apikit-soap:config name="soapkit-config" port="PORT_NAME" service="SERVICE_NAME" wsdlLocation="WSDL_LOCATION" inboundValidationEnabled="${validate.soap.requests}"/>'

    static final String OPERATION_TEMPLATE = '<flow name="OPERATION_NAME:\\soapkit-config">\n' +
            '        <ee:transform>\n' +
            '            <ee:message>\n' +
            '                <ee:set-payload><![CDATA[%dw 2.0\n' +
            'output application/java\n' +
            'ns soap http://schemas.xmlsoap.org/soap/envelope\n' +
            '---\n' +
            '{\n' +
            '    body: {\n' +
            '        soap#Fault: {\n' +
            '            faultcode: "soap:Server",\n' +
            '            faultstring: "Operation [OPERATION_NAME:\\soapkit-config] not implemented"\n' +
            '        }\n' +
            '    } write "application/xml"\n' +
            '}]]></ee:set-payload>\n' +
            '            </ee:message>\n' +
            '        </ee:transform>\n' +
            '    </flow>'

    static final String FOOTER = '</mule>'
}
