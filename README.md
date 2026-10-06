# APIKit Flow Generator Maven Plugin for Mule 4
This maven plugin generates mule flows for REST or SOAP APIs.

## Usage

### REST Contract Sources

REST generation supports RAML and OAS 3.0 through MuleSoft's `ParserService`
and `MainAppScaffolder`. The plugin applies AVIO listener, validation,
error-handler and autodiscovery settings to the generated configuration.

For a local OAS contract, set `specDirectory` to the contract directory
and `specFilename` to its entrypoint, such as `api.yaml`. These parameter
names remain valid for both formats. Supply the entrypoint explicitly for OAS.

For Exchange, the application's POM must declare exactly one matching API ZIP
dependency with classifier `raml` or `oas`. The plugin uses Maven's resolved file
and reads its entrypoint from `exchange.json`. It preserves that classifier in
the generated `resource::` reference. OAS is parsed directly, without Exchange
RAML conversion. If both classifiers are declared for the selected API, remove
the unwanted dependency before generation. Other cached ZIPs do not affect the
selection.

Exchange generation reads `exchange.json` inside Maven's resolved ZIP and uses
a resource loader to read the API and its included files through `jar:file:`
URIs. It does not extract the contract into the application or a temporary
project. Local contracts still use `src/main/resources/api/`.

These changes are in `3.0.0-SNAPSHOT`; existing archetypes pinned to an older
plugin must be updated before they can use this path. An OAS archetype path must
also declare the API dependency with classifier `oas`.

Version 3 replaces the `raml*` input prefix with `spec*`: `specDirectory`,
`specFilename`, `specGroupId`, `specArtifactId`, `specDcProject` and
`specDcBranch`. Exchange version and classifier come from the application POM.
The archetype sets that dependency using `specVersion` and `specClassifier`.

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

REST flow generation from a local contract:
```
mvn apikit-flow-generator:generateFlowRest \
    -DspecDirectory=/path/to/test-api \
    -DspecFilename=test-api.raml
```
_**NOTE:** Anypoint users with MFA enabled or SSO Users are not supported by this plugin._
