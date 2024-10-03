# APIKit Flow Generator Maven Plugin for Mule 4
This maven plugin generates mule flows for REST or SOAP APIs. 

## Usage

**Step0:** Configure AVIO Github Package Registry

In your POM, add following plugin repository in `pluginRepositories` tag (add if doesn't exist) -

```xml
    <pluginRepository>
        <id>github-avio-pkg</id>
        <name>AVIO Github Package Repository</name>
        <url>https://maven.pkg.github.com/avioconsulting/public-packages/</url>
        <layout>default</layout>
    </pluginRepository>
```

In your `~/.m2/settings.xml`, add credentials for server id `github-avio-pkg`, like below -
```xml
    <server>
        <id>github-avio-pkg</id>
        <username>YOUR_GIT_USER</username>
        <password>YOUR_GIT_PERSONAL_TOKEN</password>
    </server>
```
See [working-with-the-apache-maven-registry#authenticating-with-a-personal-access-token](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry#authenticating-with-a-personal-access-token) for more details on Github Package Authentication.

**NOTE:** The Github Personal Token must have **read:packages** permission.

**Step1:** To use this plugin, add following entry to maven pom.xml -
```xml
<plugin>
    <groupId>com.avioconsulting.mule</groupId>
    <artifactId>apikit-flow-generator-maven-plugin</artifactId>
    <version>7.5.1.1</version>
</plugin>
```
This makes two goals available to generate flows - 
1. apikit-flow-generator:generateFlowRest
2. apikit-flow-generator:generateFlowSoap

REST flow generation from RAML-
```
mvn apikit-flow-generator:generateFlowRest \
    -Dlocal.raml.directory=~/AnypointStudio/studio-workspace/test-api \
    -Dmain.raml=test-api.raml \
    -Danypoint.username= \
    -Danypoint.password= \
    -Danypoint.connected-app.id= \
    -Danypoint.connected-app.secret= \
    -DdesignCenter.project.name=
```
_**NOTE:** Anypoint users with MFA enabled or SSO Users are not supported by this plugin._
