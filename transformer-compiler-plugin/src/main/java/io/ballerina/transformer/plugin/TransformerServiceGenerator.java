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

import io.ballerina.compiler.syntax.tree.AbstractNodeFactory;
import io.ballerina.compiler.syntax.tree.AnnotationNode;
import io.ballerina.compiler.syntax.tree.ArrayDimensionNode;
import io.ballerina.compiler.syntax.tree.BasicLiteralNode;
import io.ballerina.compiler.syntax.tree.BinaryExpressionNode;
import io.ballerina.compiler.syntax.tree.BindingPatternNode;
import io.ballerina.compiler.syntax.tree.BuiltinSimpleNameReferenceNode;
import io.ballerina.compiler.syntax.tree.DefaultableParameterNode;
import io.ballerina.compiler.syntax.tree.ExplicitNewExpressionNode;
import io.ballerina.compiler.syntax.tree.ExpressionNode;
import io.ballerina.compiler.syntax.tree.FieldAccessExpressionNode;
import io.ballerina.compiler.syntax.tree.FunctionArgumentNode;
import io.ballerina.compiler.syntax.tree.FunctionBodyNode;
import io.ballerina.compiler.syntax.tree.FunctionCallExpressionNode;
import io.ballerina.compiler.syntax.tree.FunctionDefinitionNode;
import io.ballerina.compiler.syntax.tree.FunctionSignatureNode;
import io.ballerina.compiler.syntax.tree.IdentifierToken;
import io.ballerina.compiler.syntax.tree.ImportDeclarationNode;
import io.ballerina.compiler.syntax.tree.ImportOrgNameNode;
import io.ballerina.compiler.syntax.tree.ListConstructorExpressionNode;
import io.ballerina.compiler.syntax.tree.LiteralValueToken;
import io.ballerina.compiler.syntax.tree.MinutiaeList;
import io.ballerina.compiler.syntax.tree.ModuleMemberDeclarationNode;
import io.ballerina.compiler.syntax.tree.ModulePartNode;
import io.ballerina.compiler.syntax.tree.ModuleVariableDeclarationNode;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.NodeFactory;
import io.ballerina.compiler.syntax.tree.NodeList;
import io.ballerina.compiler.syntax.tree.ParameterNode;
import io.ballerina.compiler.syntax.tree.ParenthesizedArgList;
import io.ballerina.compiler.syntax.tree.PositionalArgumentNode;
import io.ballerina.compiler.syntax.tree.QualifiedNameReferenceNode;
import io.ballerina.compiler.syntax.tree.RecordFieldNode;
import io.ballerina.compiler.syntax.tree.RecordTypeDescriptorNode;
import io.ballerina.compiler.syntax.tree.RequiredParameterNode;
import io.ballerina.compiler.syntax.tree.RestArgumentNode;
import io.ballerina.compiler.syntax.tree.RestParameterNode;
import io.ballerina.compiler.syntax.tree.ReturnStatementNode;
import io.ballerina.compiler.syntax.tree.ReturnTypeDescriptorNode;
import io.ballerina.compiler.syntax.tree.SeparatedNodeList;
import io.ballerina.compiler.syntax.tree.ServiceDeclarationNode;
import io.ballerina.compiler.syntax.tree.SimpleNameReferenceNode;
import io.ballerina.compiler.syntax.tree.StatementNode;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.compiler.syntax.tree.Token;
import io.ballerina.compiler.syntax.tree.TypeDefinitionNode;
import io.ballerina.compiler.syntax.tree.TypeDescriptorNode;
import io.ballerina.compiler.syntax.tree.TypedBindingPatternNode;
import io.ballerina.compiler.syntax.tree.UnionTypeDescriptorNode;
import io.ballerina.projects.plugins.GeneratorTask;
import io.ballerina.projects.plugins.SourceGeneratorContext;
import io.ballerina.tools.text.TextDocument;
import io.ballerina.tools.text.TextDocuments;
import org.ballerinalang.formatter.core.Formatter;
import org.ballerinalang.formatter.core.FormatterException;

import java.util.ArrayList;
import java.util.List;

/**
 * Transformer module Service Generator.
 *
 */
