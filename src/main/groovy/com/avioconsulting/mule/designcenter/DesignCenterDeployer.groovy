package com.avioconsulting.mule.designcenter


import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpUriRequest
import org.apache.maven.plugin.logging.Log

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

    def getBranchUrl(HttpClientWrapper clientWrapper,
                     String projectId,
                     String branchName) {
        "${clientWrapper.baseUrl}/designcenter/api-designer/projects/${projectId}/branches/${branchName}"
    }

    private def getFilesUrl(String projectId,
                            String branchName) {
        "${getBranchUrl(clientWrapper, projectId, branchName)}/files"
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
        def url = getFilesUrl(projectId,
                              branchName)
        def request = new HttpGet(url)
        executeDesignCenterRequest(request,
                                   'Fetching project files') { List<Map> results ->
            def filesWeCareAbout = results.findAll { result ->
                def asFile = new File(result.path)
                asFile.name != '.gitignore'
            }
            return filesWeCareAbout.collect { result ->
                def filePath = result.path
                def escapedForUrl = URLEncoder.encode(filePath)
                def resultType = result.type
                if (resultType == 'FOLDER') {
                    new RamlFile(filePath,
                                 null,
                                 resultType)
                } else {
                    executeDesignCenterRequest(new HttpGet("${url}/${escapedForUrl}"),
                                               "Fetching file ${filePath}") { String contents ->
                        new RamlFile(filePath,
                                     contents,
                                     resultType)
                    }
                }
            }
        }
    }
}
