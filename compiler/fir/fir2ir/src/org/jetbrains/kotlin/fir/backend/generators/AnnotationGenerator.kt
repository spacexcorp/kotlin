/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.backend.generators

import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.fir.FirAnnotationContainer
import org.jetbrains.kotlin.fir.backend.Fir2IrVisitor
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.util.isPropertyField
import org.jetbrains.kotlin.ir.util.isSetter

internal class AnnotationGenerator(private val visitor: Fir2IrVisitor) {

    fun generate(irContainer: IrMutableAnnotationContainer, firContainer: FirAnnotationContainer) {
        irContainer.annotations = firContainer.annotations.mapNotNull {
            it.accept(visitor, null) as? IrConstructorCall
        }
    }

    fun generate(irProperty: IrProperty, property: FirProperty) {
        irProperty.annotations +=
            property.annotations
                .filter {
                    it.useSiteTarget == null || it.useSiteTarget == AnnotationUseSiteTarget.PROPERTY
                }
                .mapNotNull {
                    it.accept(visitor, null) as? IrConstructorCall
                }
    }

    fun generate(irField: IrField, property: FirProperty) {
        assert(irField.isPropertyField) {
            "$irField is not a property field."
        }
        irField.annotations +=
            property.annotations
                .filter {
                    it.useSiteTarget == AnnotationUseSiteTarget.FIELD ||
                            it.useSiteTarget == AnnotationUseSiteTarget.PROPERTY_DELEGATE_FIELD
                }
                .mapNotNull { it.accept(visitor, null) as? IrConstructorCall }
    }

    fun generate(propertyAccessor: IrFunction, property: FirProperty) {
        assert(propertyAccessor.isPropertyAccessor) {
            "$propertyAccessor is not a property accessor."
        }
        if (propertyAccessor.isSetter) {
            propertyAccessor.annotations +=
                property.annotations
                    .filter { it.useSiteTarget == AnnotationUseSiteTarget.PROPERTY_SETTER }
                    .mapNotNull { it.accept(visitor, null) as? IrConstructorCall }
            propertyAccessor.valueParameters.singleOrNull()?.annotations =
                propertyAccessor.valueParameters.singleOrNull()?.annotations?.plus(
                    property.annotations
                        .filter { it.useSiteTarget == AnnotationUseSiteTarget.SETTER_PARAMETER }
                        .mapNotNull { it.accept(visitor, null) as? IrConstructorCall }
                )!!
        } else {
            propertyAccessor.annotations +=
                property.annotations
                    .filter { it.useSiteTarget == AnnotationUseSiteTarget.PROPERTY_GETTER }
                    .mapNotNull { it.accept(visitor, null) as? IrConstructorCall }
        }
        propertyAccessor.extensionReceiverParameter?.annotations =
            propertyAccessor.extensionReceiverParameter?.annotations?.plus(
                property.annotations
                    .filter { it.useSiteTarget == AnnotationUseSiteTarget.RECEIVER }
                    .mapNotNull { it.accept(visitor, null) as? IrConstructorCall }
            )!!
    }
}