public class TransformerServiceGenerator implements GeneratorTask<SourceGeneratorContext> {

    private static final String PAYLOAD_KEYWORD = "Payload";
    private static final String PAYLOAD_TOKEN = "payload";
    private static final String HTTP_KEYWORD = "http";
    private static final String LISTENER_KEYWORD = "Listener";
    private static final String PORT_KEYWORD = "port";
    private static final String POST_KEYWORD = "post";
    private final List<FunctionDefinitionNode> transformerFunctions;

    TransformerServiceGenerator(List<FunctionDefinitionNode> transformerFunctions) {
        this.transformerFunctions = transformerFunctions;
    }

    @Override
    public void generate(SourceGeneratorContext sourceGeneratorContext) {
        // TODO: Change the Listener Port to be configurable in Ballerina.toml
        String balServiceCode = generateCode(transformerFunctions);
        TextDocument textDocument = TextDocuments.from(balServiceCode);
        sourceGeneratorContext.addSourceFile(textDocument, "service");
    }

    /**
     * This method returns generated code for the given transformer functions.
     *
     * @return {@link String} Generated code for the given transformer functions
     */
    private String generateCode(List<FunctionDefinitionNode> transformerFunctions) {
        try {
            Token importKeyword = AbstractNodeFactory.createToken(SyntaxKind.IMPORT_KEYWORD);
            Token orgNameToken = AbstractNodeFactory.createIdentifierToken("ballerina");
            Token slashToken = AbstractNodeFactory.createToken(SyntaxKind.SLASH_TOKEN);
            ImportOrgNameNode orgNameNode = NodeFactory.createImportOrgNameNode(orgNameToken, slashToken);
            IdentifierToken httpKeyword = AbstractNodeFactory.createIdentifierToken(HTTP_KEYWORD);
            SeparatedNodeList<IdentifierToken> moduleName =
                    AbstractNodeFactory.createSeparatedNodeList(List.of(httpKeyword));
            Token semicolonToken = AbstractNodeFactory.createToken(SyntaxKind.SEMICOLON_TOKEN);
            ImportDeclarationNode httpImport =
                    NodeFactory.createImportDeclarationNode(importKeyword, orgNameNode, moduleName, null,
                            semicolonToken);
            NodeList<ImportDeclarationNode> imports = AbstractNodeFactory.createNodeList(List.of(httpImport));
            List<TypeDefinitionNode> typeDefNodes = new ArrayList<>();
            transformerFunctions.forEach(transformerFunc -> {
                if (transformerFunc.functionSignature().parameters().size() > 0) {
                    typeDefNodes.add(generatePayloadRecord(transformerFunc));
                }
            });
            List<ModuleMemberDeclarationNode> moduleMembers = new ArrayList<>();
            moduleMembers.add(generateConfigurable());
            moduleMembers.add(generateService(transformerFunctions));
            moduleMembers.addAll(typeDefNodes);
            NodeList<ModuleMemberDeclarationNode> moduleMemberNodes = AbstractNodeFactory.createNodeList(moduleMembers);
            Token eofToken = AbstractNodeFactory.createIdentifierToken("");
            ModulePartNode modulePartNode = NodeFactory.createModulePartNode(imports, moduleMemberNodes, eofToken);
            return Formatter.format(modulePartNode.syntaxTree()).toSourceCode();
        } catch (FormatterException e) {
            return null;
        }
    }

