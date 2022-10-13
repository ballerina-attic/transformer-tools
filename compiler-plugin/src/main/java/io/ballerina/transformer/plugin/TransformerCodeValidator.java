/*
 * Copyright (c) 2022, WSO2 LLC. (http://www.wso2.com) All Rights Reserved.
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

import io.ballerina.compiler.api.symbols.ArrayTypeSymbol;
import io.ballerina.compiler.api.symbols.MapTypeSymbol;
import io.ballerina.compiler.api.symbols.TableTypeSymbol;
import io.ballerina.compiler.api.symbols.TypeDescKind;
import io.ballerina.compiler.api.symbols.TypeReferenceTypeSymbol;
import io.ballerina.compiler.api.symbols.TypeSymbol;
import io.ballerina.compiler.syntax.tree.ArrayTypeDescriptorNode;
import io.ballerina.compiler.syntax.tree.DefaultableParameterNode;
import io.ballerina.compiler.syntax.tree.FunctionDefinitionNode;
import io.ballerina.compiler.syntax.tree.MapTypeDescriptorNode;
import io.ballerina.compiler.syntax.tree.ModulePartNode;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.NodeLocation;
import io.ballerina.compiler.syntax.tree.ParameterNode;
import io.ballerina.compiler.syntax.tree.RequiredParameterNode;
import io.ballerina.compiler.syntax.tree.RestParameterNode;
import io.ballerina.compiler.syntax.tree.ReturnTypeDescriptorNode;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.compiler.syntax.tree.TableTypeDescriptorNode;
import io.ballerina.compiler.syntax.tree.TypeParameterNode;
import io.ballerina.projects.DocumentId;
import io.ballerina.projects.Module;
import io.ballerina.projects.ModuleId;
import io.ballerina.projects.plugins.AnalysisTask;
import io.ballerina.projects.plugins.SyntaxNodeAnalysisContext;
import io.ballerina.tools.diagnostics.Diagnostic;
import io.ballerina.tools.diagnostics.DiagnosticFactory;
import io.ballerina.tools.diagnostics.DiagnosticInfo;
import io.ballerina.transformer.plugin.diagnostic.DiagnosticMessage;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Transformer module Code Validator.
 *
 */
public class TransformerCodeValidator implements AnalysisTask<SyntaxNodeAnalysisContext> {

    private static final String MAIN_KEYWORD = "main";
    private final AtomicInteger visitedDefaultModulePart;
    private final AtomicBoolean foundTransformerFunc;
    private final List<FunctionDefinitionNode> transformerFunctions;


    //TODO: Try to figure out a way to store these in a seperate DataType, and move these to a Const file.
    private final List<SyntaxKind> httpSupportedTypes = List.of(
            SyntaxKind.BOOLEAN_TYPE_DESC,
            SyntaxKind.INT_TYPE_DESC,
            SyntaxKind.FLOAT_TYPE_DESC,
            SyntaxKind.DECIMAL_TYPE_DESC,
            SyntaxKind.BYTE_TYPE_DESC,
            SyntaxKind.STRING_TYPE_DESC,
            SyntaxKind.JSON_TYPE_DESC,
            SyntaxKind.MAP_TYPE_DESC
    );

    private final List<TypeDescKind> httpSupportedRefTypes = List.of(
            TypeDescKind.BOOLEAN,
            TypeDescKind.INT,
            TypeDescKind.FLOAT,
            TypeDescKind.DECIMAL,
            TypeDescKind.BYTE,
            TypeDescKind.STRING,
            TypeDescKind.JSON,
            TypeDescKind.RECORD
    );

    private boolean diagnosticForCompilationErrorReported = false;

    TransformerCodeValidator(AtomicInteger visitedDefaultModulePart, AtomicBoolean foundTransformerFunc,
                             List<FunctionDefinitionNode> transformerFunctions) {
        this.visitedDefaultModulePart = visitedDefaultModulePart;
        this.foundTransformerFunc = foundTransformerFunc;
        this.transformerFunctions = transformerFunctions;
    }

