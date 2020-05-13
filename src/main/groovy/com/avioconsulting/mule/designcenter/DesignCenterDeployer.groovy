package com.avioconsulting.mule.designcenter

import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpUriRequest
import org.apache.maven.plugin.logging.Log

import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class DesignCenterDeployer implements DesignCenterHttpFunctionality {
    private final HttpClientWrapper clientWrapper
    private final Log logger

    DesignCenterDeployer(HttpClientWrapper clientWrapper,
                         Log logger) {
        this.logger = logger
        this.clientWrapper = clientWrapper
    }

    List<RamlFile> getExistingDesignCenterFilesByProjectName(String projectName,
                                                             String branchName) {
        def projectId = getDesignCenterProjectId(projectName)
        return getExistingDesignCenterFiles(projectId,
                                            branchName)
    }

    private String getDesignCenterProjectId(String projectName) {
        logger.println "Looking up ID for Design Center project '${projectName}'"
        def request = new HttpGet("${clientWrapper.baseUrl}/designcenter/api-designer/projects")
        def failureContext = "fetch design center project ID for '${projectName}'"
        executeDesignCenterRequest(request,
                                   failureContext) { results ->
            def id = results.find { result ->
                result.name == projectName
            }?.id
            if (id) {
                logger.println "Identified Design Center project '${projectName}' as ID ${id}"
            } else {
                throw new Exception("Unable to find ID for Design Center project '${projectName}'")
            }
            return id
        }
    }

    def executeDesignCenterRequest(HttpUriRequest request,
                                   String failureContext,
                                   Closure resultHandler = null) {
        executeDesignCenterRequest(clientWrapper,
                                   request,
                                   failureContext,
                                   resultHandler)
    }

    private List<RamlFile> getExistingDesignCenterFiles(String projectId,
                                                        String branchName) {
        logger.println('Fetching existing Design Center RAML files')
        def url = "${clientWrapper.baseUrl}/exchange/api/v1/organizations/${clientWrapper.anypointOrganizationId}/projects/${projectId}/refs/${branchName}/archive"
        def request = new HttpGet(url)
        clientWrapper.execute(request).withCloseable { response ->
            HttpClientWrapper.assertSuccessfulResponse(response,
                                                       'fetching files')
            new ZipInputStream(response.entity.content).withCloseable { zip ->
                List<RamlFile> results = []
                ZipEntry ze
                def buffer = new byte[2048]
                while ((ze = zip.nextEntry) != null) {
                    if (ze.directory) {
                        def withoutTrailingSlash = ze.name[0..-2]
                        results << new RamlFile(withoutTrailingSlash,
                                                null,
                                                'FOLDER')
                    } else {
                        def len = 0
                        def bos = new ByteArrayOutputStream()
                        while ((len = zip.read(buffer)) > 0) {
                            bos.write(buffer,
                                      0,
                                      len)
                        }
                        results << new RamlFile(ze.name,
                                                new String(bos.toByteArray()),
                                                'FILE')
                    }
                }
                return results
            }
        }
    }
}
