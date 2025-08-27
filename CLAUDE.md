# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Maven plugin for generating MuleSoft APIKit flows from REST (RAML) or SOAP (WSDL) specifications. The plugin scaffolds Mule 4 flows and configurations automatically.

## Essential Commands

### Building and Testing
- **Build the plugin**: `mvn clean compile`
- **Run tests**: `mvn test`
- **Package the plugin**: `mvn package`
- **Install locally**: `mvn install`
- **Release build**: `mvn clean package -Prelease`

### Running the Plugin Goals
- **Generate REST flows**: `mvn apikit-flow-generator:generateFlowRest`
- **Generate SOAP flows**: `mvn apikit-flow-generator:generateFlowSoap`

### Testing Plugin Goals Locally
After building with `mvn install`, test in a target Mule project:
```bash
mvn com.avioconsulting.mule:apikit-flow-generator-maven-plugin:generateFlowRest \
    -Dlocal.raml.directory=/path/to/raml \
    -Dmain.raml=api.raml
```

## Code Architecture

### Core Components
- **RestGenerateMojo.groovy**: Maven goal for REST API flow generation from RAML specs
- **SoapGenerateMojo.groovy**: Maven goal for SOAP service flow generation from WSDL files
- **RestGenerator.groovy**: Core logic for REST flow scaffolding using MuleSoft APIKit scaffolder
- **SoapGenerator.groovy**: Core logic for SOAP flow generation and XML manipulation

### RAML/API Sources Supported
1. **Local RAML files**: `ramlDirectory` + `ramlFilename`
2. **Exchange artifacts**: `ramlGroupId` + `ramlArtifactId` (resolved from Maven dependencies)  
3. **Design Center projects**: `ramlDcProject` + `ramlDcBranch` with Anypoint credentials

### Authentication Methods
- Username/Password: `anypointUsername` + `anypointPassword`
- Connected App: `anypointConnectedAppId` + `anypointConnectedAppSecret`

### Key Dependencies
- **Groovy 3.0.11**: Primary language for plugin implementation
- **MuleSoft APIKit Scaffolder 2.4.2**: Core scaffolding functionality
- **WSDL4J 1.6.3**: WSDL parsing for SOAP generation
- **Apache HttpClient 4.5.14**: HTTP operations for Design Center integration

### Design Center Integration
- **DesignCenterDeployer.groovy**: Handles API deployment/download from Design Center
- **HttpClientWrapper.groovy**: Wraps HTTP operations with authentication
- **RamlFile.groovy**: Represents RAML files from Design Center

### Test Structure
- Unit tests in `src/test/groovy/` mirror the main source structure
- Test resources include sample RAML, WSDL, and expected XML output files
- Uses JUnit 4.13.2 with Hamcrest matchers and XMLUnit for XML comparison

## Maven Plugin Development Notes

This follows standard Maven plugin conventions:
- Mojos are annotated with `@Mojo(name = 'goalName')`
- Parameters use `@Parameter(property = 'propertyName')`
- Extends `AbstractMojo` for logging and execution
- Uses Groovy instead of Java for implementation

The plugin generates Mule configuration XML files in the target project's `src/main/mule/` directory.