package com.avioconsulting.mule

import org.mule.tools.apikit.ScaffolderAPI

class Generator implements FileUtil {
    static generate(File baseDirectory,
                    String ramlPath) {
        def apiBuilder = new ScaffolderAPI()
        def mainDir = join(baseDirectory, 'src', 'main')
        def ramlFile = join(mainDir, 'api', ramlPath)
        assert ramlFile.exists()
        def appDirectory = join(mainDir, 'app')
        apiBuilder.run([ramlFile],
                       appDirectory)
    }
}
