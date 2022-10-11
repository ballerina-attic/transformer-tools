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

package io.ballerina.transformer.plugin.diagnostic;

import io.ballerina.tools.diagnostics.DiagnosticSeverity;

/**
 * Transformer module Diagnostic messages.
 *
 * @since 1.1.0
 */
public enum DiagnosticMessage {
    ERROR_100("TRANSFORMER_ERROR_100",
            "Transformer packages are not allowed to have entry points.",
            DiagnosticSeverity.ERROR),
    ERROR_101("TRANSFORMER_ERROR_101",
            "Public qualifiers are allowed only for expression bodied functions.",
            DiagnosticSeverity.ERROR),
    ERROR_102("TRANSFORMER_ERROR_102",
            "Transformer packages are not allowed to have listeners.",
            DiagnosticSeverity.ERROR),
    ERROR_103("TRANSFORMER_ERROR_103",
            "Transformer packages are not allowed to have classes.",
            DiagnosticSeverity.ERROR),
    ERROR_104("TRANSFORMER_ERROR_104",
            "Transformer packages are not allowed to have services.",
            DiagnosticSeverity.ERROR),
    ERROR_105("TRANSFORMER_ERROR_105",
            "Transformer packages should have more than one public and isolated expression bodied functions.",
            DiagnosticSeverity.ERROR),
    ERROR_106("TRANSFORMER_ERROR_106",
            "Transformer packages are not allowed to have annotations.",
            DiagnosticSeverity.ERROR),
    ERROR_107("TRANSFORMER_ERROR_107",
                      "Transformer packages contains non-service generatable functions.",
              DiagnosticSeverity.ERROR);

    private final String code;
    private final String messageFormat;
    private final DiagnosticSeverity severity;

    DiagnosticMessage(String code, String messageFormat, DiagnosticSeverity severity) {
        this.code = code;
        this.messageFormat = messageFormat;
        this.severity = severity;
    }

    public String getCode() {
        return this.code;
    }

    public String getMessageFormat() {
        return this.messageFormat;
    }

    public DiagnosticSeverity getSeverity() {
        return this.severity;
    }
}
