/*
 * Copyright (c) 2022, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.transformer.plugin;

import io.ballerina.compiler.syntax.tree.FunctionDefinitionNode;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.projects.plugins.CodeGenerator;
import io.ballerina.projects.plugins.CodeGeneratorContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Transformer module Code Analyzer and Generator.
 *
 */
public class TransformerCodeAnalyzerGenerator extends CodeGenerator {
    private final AtomicInteger visitedDefaultModulePart = new AtomicInteger(0);
    private final AtomicBoolean foundTransformerFunc = new AtomicBoolean(false);
    private final List<FunctionDefinitionNode> transformerFunctions = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void init(CodeGeneratorContext codeGeneratorContext) {
        codeGeneratorContext.addSyntaxNodeAnalysisTask(
                new TransformerCodeValidator(visitedDefaultModulePart, foundTransformerFunc, transformerFunctions),
                List.of(SyntaxKind.MODULE_PART));
        codeGeneratorContext.addSourceGeneratorTask(new TransformerServiceGenerator(transformerFunctions));
    }
}
