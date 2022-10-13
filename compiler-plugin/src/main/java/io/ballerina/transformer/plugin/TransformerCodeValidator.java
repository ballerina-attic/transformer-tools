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
import org.ballerinalang.model.types.ArrayType;
import org.ballerinalang.model.types.TypeKind;
//import org.ballerinalang.model.types.TypeKind;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Transformer module Code Validator.
 *
 */
public class TransformerCodeValidator implements AnalysisTask<SyntaxNodeAnalysisContext> {

    private final AtomicInteger visitedDefaultModulePart;
    private final AtomicBoolean foundTransformerFunc;
    private final List<FunctionDefinitionNode> transformerFunctions;

    private final List<SyntaxKind> httpSupportedTypes = List.of(
            SyntaxKind.BOOLEAN_TYPE_DESC,
            SyntaxKind.INT_TYPE_DESC,
            SyntaxKind.FLOAT_TYPE_DESC,
            SyntaxKind.DECIMAL_TYPE_DESC,
            SyntaxKind.BYTE_TYPE_DESC,
            SyntaxKind.STRING_TYPE_DESC,
            SyntaxKind.JSON_TYPE_DESC,
            SyntaxKind.MAP_TYPE_DESC);

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

        // Analyze each node within each ModulePart nodes
        modulePartNode.members().forEach(member -> {
            SyntaxKind nodeKind = member.kind();
            NodeLocation memberLocation = member.location();

            switch (nodeKind) {
                case FUNCTION_DEFINITION:
                    FunctionDefinitionNode functionDefNode = (FunctionDefinitionNode) member;
                    if (functionDefNode.functionName().text().equals("main")) {
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
                            if (!isServiceGenerableFunc(functionDefNode, syntaxNodeAnalysisContext)) {
                                reportDiagnostics(syntaxNodeAnalysisContext, DiagnosticMessage.ERROR_107,
                                        memberLocation, functionDefNode.functionName().text());
                            } else if (!isReturnTypeSupported(functionDefNode, syntaxNodeAnalysisContext)) {
                                reportDiagnostics(syntaxNodeAnalysisContext, DiagnosticMessage.ERROR_108,
                                        memberLocation, functionDefNode.functionName().text());
                            }
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
                default:
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
                qualifier.kind() == SyntaxKind.PUBLIC_KEYWORD)
                && funcDefNode.functionBody().kind() == SyntaxKind.EXPRESSION_FUNCTION_BODY;
    }

    private boolean isServiceGenerableFunc(FunctionDefinitionNode funcDefNode,
                                           SyntaxNodeAnalysisContext syntaxNodeAnalysisContext) {
        AtomicBoolean foundSupportedType = new AtomicBoolean(false);
        AtomicBoolean foundUnsupportedType = new AtomicBoolean(false);
        funcDefNode.functionSignature().parameters().forEach(param -> {
            if (param.kind().equals(SyntaxKind.REQUIRED_PARAM)) {
                RequiredParameterNode requiredParamNode = (RequiredParameterNode) param;
                if (requiredParamNode.typeName().kind().equals(SyntaxKind.ARRAY_TYPE_DESC)) {
                    if (httpSupportedTypes.contains(((ArrayTypeDescriptorNode) requiredParamNode.typeName())
                            .memberTypeDesc().kind())) {
                        foundSupportedType.set(true);
                    }
                } else if (requiredParamNode.typeName().kind().equals(SyntaxKind.MAP_TYPE_DESC)) {
                    if (httpSupportedTypes.contains(((MapTypeDescriptorNode) requiredParamNode.typeName())
                            .mapTypeParamsNode().typeNode().kind())) {
                        foundSupportedType.set(true);
                    }
                } else if (requiredParamNode.typeName().kind().equals(SyntaxKind.TABLE_TYPE_DESC)) {
                    if (((TypeParameterNode) ((TableTypeDescriptorNode) requiredParamNode.typeName())
                            .rowTypeParameterNode()).typeNode().kind().equals(SyntaxKind.MAP_TYPE_DESC)) {
                        if (httpSupportedTypes.contains(((MapTypeDescriptorNode) ((TypeParameterNode)
                                ((TableTypeDescriptorNode) requiredParamNode.typeName()).rowTypeParameterNode())
                                .typeNode()).mapTypeParamsNode().typeNode().kind())) {
                            foundSupportedType.set(true);
                        }
                    }
                } else if (requiredParamNode.typeName().kind().equals(SyntaxKind.SIMPLE_NAME_REFERENCE)) {
                    if (syntaxNodeAnalysisContext.semanticModel().symbol(requiredParamNode.typeName()).isPresent()) {
                        TypeSymbol typeSymbol =((TypeReferenceTypeSymbol) syntaxNodeAnalysisContext.semanticModel()
                                .symbol(requiredParamNode.typeName()).get()).typeDescriptor();
                        boolean isSupportedTypeRef = isSupportedTypeReference(typeSymbol);
                        if (isSupportedTypeRef) {
                            foundSupportedType.set(true);
                        } else {
                            foundUnsupportedType.set(true);
                        }
                    } else {
                        foundUnsupportedType.set(true);
                    }
                } else if (httpSupportedTypes.contains(requiredParamNode.typeName().kind())) {
                    foundSupportedType.set(true);
                } else {
                    foundUnsupportedType.set(true);
                }
            } else if (param.kind().equals(SyntaxKind.DEFAULTABLE_PARAM)) {
                DefaultableParameterNode defaultableParamNode = (DefaultableParameterNode) param;
                if (defaultableParamNode.typeName().kind().equals(SyntaxKind.ARRAY_TYPE_DESC)) {
                    if (httpSupportedTypes.contains(((ArrayTypeDescriptorNode) defaultableParamNode.typeName())
                            .memberTypeDesc().kind())) {
                        foundSupportedType.set(true);
                    }
                } else if (defaultableParamNode.typeName().kind().equals(SyntaxKind.MAP_TYPE_DESC)) {
                    if (httpSupportedTypes.contains(((MapTypeDescriptorNode) defaultableParamNode.typeName())
                            .mapTypeParamsNode().typeNode().kind())) {
                        foundSupportedType.set(true);
                    }
                } else if (defaultableParamNode.typeName().kind().equals(SyntaxKind.TABLE_TYPE_DESC)) {
                    if (((TypeParameterNode) ((TableTypeDescriptorNode) defaultableParamNode.typeName())
                            .rowTypeParameterNode()).typeNode().kind().equals(SyntaxKind.MAP_TYPE_DESC)) {
                        if (httpSupportedTypes.contains(((MapTypeDescriptorNode) ((TypeParameterNode)
                                ((TableTypeDescriptorNode) defaultableParamNode.typeName()).rowTypeParameterNode())
                                .typeNode()).mapTypeParamsNode().typeNode().kind())) {
                            foundSupportedType.set(true);
                        }
                    }
                } else if (defaultableParamNode.typeName().kind().equals(SyntaxKind.SIMPLE_NAME_REFERENCE)) {
                    if (syntaxNodeAnalysisContext.semanticModel().symbol(defaultableParamNode.typeName()).isPresent()) {
                        TypeSymbol typeSymbol =((TypeReferenceTypeSymbol) syntaxNodeAnalysisContext.semanticModel()
                                .symbol(defaultableParamNode.typeName()).get()).typeDescriptor();
                        boolean isSupportedTypeRef = isSupportedTypeReference(typeSymbol);
                        if (isSupportedTypeRef) {
                            foundSupportedType.set(true);
                        } else {
                            foundUnsupportedType.set(true);
                        }
                    } else {
                        foundUnsupportedType.set(true);
                    }
                } else if (httpSupportedTypes.contains(defaultableParamNode.typeName().kind())) {
                    foundSupportedType.set(true);
                } else {
                    foundUnsupportedType.set(true);
                }
            } else if (param.kind().equals(SyntaxKind.REST_PARAM)) {
                RestParameterNode restParamNode = (RestParameterNode) param;
                if (restParamNode.typeName().kind().equals(SyntaxKind.ARRAY_TYPE_DESC)) {
                    if (httpSupportedTypes.contains(((ArrayTypeDescriptorNode) restParamNode.typeName())
                            .memberTypeDesc().kind())) {
                        foundSupportedType.set(true);
                    }
                } else if (restParamNode.typeName().kind().equals(SyntaxKind.MAP_TYPE_DESC)) {
                    if (httpSupportedTypes.contains(((MapTypeDescriptorNode) restParamNode.typeName())
                            .mapTypeParamsNode().typeNode().kind())) {
                        foundSupportedType.set(true);
                    }
                } else if (restParamNode.typeName().kind().equals(SyntaxKind.TABLE_TYPE_DESC)) {
                    if (((TypeParameterNode) ((TableTypeDescriptorNode) restParamNode.typeName())
                            .rowTypeParameterNode()).typeNode().kind().equals(SyntaxKind.MAP_TYPE_DESC)) {
                        if (httpSupportedTypes.contains(((MapTypeDescriptorNode) ((TypeParameterNode)
                                ((TableTypeDescriptorNode) restParamNode.typeName()).rowTypeParameterNode())
                                .typeNode()).mapTypeParamsNode().typeNode().kind())) {
                            foundSupportedType.set(true);
                        }
                    }
                } else if (restParamNode.typeName().kind().equals(SyntaxKind.SIMPLE_NAME_REFERENCE)) {
                    if (syntaxNodeAnalysisContext.semanticModel().symbol(restParamNode.typeName()).isPresent()) {
                        TypeSymbol typeSymbol =((TypeReferenceTypeSymbol) syntaxNodeAnalysisContext.semanticModel()
                                .symbol(restParamNode.typeName()).get()).typeDescriptor();
                        boolean isSupportedTypeRef = isSupportedTypeReference(typeSymbol);
                        if (isSupportedTypeRef) {
                            foundSupportedType.set(true);
                        } else {
                            foundUnsupportedType.set(true);
                        }
                    } else {
                        foundUnsupportedType.set(true);
                    }
                } else if (httpSupportedTypes.contains(restParamNode.typeName().kind())) {
                    foundSupportedType.set(true);
                } else {
                    foundUnsupportedType.set(true);
                }
            }
        });
        return foundSupportedType.get() && !foundUnsupportedType.get();
    }

    private boolean isReturnTypeSupported(FunctionDefinitionNode funcDefNode,
                                          SyntaxNodeAnalysisContext syntaxNodeAnalysisContext) {
        AtomicBoolean foundSupportedType = new AtomicBoolean(false);
        AtomicBoolean foundUnsupportedType = new AtomicBoolean(false);
        if (funcDefNode.functionSignature().returnTypeDesc().isPresent()) {
            ReturnTypeDescriptorNode returnTypeDescNode = funcDefNode.functionSignature().returnTypeDesc().get();
            if (returnTypeDescNode.type().kind().equals(SyntaxKind.ARRAY_TYPE_DESC)) {
                if (httpSupportedTypes.contains(((ArrayTypeDescriptorNode) returnTypeDescNode.type())
                        .memberTypeDesc().kind())) {
                    foundSupportedType.set(true);
                }
            } else if (funcDefNode.functionSignature().returnTypeDesc().get().type().kind()
                    .equals(SyntaxKind.ARRAY_TYPE_DESC)) {
                if (httpSupportedTypes.contains(((MapTypeDescriptorNode) returnTypeDescNode.type())
                        .mapTypeParamsNode().typeNode().kind())) {
                    foundSupportedType.set(true);
                }
            } else if (funcDefNode.functionSignature().returnTypeDesc().get().type().kind()
                    .equals(SyntaxKind.ARRAY_TYPE_DESC)) {
                if (((TypeParameterNode) ((TableTypeDescriptorNode) returnTypeDescNode.type())
                        .rowTypeParameterNode()).typeNode().kind().equals(SyntaxKind.MAP_TYPE_DESC)) {
                    if (httpSupportedTypes.contains(((MapTypeDescriptorNode) ((TypeParameterNode)
                            ((TableTypeDescriptorNode) returnTypeDescNode.type()).rowTypeParameterNode())
                            .typeNode()).mapTypeParamsNode().typeNode().kind())) {
                        foundSupportedType.set(true);
                    }
                }
            } else if (funcDefNode.functionSignature().returnTypeDesc().get().type().kind()
                    .equals(SyntaxKind.SIMPLE_NAME_REFERENCE)) {
                if (syntaxNodeAnalysisContext.semanticModel().symbol(funcDefNode.functionSignature().returnTypeDesc()
                        .get().type()).isPresent()) {
                    TypeSymbol typeSymbol =((TypeReferenceTypeSymbol) syntaxNodeAnalysisContext.semanticModel()
                            .symbol(funcDefNode.functionSignature().returnTypeDesc().get().type()).get())
                            .typeDescriptor();
                    boolean isSupportedTypeRef = isSupportedTypeReference(typeSymbol);
                    if (isSupportedTypeRef) {
                        foundSupportedType.set(true);
                    } else {
                        foundUnsupportedType.set(true);
                    }
                } else {
                    foundUnsupportedType.set(true);
                }
            } else if (httpSupportedTypes
                    .contains(funcDefNode.functionSignature().returnTypeDesc().get().type().kind())) {
                foundSupportedType.set(true);
            } else {
                foundUnsupportedType.set(true);
            }
        } else {
            foundSupportedType.set(true);
        }
        return foundSupportedType.get() && !foundUnsupportedType.get();
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
