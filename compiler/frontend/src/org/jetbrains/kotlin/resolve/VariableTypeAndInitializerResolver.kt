/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.resolve

import org.jetbrains.kotlin.descriptors.VariableDescriptorWithAccessors
import org.jetbrains.kotlin.descriptors.impl.VariableDescriptorWithInitializerImpl
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.diagnostics.Errors.VARIABLE_WITH_NO_TYPE_NO_INITIALIZER
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtVariableDeclaration
import org.jetbrains.kotlin.resolve.DescriptorResolver.transformAnonymousTypeIfNeeded
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowInfo
import org.jetbrains.kotlin.resolve.constants.evaluate.ConstantExpressionEvaluator
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.resolve.scopes.ScopeUtils
import org.jetbrains.kotlin.storage.StorageManager
import org.jetbrains.kotlin.types.DeferredType
import org.jetbrains.kotlin.types.ErrorUtils
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.expressions.ExpressionTypingServices
import org.jetbrains.kotlin.types.expressions.PreliminaryDeclarationVisitor

class VariableTypeAndInitializerResolver(
        private val storageManager: StorageManager,
        private val expressionTypingServices: ExpressionTypingServices,
        private val typeResolver: TypeResolver,
        private val constantExpressionEvaluator: ConstantExpressionEvaluator,
        private val delegatedPropertyResolver: DelegatedPropertyResolver
) {

    fun resolveType(
            variableDescriptor: VariableDescriptorWithInitializerImpl,
            scopeForInitializer: LexicalScope,
            variable: KtVariableDeclaration,
            dataFlowInfo: DataFlowInfo,
            notLocal: Boolean,
            trace: BindingTrace
    ): KotlinType {
        val propertyTypeRef = variable.typeReference

        val hasDelegate = variable is KtProperty && variable.hasDelegateExpression()
        return when {
            propertyTypeRef != null -> typeResolver.resolveType(scopeForInitializer, propertyTypeRef, trace, true)
            !variable.hasInitializer() -> {
                if (hasDelegate && variableDescriptor is VariableDescriptorWithAccessors) {
                    val property = variable as KtProperty
                    if (property.hasDelegateExpression()) {
                        return DeferredType.createRecursionIntolerant(
                                storageManager,
                                trace
                        ) {
                            resolveDelegatedPropertyType(property, variableDescriptor, scopeForInitializer,
                                                         property.delegateExpression!!, dataFlowInfo, trace)
                        }
                    }
                }
                if (!notLocal) {
                    trace.report(VARIABLE_WITH_NO_TYPE_NO_INITIALIZER.on(variable))
                }
                return ErrorUtils.createErrorType("No type, no body")
            }
            notLocal -> {
                DeferredType.createRecursionIntolerant(
                        storageManager,
                        trace
                ) {
                    PreliminaryDeclarationVisitor.createForDeclaration(variable, trace)
                    val initializerType = resolveInitializerType(scopeForInitializer, variable.initializer!!, dataFlowInfo, trace)
                    transformAnonymousTypeIfNeeded(variableDescriptor, variable, initializerType, trace)
                }
            }
            else -> resolveInitializerType(scopeForInitializer, variable.initializer!!, dataFlowInfo, trace)
        }
    }

    fun setConstantForVariableIfNeeded(
            variableDescriptor: VariableDescriptorWithInitializerImpl,
            scope: LexicalScope,
            variable: KtVariableDeclaration,
            dataFlowInfo: DataFlowInfo,
            variableType: KotlinType,
            trace: BindingTrace
    ) {
        if (!variable.hasInitializer() || variable.isVar) return
        variableDescriptor.setCompileTimeInitializer(
                storageManager.createRecursionTolerantNullableLazyValue(
                        computeInitializer@{
                            if (!DescriptorUtils.shouldRecordInitializerForProperty(variableDescriptor, variableType)) return@computeInitializer null

                            val initializer = variable.initializer
                            val initializerType = expressionTypingServices.safeGetType(scope, initializer!!, variableType, dataFlowInfo, trace)
                            val constant = constantExpressionEvaluator.evaluateExpression(initializer, trace, initializerType)
                                           ?: return@computeInitializer null

                            if (constant.usesNonConstValAsConstant && variableDescriptor.isConst) {
                                trace.report(Errors.NON_CONST_VAL_USED_IN_CONSTANT_EXPRESSION.on(initializer))
                            }

                            constant.toConstantValue(initializerType)
                        },
                        null
                )
        )
    }

    private fun resolveDelegatedPropertyType(
            property: KtProperty,
            variableDescriptor: VariableDescriptorWithAccessors,
            scopeForInitializer: LexicalScope,
            delegateExpression: KtExpression,
            dataFlowInfo: DataFlowInfo,
            trace: BindingTrace
    ): KotlinType {
        val type = delegatedPropertyResolver.resolveDelegateExpression(
                delegateExpression, property, variableDescriptor, scopeForInitializer, trace, dataFlowInfo)

        val delegateFunctionsScope = ScopeUtils.makeScopeForDelegateConventionFunctions(scopeForInitializer, variableDescriptor)
        val getterReturnType = delegatedPropertyResolver.getDelegatedPropertyGetMethodReturnType(
                variableDescriptor, delegateExpression, type, trace, delegateFunctionsScope, dataFlowInfo
        )
        if (getterReturnType != null) {
            return getterReturnType
        }
        return ErrorUtils.createErrorType("Type from delegate")
    }

    private fun resolveInitializerType(
            scope: LexicalScope,
            initializer: KtExpression,
            dataFlowInfo: DataFlowInfo,
            trace: BindingTrace
    ): KotlinType {
        return expressionTypingServices.safeGetType(scope, initializer, TypeUtils.NO_EXPECTED_TYPE, dataFlowInfo, trace)
    }
}
