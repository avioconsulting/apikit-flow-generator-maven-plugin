package com.avioconsulting.mule

import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IFolder
import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.IResource
import org.eclipse.core.runtime.CoreException
import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.core.IClasspathEntry
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.JavaModelException
import org.mule.tooling.core.cache.IMuleConfigurationsCache
import org.mule.tooling.core.io.EditingScope
import org.mule.tooling.core.m2.Repository
import org.mule.tooling.core.m2.dependency.MavenDependency
import org.mule.tooling.core.model.IMuleApplicationProject
import org.mule.tooling.core.model.IMuleDomain
import org.mule.tooling.core.model.IMuleDomainProject
import org.mule.tooling.core.model.IMuleProjectComponent
import org.mule.tooling.core.model.IMuleProjectVisitor
import org.mule.tooling.core.model.MuleProjectKind
import org.mule.tooling.core.model.ProjectModelState
import org.mule.tooling.core.module.ExternalContributionMuleModule
import org.mule.tooling.core.module.IMuleModuleManager
import org.mule.tooling.core.runtime.server.ISchemaLocationLookup
import org.mule.tooling.core.runtime.server.ServerDefinition
import org.mule.tooling.model.project.IMuleProjectModel
import org.mule.tooling.model.project.MuleExtension

import java.util.function.Consumer

class MuleAppProject implements IMuleApplicationProject {
    @Override
    IFolder getMuleTestResourcesFolder() {
        return null
    }

    @Override
    IFolder getDataMappingsFolder() {
        return null
    }

    @Override
    IFolder getApisFolder() {
        return null
    }

    @Override
    boolean isTestResource(IResource iResource) {
        return false
    }

    @Override
    IFolder getTestSourceFolder() {
        return null
    }

    @Override
    IFolder getTestResourcesFolder() {
        return null
    }

    @Override
    String getDomainName() {
        return null
    }

    @Override
    Optional<MavenDependency> getDomainCoordinates() {
        return null
    }

    @Override
    IMuleDomain getDomain() {
        return null
    }

    @Override
    boolean hasDefaultDomain() {
        return false
    }

    @Override
    void setDomain(Optional<MavenDependency> optional) throws CoreException {

    }

    @Override
    void setDomain(Optional<MavenDependency> optional, IProgressMonitor iProgressMonitor) throws CoreException {

    }

    @Override
    boolean belongsToDomain(IMuleDomainProject iMuleDomainProject) {
        return false
    }

    @Override
    IFolder getMuleSourceFolder() {
        return null
    }

    @Override
    MuleProjectKind<?> getKind() {
        return null
    }

    @Override
    MavenDependency getProjectDependency() {
        return null
    }

    @Override
    ProjectModelState getProjectModelState() {
        return null
    }

    @Override
    IJavaProject getJavaProject() {
        return null
    }

    @Override
    IProject getProject() {
        return null
    }

    @Override
    IMuleConfigurationsCache getConfigurationsCache() {
        return null
    }

    @Override
    IMuleModuleManager getModuleManager() {
        return null
    }

    @Override
    IMuleModuleManager getModuleManager(EditingScope editingScope) {
        return null
    }

    @Override
    IFolder getMuleResourcesFolder() {
        return null
    }

    @Override
    IFolder getMuleAppsFolder() {
        return null
    }

    @Override
    IFolder getProjectRootFolder() {
        return null
    }

    @Override
    IFile getMuleProjectDescriptorFile() {
        return null
    }

    @Override
    IMuleProjectModel getMuleProjectModel() {
        return null
    }

    @Override
    void save() throws CoreException {

    }

    @Override
    IFolder getFolder(String s) {
        return null
    }

    @Override
    File getFolder(String s, boolean b) {
        return null
    }

    @Override
    IFile getFile(String s) {
        return null
    }

    @Override
    IFile getFile(IPath iPath) {
        return null
    }

    @Override
    IPath getLocation() {
        return null
    }

    @Override
    IFile getClasspathFile() {
        return null
    }

    @Override
    IFile getProjectFile() {
        return null
    }

    @Override
    String getName() {
        return null
    }

    @Override
    String getLabel() {
        return null
    }

    @Override
    void setLabel(String s) {

    }

    @Override
    String getDescription() {
        return null
    }

    @Override
    void setDescription(String s) {

    }

    @Override
    String getRuntimeId() {
        return null
    }

    @Override
    void changeRuntime(String s) throws CoreException {

    }

    @Override
    ServerDefinition getServerDefinition() {
        return null
    }

    @Override
    List<MuleExtension> getDeclaredExtensions() {
        return null
    }

    @Override
    List<MavenDependency> getDeclaredDependencies() {
        return null
    }

    @Override
    void addMuleExtension(ExternalContributionMuleModule externalContributionMuleModule) throws CoreException {

    }

    @Override
    void addMuleTestExtension(ExternalContributionMuleModule externalContributionMuleModule) throws CoreException {

    }

    @Override
    void removeMuleExtension(ExternalContributionMuleModule externalContributionMuleModule) throws CoreException {

    }

    @Override
    void addObserver(Observer observer) {

    }

    @Override
    void deleteObserver(Observer observer) {

    }

    @Override
    void refresh() {

    }

    @Override
    void refresh(IProgressMonitor iProgressMonitor) {

    }

    @Override
    void refresh(IProgressMonitor iProgressMonitor, int i) {

    }

    @Override
    void accept(IMuleProjectVisitor iMuleProjectVisitor) {

    }

    @Override
    List<Repository> getProjectRepositories() {
        return null
    }

    @Override
    void modifyClasspath(Consumer<List<IClasspathEntry>> consumer,
                         IProgressMonitor iProgressMonitor) throws JavaModelException {

    }

    @Override
    ISchemaLocationLookup getSchemaLocationLookup() {
        return null
    }

    @Override
    def <T extends IMuleProjectComponent> T getProjectComponent(Class<T> aClass) {
        return null
    }
}
