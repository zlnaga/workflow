package com.axacat.workflow.core.usecase

import com.google.gson.reflect.TypeToken
import java.lang.IllegalArgumentException
import java.lang.reflect.ParameterizedType

sealed class UseCase<IN, OUT>(val key: String) {

    open fun reset() {}

    fun canHandle(input: Any?): Boolean {
        if (input == null) {
            return false
        }
        val acceptType = (this::class.java.genericSuperclass as ParameterizedType).actualTypeArguments[0]
        val inputType = TypeToken.get(input::class.java)
        return if (input::class.java.typeParameters.isNotEmpty()) {
            try {
                @Suppress("UNCHECKED_CAST")
                val wrapped = input as? IN
                wrapped != null
            } catch (error: Throwable) {
                false
            }
        } else {
            val type = inputType.type
            if (type is Class<*> && acceptType is Class<*>) {
                acceptType.isAssignableFrom(type)
            } else {
                type == acceptType
            }
        }
    }
}

abstract class AsyncUseCase<T, R>(key: String): UseCase<T, R>(key) {
    suspend fun process(input: T): R {
        if (!canHandle(input)) {
            throw IllegalArgumentException("Input is invalid @ ${this::class.java.name} >> $input")
        } else {
            return onProcess(input)
        }
    }

    abstract suspend fun onProcess(input: T): R
}

abstract class ConditionUseCase<T>(key: String): UseCase<T, Boolean>(key) {
    fun detect(input: T): Boolean {
        if (!canHandle(input)) {
            throw IllegalArgumentException("Input is invalid @ ${this::class.java.name} >> $input")
        }
        return onDetect(input)
    }

    abstract fun onDetect(input: T): Boolean
}