    /**
     * This method returns ModuleVariableDeclarationNode which defines the configurable port number.
     *
     * @return {@link ModuleVariableDeclarationNode}
     * Generated ModuleVariableDeclarationNode for configuring port number
     */
    private ModuleVariableDeclarationNode generateConfigurable() {
        Token configToken = NodeFactory.createToken(SyntaxKind.CONFIGURABLE_KEYWORD);
        List<Token> qualifiers = new ArrayList<>();
        qualifiers.add(configToken);

        NodeList<Token> qualifierNodes = AbstractNodeFactory.createNodeList(qualifiers);
        Token typeName = AbstractNodeFactory.createToken(SyntaxKind.INT_KEYWORD);
        IdentifierToken fieldName = AbstractNodeFactory.createIdentifierToken(PORT_KEYWORD);
        SimpleNameReferenceNode variableName = NodeFactory.createSimpleNameReferenceNode(fieldName);
        TypeDescriptorNode typeDescNode = NodeFactory.createBuiltinSimpleNameReferenceNode(typeName.kind(), typeName);
        BindingPatternNode bindingPatternNode = NodeFactory.createFieldBindingPatternVarnameNode(variableName);
        TypedBindingPatternNode typedBindingPatternNode =
                NodeFactory.createTypedBindingPatternNode(typeDescNode, bindingPatternNode);
        Token equalsToken = AbstractNodeFactory.createToken(SyntaxKind.EQUAL_TOKEN);
        MinutiaeList minList = AbstractNodeFactory.createEmptyMinutiaeList();
        LiteralValueToken valToken =
                NodeFactory.createLiteralValueToken(SyntaxKind.DECIMAL_INTEGER_LITERAL_TOKEN, "8080",
                        minList, minList);
        BasicLiteralNode basicLiteralNode = NodeFactory.createBasicLiteralNode(SyntaxKind.NUMERIC_LITERAL, valToken);
        Token semicolonToken = AbstractNodeFactory.createToken(SyntaxKind.SEMICOLON_TOKEN);
        return NodeFactory.createModuleVariableDeclarationNode(null, null, qualifierNodes,
                typedBindingPatternNode, equalsToken, basicLiteralNode, semicolonToken);
    }