    @Override
    public void perform(SyntaxNodeAnalysisContext syntaxNodeAnalysisContext) {
        ModulePartNode modulePartNode = (ModulePartNode) syntaxNodeAnalysisContext.node();
        DocumentId documentId = syntaxNodeAnalysisContext.documentId();
        ModuleId moduleId = syntaxNodeAnalysisContext.moduleId();

        // Exclude Test related files from transformer validation
        for (DocumentId testDocId : syntaxNodeAnalysisContext.currentPackage().module(moduleId).testDocumentIds()) {
            if (documentId.equals(testDocId)) {
                return;
            }
        }

        // Skip Code Analysis and Generation if the compilation got errors
         if (syntaxNodeAnalysisContext.compilation().diagnosticResult().errors().size() > 0) {
             if (!diagnosticForCompilationErrorReported) {
                 reportDiagnostics(syntaxNodeAnalysisContext, DiagnosticMessage.ERROR_109,
                         syntaxNodeAnalysisContext.node().location());
                 diagnosticForCompilationErrorReported = true;
             }
             return;
         }

        // Analyze each node within each ModulePart nodes
        modulePartNode.members().forEach(member -> {
            SyntaxKind nodeKind = member.kind();
            NodeLocation memberLocation = member.location();

            switch (nodeKind) {
                case FUNCTION_DEFINITION:
                    FunctionDefinitionNode functionDefNode = (FunctionDefinitionNode) member;
                    if (functionDefNode.functionName().text().equals(MAIN_KEYWORD)) {
                        reportDiagnostics(syntaxNodeAnalysisContext, DiagnosticMessage.ERROR_100, memberLocation);
                    }
                    if (functionDefNode.qualifierList().stream().anyMatch(qualifier ->
                            qualifier.kind() == SyntaxKind.PUBLIC_KEYWORD)
                            && functionDefNode.functionBody().kind() != SyntaxKind.EXPRESSION_FUNCTION_BODY) {
                        reportDiagnostics(syntaxNodeAnalysisContext, DiagnosticMessage.ERROR_101, memberLocation);
                    }
                    functionDefNode.metadata().ifPresent(metadata -> {
                        if (!metadata.annotations().isEmpty()) {
                            reportDiagnostics(syntaxNodeAnalysisContext, DiagnosticMessage.ERROR_106, memberLocation);
                        }
                    });
                    if (isDefaultModule(syntaxNodeAnalysisContext.currentPackage().modules(), moduleId)) {
                        if (isTransformerFunc(functionDefNode)) {
                            foundTransformerFunc.set(true);
                            transformerFunctions.add(functionDefNode);
                            validateServiceGenerableFunction(functionDefNode, syntaxNodeAnalysisContext);
                        }
                    }
                    break;
                case LISTENER_DECLARATION:
                    reportDiagnostics(syntaxNodeAnalysisContext, DiagnosticMessage.ERROR_102, memberLocation);
                    break;
                case CLASS_DEFINITION:
                    reportDiagnostics(syntaxNodeAnalysisContext, DiagnosticMessage.ERROR_103, memberLocation);
                    break;
                case SERVICE_DECLARATION:
                    reportDiagnostics(syntaxNodeAnalysisContext, DiagnosticMessage.ERROR_104, memberLocation);
                    break;
                case TYPE_DEFINITION:
                case MODULE_VAR_DECL:
                case CONST_DECLARATION:
                case ENUM_DECLARATION:
                case MODULE_XML_NAMESPACE_DECLARATION:
                    break;
                default:
                    reportDiagnostics(syntaxNodeAnalysisContext, DiagnosticMessage.ERROR_110, memberLocation);
                    break;
            }
        });

        // Check if all ModulePart nodes within default package is visited to report diagnostics
        if (isDefaultModule(syntaxNodeAnalysisContext.currentPackage().modules(), moduleId)) {
            if (syntaxNodeAnalysisContext.currentPackage().module(moduleId).documentIds().size()
                    == visitedDefaultModulePart.incrementAndGet()
                    && !foundTransformerFunc.get()) {
                reportDiagnostics(syntaxNodeAnalysisContext, DiagnosticMessage.ERROR_105,
                        syntaxNodeAnalysisContext.node().location());
            }
        }
    }

    private void reportDiagnostics(SyntaxNodeAnalysisContext syntaxNodeAnalysisContext,
                                   DiagnosticMessage diagnosticMessage, NodeLocation location, Object... args) {
        DiagnosticInfo diagnosticInfo = new DiagnosticInfo(diagnosticMessage.getCode(),
                diagnosticMessage.getMessageFormat(), diagnosticMessage.getSeverity());
        Diagnostic diagnostic =
                DiagnosticFactory.createDiagnostic(diagnosticInfo, location, args);
        syntaxNodeAnalysisContext.reportDiagnostic(diagnostic);
    }

    private boolean isDefaultModule(Iterable<Module> modules, ModuleId moduleId) {
        for (Module module : modules) {
            if (module.isDefaultModule() && module.moduleId() == moduleId) {
                return true;
            }
        }
        return false;
    }

    private boolean isTransformerFunc(FunctionDefinitionNode funcDefNode) {
        return !funcDefNode.qualifierList().isEmpty() &&
                funcDefNode.qualifierList().stream().anyMatch(qualifier ->
                        qualifier.kind() == SyntaxKind.PUBLIC_KEYWORD)
                && funcDefNode.qualifierList().stream().anyMatch(qualifier ->
                qualifier.kind() == SyntaxKind.ISOLATED_KEYWORD)
                && funcDefNode.functionBody().kind() == SyntaxKind.EXPRESSION_FUNCTION_BODY;
    }

    private void validateServiceGenerableFunction(FunctionDefinitionNode funcDefNode,
                                                  SyntaxNodeAnalysisContext syntaxNodeAnalysisContext) {
        if (!isParamsSupported(funcDefNode, syntaxNodeAnalysisContext)) {
            reportDiagnostics(syntaxNodeAnalysisContext, DiagnosticMessage.ERROR_107,
                    funcDefNode.location(), funcDefNode.functionName().text());
        } else if (!isReturnTypeSupported(funcDefNode, syntaxNodeAnalysisContext)) {
            reportDiagnostics(syntaxNodeAnalysisContext, DiagnosticMessage.ERROR_108,
                    funcDefNode.location(), funcDefNode.functionName().text());
        }
    }

