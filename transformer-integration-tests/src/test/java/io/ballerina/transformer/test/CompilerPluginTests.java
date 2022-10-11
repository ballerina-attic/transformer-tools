/*
 *  Copyright (c) 2022, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package io.ballerina.transformer.test;

import io.ballerina.projects.CodeGeneratorResult;
import io.ballerina.projects.DiagnosticResult;
import io.ballerina.projects.Package;
import io.ballerina.projects.ProjectEnvironmentBuilder;
import io.ballerina.projects.directory.BuildProject;
import io.ballerina.projects.environment.Environment;
import io.ballerina.projects.environment.EnvironmentBuilder;
import io.ballerina.tools.diagnostics.Diagnostic;
import io.ballerina.transformer.plugin.diagnostic.DiagnosticMessage;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * This class includes integration tests for Ballerina Transformer compiler plugin.
 */
public class CompilerPluginTests {

    private static final Path RESOURCE_DIRECTORY = Paths.get("src", "test", "resources")
            .toAbsolutePath();
    private static final Path DISTRIBUTION_PATH = Paths.get("../", "target", "ballerina-runtime")
            .toAbsolutePath();

    private Package loadPackage(String path) {
        Path projectDirPath = RESOURCE_DIRECTORY.resolve(path);
        BuildProject project = BuildProject.load(getEnvironmentBuilder(), projectDirPath);
        return project.currentPackage();
    }

    private static ProjectEnvironmentBuilder getEnvironmentBuilder() {
        Environment environment = EnvironmentBuilder.getBuilder().setBallerinaHome(DISTRIBUTION_PATH).build();
        return ProjectEnvironmentBuilder.getBuilder(environment);
    }

    private void assertError(DiagnosticResult diagnosticResult, int index, DiagnosticMessage diagnosticMessage) {
        Diagnostic diagnostic = (Diagnostic) diagnosticResult.errors().toArray()[index];
        Assert.assertEquals(diagnostic.diagnosticInfo().messageFormat(), diagnosticMessage.getMessageFormat());
        Assert.assertEquals(diagnostic.diagnosticInfo().code(), diagnosticMessage.getCode());
    }

    @Test
    public void testForAnnotatedFunctions() {
        Package currentPackage = loadPackage("sample_package_0");
        CodeGeneratorResult codeGenResult = currentPackage.runCodeGeneratorPlugins();
        DiagnosticResult diagnosticResult = codeGenResult.reportedDiagnostics();
        Assert.assertEquals(diagnosticResult.errorCount(), 5);
        assertError(diagnosticResult, 0, DiagnosticMessage.ERROR_102);
        assertError(diagnosticResult, 1, DiagnosticMessage.ERROR_104);
        assertError(diagnosticResult, 2, DiagnosticMessage.ERROR_101);
        assertError(diagnosticResult, 3, DiagnosticMessage.ERROR_106);
        assertError(diagnosticResult, 4, DiagnosticMessage.ERROR_105);
    }

    @Test
    public void testForNoPublicIsolatedExpressionBodyFunc() {
        Package currentPackage = loadPackage("sample_package_1");
        CodeGeneratorResult codeGenResult = currentPackage.runCodeGeneratorPlugins();
        DiagnosticResult diagnosticResult = codeGenResult.reportedDiagnostics();
        Assert.assertEquals(diagnosticResult.errorCount(), 1);
        assertError(diagnosticResult, 0, DiagnosticMessage.ERROR_105);
    }

    @Test
    public void testForMainEntryPoint() {
        Package currentPackage = loadPackage("sample_package_2");
        CodeGeneratorResult codeGenResult = currentPackage.runCodeGeneratorPlugins();
        DiagnosticResult diagnosticResult = codeGenResult.reportedDiagnostics();
        Assert.assertEquals(diagnosticResult.errorCount(), 3);
        assertError(diagnosticResult, 0, DiagnosticMessage.ERROR_100);
        assertError(diagnosticResult, 1, DiagnosticMessage.ERROR_101);
        assertError(diagnosticResult, 2, DiagnosticMessage.ERROR_105);
    }

    @Test
    public void testForClasses() {
        Package currentPackage = loadPackage("sample_package_3");
        CodeGeneratorResult codeGenResult = currentPackage.runCodeGeneratorPlugins();
        DiagnosticResult diagnosticResult = codeGenResult.reportedDiagnostics();
        Assert.assertEquals(diagnosticResult.errorCount(), 2);
        assertError(diagnosticResult, 0, DiagnosticMessage.ERROR_103);
        assertError(diagnosticResult, 1, DiagnosticMessage.ERROR_105);
    }

    @Test
    public void testForServices() {
        Package currentPackage = loadPackage("sample_package_4");
        CodeGeneratorResult codeGenResult = currentPackage.runCodeGeneratorPlugins();
        DiagnosticResult diagnosticResult = codeGenResult.reportedDiagnostics();
        Assert.assertEquals(diagnosticResult.errorCount(), 2);
        assertError(diagnosticResult, 0, DiagnosticMessage.ERROR_104);
        assertError(diagnosticResult, 1, DiagnosticMessage.ERROR_105);
    }

    @Test
    public void testForListeners() {
        Package currentPackage = loadPackage("sample_package_5");
        CodeGeneratorResult codeGenResult = currentPackage.runCodeGeneratorPlugins();
        DiagnosticResult diagnosticResult = codeGenResult.reportedDiagnostics();
        Assert.assertEquals(diagnosticResult.errorCount(), 4);
        assertError(diagnosticResult, 0, DiagnosticMessage.ERROR_102);
        assertError(diagnosticResult, 1, DiagnosticMessage.ERROR_104);
        assertError(diagnosticResult, 2, DiagnosticMessage.ERROR_103);
        assertError(diagnosticResult, 3, DiagnosticMessage.ERROR_105);
    }

    @Test
    public void testForPublicFunctions() {
        Package currentPackage = loadPackage("sample_package_6");
        CodeGeneratorResult codeGenResult = currentPackage.runCodeGeneratorPlugins();
        DiagnosticResult diagnosticResult = codeGenResult.reportedDiagnostics();
        Assert.assertEquals(diagnosticResult.errorCount(), 4);
        assertError(diagnosticResult, 0, DiagnosticMessage.ERROR_102);
        assertError(diagnosticResult, 1, DiagnosticMessage.ERROR_104);
        assertError(diagnosticResult, 2, DiagnosticMessage.ERROR_101);
        assertError(diagnosticResult, 3, DiagnosticMessage.ERROR_105);
    }

    @Test
    public void testForValidPackage() {
        Package currentPackage = loadPackage("sample_package_7");
        CodeGeneratorResult codeGenResult = currentPackage.runCodeGeneratorPlugins();
        DiagnosticResult diagnosticResult = codeGenResult.reportedDiagnostics();
        Assert.assertEquals(diagnosticResult.errorCount(), 0);
    }

    @Test
    public void testForValidPackageWithFailingCodeGeneration() {
        Package currentPackage = loadPackage("sample_package_8");
//        PackageCompilation compilation = currentPackage.getCompilation();
//        DiagnosticResult diagnosticResult = compilation.diagnosticResult();
        CodeGeneratorResult codeGenResult = currentPackage.runCodeGeneratorPlugins();
//        codeGenResult.updatedPackage().orElse(null).modules().forEach(module -> {
//            module.documentIds().forEach(docId -> {
//                Systems.out.println(module.document(docId).textDocument().toString());
//            });
//        });
        DiagnosticResult diagnosticResult = codeGenResult.reportedDiagnostics();
        Assert.assertEquals(diagnosticResult.errorCount(), 0);
    }
}