    /**
     * This method returns ServiceDeclarationNode for the transformer function nodes.
     *
     * @param transformerFunctions List of transformer functions for which resource functions to be generated
     * @return {@link ServiceDeclarationNode} Generated ServiceDeclarationNode
     */
    private ServiceDeclarationNode generateService(List<FunctionDefinitionNode> transformerFunctions) {
        NodeList<Token> qualifierNodes = AbstractNodeFactory.createEmptyNodeList();
        Token serviceKeyword = AbstractNodeFactory.createToken(SyntaxKind.SERVICE_KEYWORD);

        Token resourcePath = NodeFactory.createToken(SyntaxKind.SLASH_TOKEN);
        NodeList<Node> absoluteResourcePathNodes = AbstractNodeFactory.createNodeList(resourcePath);

        Token onKeyword = AbstractNodeFactory.createToken(SyntaxKind.ON_KEYWORD);
        Token newKeyword = AbstractNodeFactory.createToken(SyntaxKind.NEW_KEYWORD);

        IdentifierToken modulePrefix = AbstractNodeFactory.createIdentifierToken(HTTP_KEYWORD);
        Token colonToken = AbstractNodeFactory.createToken(SyntaxKind.COLON_TOKEN);
        IdentifierToken listenerIdentifier = AbstractNodeFactory.createIdentifierToken(LISTENER_KEYWORD);
        TypeDescriptorNode httpListenerTypeDescNode =
                NodeFactory.createQualifiedNameReferenceNode(modulePrefix, colonToken, listenerIdentifier);

        Token opParenToken = AbstractNodeFactory.createToken(SyntaxKind.OPEN_PAREN_TOKEN);
        Token positionalArgNameToken = AbstractNodeFactory.createIdentifierToken(PORT_KEYWORD);
        SimpleNameReferenceNode positionalArgExprNode =
                NodeFactory.createSimpleNameReferenceNode(positionalArgNameToken);
        PositionalArgumentNode newHTTPListenerExprArgNode =
                NodeFactory.createPositionalArgumentNode(positionalArgExprNode);
        SeparatedNodeList<FunctionArgumentNode> newHTTPListenerExprArgNodes =
                AbstractNodeFactory.createSeparatedNodeList(newHTTPListenerExprArgNode);
        Token clParenToken = AbstractNodeFactory.createToken(SyntaxKind.CLOSE_PAREN_TOKEN);
        ParenthesizedArgList newHTTPListenerExprArgs =
                NodeFactory.createParenthesizedArgList(opParenToken, newHTTPListenerExprArgNodes, clParenToken);
        ExplicitNewExpressionNode newHTTPListenerExprNode = NodeFactory
                .createExplicitNewExpressionNode(newKeyword, httpListenerTypeDescNode, newHTTPListenerExprArgs);
        SeparatedNodeList<ExpressionNode> expressionNodes =
                AbstractNodeFactory.createSeparatedNodeList(newHTTPListenerExprNode);

        Token opBraceToken = AbstractNodeFactory.createToken(SyntaxKind.OPEN_BRACE_TOKEN);
        Token clBraceToken = AbstractNodeFactory.createToken(SyntaxKind.CLOSE_BRACE_TOKEN);
        List<Node> funcMembers = new ArrayList<>();
        for (FunctionDefinitionNode transformerFuncNode : transformerFunctions) {
            List<Token> functionQualifiers = new ArrayList<>();
            Token resourceKeyword = AbstractNodeFactory.createToken(SyntaxKind.RESOURCE_KEYWORD);
            functionQualifiers.add(resourceKeyword);
            NodeList<Token> functionQualifierNodes = AbstractNodeFactory.createNodeList(functionQualifiers);
            Token functionKeyword = AbstractNodeFactory.createToken(SyntaxKind.FUNCTION_KEYWORD);
            IdentifierToken functionName = AbstractNodeFactory.createIdentifierToken(POST_KEYWORD);
            List<Node> relativeResourcePaths = new ArrayList<>();
            IdentifierToken relativeResourcePathToken =
                    AbstractNodeFactory.createIdentifierToken(transformerFuncNode.functionName().text());
            relativeResourcePaths.add(relativeResourcePathToken);
            NodeList<Node> relativeResourcePathNodes = AbstractNodeFactory.createNodeList(relativeResourcePaths);

            Token atToken = AbstractNodeFactory.createToken(SyntaxKind.AT_TOKEN);
            IdentifierToken annotationToken = AbstractNodeFactory.createIdentifierToken(PAYLOAD_KEYWORD);
            QualifiedNameReferenceNode annotationReferenceNode =
                    NodeFactory.createQualifiedNameReferenceNode(modulePrefix, colonToken, annotationToken);
            AnnotationNode annotationNode = NodeFactory.createAnnotationNode(atToken, annotationReferenceNode, null);
            NodeList<AnnotationNode> annotationNodes = AbstractNodeFactory.createNodeList(annotationNode);

            SeparatedNodeList<ParameterNode> parameterNodes = AbstractNodeFactory.createSeparatedNodeList();
            if (transformerFuncNode.functionSignature().parameters().size() > 0) {
                IdentifierToken typeName = AbstractNodeFactory
                        .createIdentifierToken(transformerFuncNode.functionName().text() + PAYLOAD_KEYWORD);
                SimpleNameReferenceNode typeNameNode = NodeFactory.createSimpleNameReferenceNode(typeName);
                IdentifierToken paramName = AbstractNodeFactory.createIdentifierToken(PAYLOAD_TOKEN);
                RequiredParameterNode requiredParamNode =
                        NodeFactory.createRequiredParameterNode(annotationNodes, typeNameNode, paramName);
                parameterNodes = AbstractNodeFactory.createSeparatedNodeList(requiredParamNode);
            }

            Token returnsKeyword = AbstractNodeFactory.createToken(SyntaxKind.RETURNS_KEYWORD);
            NodeList<AnnotationNode> returnTypeAnnotations = AbstractNodeFactory.createEmptyNodeList();

            boolean isReturnTypeDescNodePresent =
                    transformerFuncNode.functionSignature().returnTypeDesc().isPresent() &&
                            !transformerFuncNode.functionSignature().returnTypeDesc().get().type()
                                    .kind().equals(SyntaxKind.NIL_TYPE_DESC);
            TypeDescriptorNode leftTypeNameNode = isReturnTypeDescNodePresent ?
                    NodeFactory.createSimpleNameReferenceNode(AbstractNodeFactory
                            .createToken(((BuiltinSimpleNameReferenceNode) transformerFuncNode.functionSignature()
                                    .returnTypeDesc().get().type()).name().kind())) :
                    NodeFactory.createNilTypeDescriptorNode(opParenToken, clParenToken);
            Token pipeToken = AbstractNodeFactory.createToken(SyntaxKind.PIPE_TOKEN);
            Token rightTypeName = AbstractNodeFactory.createToken(SyntaxKind.ERROR_KEYWORD);
            SimpleNameReferenceNode rightTypeNameNode = NodeFactory.createSimpleNameReferenceNode(rightTypeName);
            UnionTypeDescriptorNode unionTypeDescNode =
                    NodeFactory.createUnionTypeDescriptorNode(leftTypeNameNode, pipeToken, rightTypeNameNode);
            ReturnTypeDescriptorNode returnTypeDescNode =
                    NodeFactory.createReturnTypeDescriptorNode(returnsKeyword, returnTypeAnnotations,
                            unionTypeDescNode);
            FunctionSignatureNode funcSignatureNode =
                    NodeFactory.createFunctionSignatureNode(opParenToken, parameterNodes, clParenToken,
                            returnTypeDescNode);

            IdentifierToken funcNameToken =
                    AbstractNodeFactory.createIdentifierToken(transformerFuncNode.functionName().text());
            SimpleNameReferenceNode funcNameNode = NodeFactory.createSimpleNameReferenceNode(funcNameToken);

            List<Node> funcArgNodes = new ArrayList<>();
            transformerFuncNode.functionSignature().parameters().forEach(param -> {
                IdentifierToken expressionName = NodeFactory.createIdentifierToken("payload");
                SimpleNameReferenceNode methodExpressionNode =
                        NodeFactory.createSimpleNameReferenceNode(expressionName);
                Token dotToken = AbstractNodeFactory.createToken(SyntaxKind.DOT_TOKEN);
                Token elvisToken = AbstractNodeFactory.createToken(SyntaxKind.ELVIS_TOKEN);
                if (param.kind().equals(SyntaxKind.REQUIRED_PARAM)) {
                    RequiredParameterNode requiredParamNode = (RequiredParameterNode) param;
                    Token defaultFieldName = AbstractNodeFactory.createIdentifierToken("defaultName");
                    IdentifierToken fieldName = AbstractNodeFactory
                            .createIdentifierToken(requiredParamNode.paramName().orElse(defaultFieldName).text());
                    SimpleNameReferenceNode fieldNameRefNode = NodeFactory.createSimpleNameReferenceNode(fieldName);
                    FieldAccessExpressionNode fieldAccessExprNode =
                            NodeFactory.createFieldAccessExpressionNode(methodExpressionNode, dotToken,
                                    fieldNameRefNode);
                    PositionalArgumentNode positionalArgNode =
                            NodeFactory.createPositionalArgumentNode(fieldAccessExprNode);
                    funcArgNodes.add(positionalArgNode);
                } else if (param.kind().equals(SyntaxKind.DEFAULTABLE_PARAM)) {
                    DefaultableParameterNode requiredParamNode = (DefaultableParameterNode) param;
                    Token defaultFieldName = AbstractNodeFactory.createIdentifierToken("defaultName");
                    IdentifierToken fieldName = AbstractNodeFactory
                            .createIdentifierToken(requiredParamNode.paramName().orElse(defaultFieldName).text());
                    SimpleNameReferenceNode fieldNameRefNode = NodeFactory.createSimpleNameReferenceNode(fieldName);
                    FieldAccessExpressionNode fieldAccessExprNode =
                            NodeFactory.createFieldAccessExpressionNode(methodExpressionNode,
                                    dotToken, fieldNameRefNode);
                    BinaryExpressionNode binExprNode =
                            NodeFactory.createBinaryExpressionNode(SyntaxKind.BINARY_EXPRESSION,
                                    fieldAccessExprNode, elvisToken, requiredParamNode.expression());
                    PositionalArgumentNode positionalArgNode =
                            NodeFactory.createPositionalArgumentNode(binExprNode);
                    funcArgNodes.add(positionalArgNode);
                } else if (param.kind().equals(SyntaxKind.REST_PARAM)) {
                    Token ellipsisToken = AbstractNodeFactory.createToken(SyntaxKind.ELLIPSIS_TOKEN);
                    RestParameterNode restParamNode = (RestParameterNode) param;
                    Token defaultFieldName = AbstractNodeFactory.createIdentifierToken("defaultName");
                    IdentifierToken fieldName = AbstractNodeFactory
                            .createIdentifierToken(restParamNode.paramName().orElse(defaultFieldName).text());
                    SimpleNameReferenceNode fieldNameRefNode = NodeFactory.createSimpleNameReferenceNode(fieldName);
                    FieldAccessExpressionNode fieldAccessExprNode =
                            NodeFactory.createFieldAccessExpressionNode(methodExpressionNode,
                                    dotToken, fieldNameRefNode);
                    Token openSBracketToken = AbstractNodeFactory.createToken(SyntaxKind.OPEN_BRACKET_TOKEN);
                    Token closeSBracketToken = AbstractNodeFactory.createToken(SyntaxKind.CLOSE_BRACKET_TOKEN);
                    SeparatedNodeList<Node> rhsNodeExpressions = NodeFactory.createSeparatedNodeList();
                    ListConstructorExpressionNode rhsNode = NodeFactory.createListConstructorExpressionNode(
                            openSBracketToken, rhsNodeExpressions, closeSBracketToken);
                    BinaryExpressionNode binExprNode =
                            NodeFactory.createBinaryExpressionNode(SyntaxKind.BINARY_EXPRESSION,
                                    fieldAccessExprNode, elvisToken, rhsNode);
                    RestArgumentNode restArgNode = NodeFactory.createRestArgumentNode(ellipsisToken, binExprNode);
                    funcArgNodes.add(restArgNode);
                }
            });

            Node[] newNodes = new Node[funcArgNodes.size()];
            if (funcArgNodes.size() > 0) {
                newNodes = new Node[funcArgNodes.size() * 2 - 1];
            }

            for (int index = 0; index < funcArgNodes.size(); index++) {
                Node node = funcArgNodes.get(index);
                newNodes[2 * index] = node;

                if (index == funcArgNodes.size() - 1) {
                    break;
                }

                Token separator = NodeFactory.createToken(SyntaxKind.COMMA_TOKEN);
                newNodes[(2 * index) + 1] = separator;
            }

            SeparatedNodeList<FunctionArgumentNode> argumentNodes = NodeFactory.createSeparatedNodeList(newNodes);

            FunctionCallExpressionNode expressionNode = NodeFactory.createFunctionCallExpressionNode(funcNameNode,
                    opParenToken, argumentNodes, clParenToken);
            Token semicolonToken = NodeFactory.createToken(SyntaxKind.SEMICOLON_TOKEN);
            Token returnKeyword = AbstractNodeFactory.createToken(SyntaxKind.RETURN_KEYWORD);
            ReturnStatementNode returnStatementNode = NodeFactory.createReturnStatementNode(returnKeyword,
                    expressionNode, semicolonToken);
            NodeList<StatementNode> statements = AbstractNodeFactory.createNodeList(returnStatementNode);
            FunctionBodyNode funcBodyNode = NodeFactory.createFunctionBodyBlockNode(opBraceToken, null,
                    statements, clBraceToken);
            FunctionDefinitionNode funcDefNode =
                    NodeFactory.createFunctionDefinitionNode(null, null, functionQualifierNodes, functionKeyword,
                            functionName, relativeResourcePathNodes, funcSignatureNode, funcBodyNode);
            funcMembers.add(funcDefNode);
        }
        NodeList<Node> members = AbstractNodeFactory.createNodeList(funcMembers);

        return NodeFactory.createServiceDeclarationNode(null, qualifierNodes, serviceKeyword,
                null, absoluteResourcePathNodes, onKeyword, expressionNodes, opBraceToken, members,
                clBraceToken);
    }