    private boolean isParamsSupported(FunctionDefinitionNode funcDefNode,
                                      SyntaxNodeAnalysisContext syntaxNodeAnalysisContext) {
        for (ParameterNode param : funcDefNode.functionSignature().parameters()) {
            if (param.kind().equals(SyntaxKind.REQUIRED_PARAM)) {
                RequiredParameterNode requiredParamNode = (RequiredParameterNode) param;
                if (!isNodeTypeHTTPSupported(requiredParamNode.typeName(), syntaxNodeAnalysisContext)) {
                    return false;
                }
            } else if (param.kind().equals(SyntaxKind.DEFAULTABLE_PARAM)) {
                DefaultableParameterNode defaultableParamNode = (DefaultableParameterNode) param;
                if (!isNodeTypeHTTPSupported(defaultableParamNode.typeName(), syntaxNodeAnalysisContext)) {
                    return false;
                }
            } else if (param.kind().equals(SyntaxKind.REST_PARAM)) {
                RestParameterNode restParamNode = (RestParameterNode) param;
                if (!isNodeTypeHTTPSupported(restParamNode.typeName(), syntaxNodeAnalysisContext)) {
                    return false;
                }
            } else {
                return false;
            }
        }
        return true;
    }

    private boolean isReturnTypeSupported(FunctionDefinitionNode funcDefNode,
                                          SyntaxNodeAnalysisContext syntaxNodeAnalysisContext) {
        // TODO: handle union type desc for return types. ie: error?, (int|error), (int|string), (RecName|error)
        AtomicBoolean foundSupportedType = new AtomicBoolean(false);
        AtomicBoolean foundUnsupportedType = new AtomicBoolean(false);
        if (funcDefNode.functionSignature().returnTypeDesc().isPresent()) {
            ReturnTypeDescriptorNode returnTypeDescNode = funcDefNode.functionSignature().returnTypeDesc().get();
            if (isNodeTypeHTTPSupported(returnTypeDescNode.type(), syntaxNodeAnalysisContext)) {
                foundSupportedType.set(true);
            } else {
                foundUnsupportedType.set(true);
            }
        } else {
            foundSupportedType.set(true);
        }
        return foundSupportedType.get() && !foundUnsupportedType.get();
    }

    private boolean isNodeTypeHTTPSupported(Node node, SyntaxNodeAnalysisContext syntaxNodeAnalysisContext) {
        switch (node.kind()) {
            case ARRAY_TYPE_DESC:
                return httpSupportedTypes.contains(((ArrayTypeDescriptorNode) node).memberTypeDesc().kind());
            case MAP_TYPE_DESC:
                return httpSupportedTypes.contains(((MapTypeDescriptorNode) node).mapTypeParamsNode()
                        .typeNode().kind());
            case TABLE_TYPE_DESC:
                if (((TypeParameterNode) ((TableTypeDescriptorNode) node).rowTypeParameterNode()).typeNode().kind()
                        .equals(SyntaxKind.MAP_TYPE_DESC)) {
                    return httpSupportedTypes.contains(((MapTypeDescriptorNode) ((TypeParameterNode)
                            ((TableTypeDescriptorNode) node).rowTypeParameterNode())
                            .typeNode()).mapTypeParamsNode().typeNode().kind());
                }
                return false;
            case SIMPLE_NAME_REFERENCE:
                if (syntaxNodeAnalysisContext.semanticModel().symbol(node).isPresent()) {
                    TypeSymbol typeSymbol = ((TypeReferenceTypeSymbol) syntaxNodeAnalysisContext.semanticModel()
                            .symbol(node).get()).typeDescriptor();
                    return isSupportedTypeReference(typeSymbol);
                }
                return false;
            default:
                return httpSupportedTypes.contains(node.kind());
        }
    }

    private boolean isSupportedTypeReference(TypeSymbol typeSymbol) {
        TypeDescKind typeDescKind = typeSymbol.typeKind();
        if (typeDescKind.equals(TypeDescKind.ARRAY)) {
            TypeSymbol memberTypeDesc = ((ArrayTypeSymbol) typeSymbol).memberTypeDescriptor();
            return isSupportedTypeReference(memberTypeDesc);
        } else if (typeDescKind.equals(TypeDescKind.MAP)) {
            TypeSymbol memberTypeDesc = ((MapTypeSymbol) typeSymbol).typeParam();
            return isSupportedTypeReference(memberTypeDesc);
        } else if (typeDescKind.equals(TypeDescKind.TABLE)) {
            TypeSymbol rowTypeDesc = ((TableTypeSymbol) typeSymbol).rowTypeParameter();
            return isSupportedTypeReference(rowTypeDesc);
        }
        return httpSupportedRefTypes.contains(typeDescKind);
    }
}
