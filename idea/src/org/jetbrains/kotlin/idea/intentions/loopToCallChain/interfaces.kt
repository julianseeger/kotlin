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

package org.jetbrains.kotlin.idea.intentions.loopToCallChain

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.psiUtil.PsiChildRange
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.psi.psiUtil.siblings

/**
 * An abstraction for generating a chained call that knows about receiver expression and handles proper formatting
 */
interface ChainedCallGenerator {
    val receiver: KtExpression

    /**
     * @param pattern pattern string for generating the part of the call to the right from the dot
     */
    fun generate(pattern: String, vararg args: Any, receiver: KtExpression = this.receiver, safeCall: Boolean = false): KtExpression
}

/**
 * Base interface for recognized transformations of the sequence. Should always be either [SequenceTransformation] or [ResultTransformation]
 */
interface Transformation {
    val loop: KtForExpression

    val presentation: String

    fun buildPresentation(prevTransformationsPresentation: String?): String {
        return if (prevTransformationsPresentation != null)
            prevTransformationsPresentation + "." + presentation
        else
            presentation
    }

    fun mergeWithPrevious(previousTransformation: SequenceTransformation): Transformation?

    fun generateCode(chainedCallGenerator: ChainedCallGenerator): KtExpression

    val chainCallCount: Int
        get() = 1

    val shouldUseInputVariable: Boolean
        get() = true
}

/**
 * Represents a transformation of input sequence into another sequence
 */
interface SequenceTransformation : Transformation {
    override fun mergeWithPrevious(previousTransformation: SequenceTransformation): SequenceTransformation? = null

    val affectsIndex: Boolean
}

/**
 * Represents a final transformation of sequence which produces the result of the whole loop (for example, assigning a found value into a variable).
 */
interface ResultTransformation : Transformation {
    override fun mergeWithPrevious(previousTransformation: SequenceTransformation): ResultTransformation? = null

    val commentSavingRange: PsiChildRange

    val expressionToBeReplacedByResultCallChain: KtExpression

    /**
     * Implementations of this method are obliged to update [commentSavingRangeHolder] when deleting or adding any element into the tree
     * except for the loop itself and the result element returned from this method
     */
    fun convertLoop(resultCallChain: KtExpression, commentSavingRangeHolder: CommentSavingRangeHolder): KtExpression
}

/**
 * Represents a state when matching a part of the loop against known transformations
 */
data class MatchingState(
        val outerLoop: KtForExpression,
        val innerLoop: KtForExpression,
        val statements: List<KtExpression>,
        val inputVariable: KtCallableDeclaration,
        /**
         * Matchers can assume that indexVariable is null if it's not used in the rest of the loop
         */
        val indexVariable: KtCallableDeclaration?,
        val initializationStatementsToDelete: Collection<KtExpression> = emptyList(),
        val previousTransformations: List<SequenceTransformation>
)

interface TransformationMatcher {
    fun match(state: MatchingState): TransformationMatch?

    val indexVariableAllowed: Boolean

    /**
     * Implementors should return true if they match some constructs with expression-embedded break or continue.
     * In this case they are obliged to deal with them and filter out invalid cases.
     */
    val embeddedBreakOrContinuePossible: Boolean
        get() = false
}

sealed class TransformationMatch(val sequenceTransformations: List<SequenceTransformation>) {
    abstract val allTransformations: List<Transformation>

    /**
     * A partial match, includes [newState] for further matching
     */
    class Sequence(transformations: List<SequenceTransformation>, val newState: MatchingState) : TransformationMatch(transformations) {
        constructor(transformation: SequenceTransformation, newState: MatchingState) : this(listOf(transformation), newState)

        override val allTransformations: List<Transformation>
            get() = sequenceTransformations
    }

    /**
     * A match of the whole rest part of the loop
     */
    class Result(val resultTransformation: ResultTransformation, sequenceTransformations: List<SequenceTransformation>) : TransformationMatch(sequenceTransformations) {
        constructor(resultTransformation: ResultTransformation, vararg sequenceTransformations: SequenceTransformation)
        : this(resultTransformation, sequenceTransformations.asList())

        override val allTransformations = sequenceTransformations + resultTransformation
    }
}

/**
 * Helper class for holding and updating PsiChildRange to be used for [CommentSaver.restore] call
 */
class CommentSavingRangeHolder(range: PsiChildRange) {
    var range = range
        private set

    /**
     * Call this method when a new element to be included into the range is added into the tree
     */
    fun add(element: PsiElement) {
        if (range.isEmpty) {
            range = PsiChildRange.singleElement(element)
            return
        }

        val rangeParent = range.first!!.parent
        val elementToAdd = element.parentsWithSelf.takeWhile { it != rangeParent }.last()
        when (elementToAdd) {
            in range -> return

            in range.first!!.siblingsBefore() -> range = PsiChildRange(elementToAdd, range.last)

            else -> range = PsiChildRange(range.first, elementToAdd)
        }
    }

    /**
     * Call this method before deletion of any element which can belong to the range
     */
    fun remove(element: PsiElement) {
        when {
            range.isEmpty -> return

            element == range.first -> {
                val newFirst = element
                        .siblings(forward = true, withItself = false)
                        .takeWhile { it != range.last!!.nextSibling }
                        .firstOrNull { it !is PsiWhiteSpace }
                if (newFirst != null) {
                    range = PsiChildRange(newFirst, range.last)
                }
                else {
                    range = PsiChildRange.EMPTY
                }
            }

            element == range.last -> {
                val newLast = element
                        .siblings(forward = false, withItself = false)
                        .takeWhile { it != range.first!!.prevSibling }
                        .firstOrNull { it !is PsiWhiteSpace }
                if (newLast != null) {
                    range = PsiChildRange(range.first, newLast)
                }
                else {
                    range = PsiChildRange.EMPTY
                }
            }
        }
    }

    private fun PsiElement.siblingsBefore() = if (prevSibling != null) PsiChildRange(parent.firstChild, prevSibling) else PsiChildRange.EMPTY
}