    /**
     * This method returns Payload Record node for the given function definition node.
     *
     * @param funcDefNode Function definition node for which the Record to be generated
     * @return {@link TypeDefinitionNode} Generated Payload Record TypeDefinitionNode
     */
    private TypeDefinitionNode generatePayloadRecord(FunctionDefinitionNode funcDefNode) {
        Token recordKeyWord = AbstractNodeFactory.createToken(SyntaxKind.RECORD_KEYWORD);
        Token bodyStartDelimiter = AbstractNodeFactory.createToken(SyntaxKind.OPEN_BRACE_TOKEN);
        Token bodyEndDelimiter = AbstractNodeFactory.createToken(SyntaxKind.CLOSE_BRACE_TOKEN);
        Token questionMarkToken = AbstractNodeFactory.createToken(SyntaxKind.QUESTION_MARK_TOKEN);
        Token semicolonToken = AbstractNodeFactory.createToken(SyntaxKind.SEMICOLON_TOKEN);

        List<Node> recordFields = new ArrayList<>();
        funcDefNode.functionSignature().parameters().forEach(funcParam -> {
            Token typeName = AbstractNodeFactory.createToken(SyntaxKind.ANYDATA_KEYWORD);
            TypeDescriptorNode fieldTypeName =
                    NodeFactory.createBuiltinSimpleNameReferenceNode(typeName.kind(), typeName);
            Token defaultFieldName = AbstractNodeFactory.createIdentifierToken("param");
            Token fieldName = defaultFieldName;
            RecordFieldNode recordFieldNode = NodeFactory.createRecordFieldNode(null, null,
                    fieldTypeName, fieldName, null, semicolonToken);
            if (funcParam.kind().equals(SyntaxKind.REQUIRED_PARAM)) {
                RequiredParameterNode requiredParamNode = (RequiredParameterNode) funcParam;
                fieldTypeName = (TypeDescriptorNode) requiredParamNode.typeName();
                fieldName = requiredParamNode.paramName().orElse(defaultFieldName);
                recordFieldNode = NodeFactory.createRecordFieldNode(null, null,
                        fieldTypeName, fieldName, null, semicolonToken);
            } else if (funcParam.kind().equals(SyntaxKind.DEFAULTABLE_PARAM)) {
                DefaultableParameterNode defaultableParamNode = (DefaultableParameterNode) funcParam;
                fieldTypeName = (TypeDescriptorNode) defaultableParamNode.typeName();
                fieldName = defaultableParamNode.paramName().orElse(defaultFieldName);

                recordFieldNode = NodeFactory.createRecordFieldNode(null, null,
                        fieldTypeName, fieldName, questionMarkToken, semicolonToken);
            } else if (funcParam.kind().equals(SyntaxKind.REST_PARAM)) {
                RestParameterNode restParamNode = (RestParameterNode) funcParam;
                fieldTypeName = (TypeDescriptorNode) restParamNode.typeName();
                NodeList<ArrayDimensionNode> arrayDimensions = NodeFactory.createEmptyNodeList();
                Token openSBracketToken = AbstractNodeFactory.createToken(SyntaxKind.OPEN_BRACKET_TOKEN);
                Token closeSBracketToken = AbstractNodeFactory.createToken(SyntaxKind.CLOSE_BRACKET_TOKEN);
                ArrayDimensionNode arrayDimension = NodeFactory.createArrayDimensionNode(openSBracketToken, null,
                        closeSBracketToken);
                arrayDimensions = arrayDimensions.add(arrayDimension);

                TypeDescriptorNode fieldTypeNameArray =
                        NodeFactory.createArrayTypeDescriptorNode(fieldTypeName, arrayDimensions);
                fieldName = restParamNode.paramName().orElse(defaultFieldName);
                recordFieldNode = NodeFactory.createRecordFieldNode(null, null,
                        fieldTypeNameArray, fieldName, questionMarkToken, semicolonToken);
            }
            recordFields.add(recordFieldNode);
        });

        Token publicKeyword = AbstractNodeFactory.createToken(SyntaxKind.PUBLIC_KEYWORD);
        Token typeKeyWord = AbstractNodeFactory.createToken(SyntaxKind.TYPE_KEYWORD);
        IdentifierToken typeName =
                AbstractNodeFactory.createIdentifierToken(funcDefNode.functionName().text() + PAYLOAD_KEYWORD);
        NodeList<Node> recordFieldNodes = AbstractNodeFactory.createNodeList(recordFields);
        RecordTypeDescriptorNode payloadRecordNode = NodeFactory.createRecordTypeDescriptorNode(recordKeyWord,
                bodyStartDelimiter, recordFieldNodes, null, bodyEndDelimiter);

        return NodeFactory.createTypeDefinitionNode(null, publicKeyword, typeKeyWord, typeName,
                payloadRecordNode, semicolonToken);
    }
}
