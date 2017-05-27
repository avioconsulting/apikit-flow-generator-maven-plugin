import org.mule.tools.apikit.ScaffolderAPI

/**
 * Created by brady on 5/27/17.
 */
class Tester {
    static void main(String[] args) {
        def apiBuilder = new ScaffolderAPI()
        def raml = new File(
                '/Users/brady/code/mule/repos_NOCRASHPLAN/pacu/process/puf-process-appsubmission/src/main/api/api-process-appsubmission-v1.raml')
        assert raml.exists()
        apiBuilder.run([raml],
                       new File('/Users/brady/code/mule/repos_NOCRASHPLAN/apikit-flow-generator-plugin/foobar'))
        println 'howdy!'
    }
}
