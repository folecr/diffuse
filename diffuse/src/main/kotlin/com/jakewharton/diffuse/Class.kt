package com.jakewharton.diffuse

import com.jakewharton.diffuse.io.Input
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.Handle
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

internal class Class private constructor(
  val declaredMembers: List<Member>,
  val referencedMembers: List<Member>
) {
  companion object {
    fun Input.toClass(): Class {
      val referencedVisitor = ReferencedMembersVisitor()
      val declaredVisitor = DeclaredMembersVisitor(referencedVisitor)
      ClassReader(toByteArray()).accept(declaredVisitor, 0)

      return Class(declaredVisitor.members.sorted(), referencedVisitor.members.sorted())
    }
  }
}

private class DeclaredMembersVisitor(
  val methodVisitor: MethodVisitor
) : ClassVisitor(Opcodes.ASM7) {
  val members = mutableListOf<Member>()

  // TODO lateinit https://youtrack.jetbrains.com/issue/KT-23814
  private var type: TypeDescriptor? = null

  override fun visit(
    version: Int,
    access: Int,
    name: String,
    signature: String?,
    superName: String?,
    interfaces: Array<out String>?
  ) {
    type = TypeDescriptor("L$name;")
  }

  override fun visitMethod(
    access: Int,
    name: String,
    descriptor: String,
    signature: String?,
    exceptions: Array<out String>?
  ): MethodVisitor? {
    members += parseMethod(type!!, name, descriptor)
    return methodVisitor
  }

  override fun visitField(
    access: Int,
    name: String,
    descriptor: String,
    signature: String?,
    value: Any?
  ): FieldVisitor? {
    members += Field(type!!, name, TypeDescriptor(descriptor))
    return null
  }
}

private class ReferencedMembersVisitor : MethodVisitor(Opcodes.ASM7) {
  val members = mutableSetOf<Member>()

  override fun visitMethodInsn(
    opcode: Int,
    owner: String,
    name: String,
    descriptor: String,
    isInterface: Boolean
  ) {
    val ownerType = parseOwner(owner)
    val referencedMethod = parseMethod(ownerType, name, descriptor)
    members += referencedMethod
  }

  override fun visitInvokeDynamicInsn(
    name: String?,
    descriptor: String?,
    bootstrapMethodHandle: Handle,
    vararg bootstrapMethodArguments: Any?
  ) {
    members += parseHandle(bootstrapMethodHandle)

    if (bootstrapMethodHandle == lambdaMetaFactory) {
      // LambdaMetaFactory.metafactory accepts 6 arguments. The first 3 are
      // provided automatically and the latter 3 are supplied as the arguments to
      // this method. The second of those is a MethodHandle to the lambda
      // implementation which needs to be counted as a method reference.
      val implementationHandle = bootstrapMethodArguments[1] as Handle
      members += parseHandle(implementationHandle)
    }
  }

  private fun parseHandle(handle: Handle): Member {
    val handlerOwner = parseOwner(handle.owner)
    val handlerName = handle.name
    val handlerDescriptor = handle.desc
    return if (handlerDescriptor.startsWith('(')) {
      parseMethod(handlerOwner, handlerName, handlerDescriptor)
    } else {
      Field(handlerOwner, handlerName, TypeDescriptor(handlerDescriptor))
    }
  }

  override fun visitFieldInsn(
    opcode: Int,
    owner: String,
    name: String,
    descriptor: String
  ) {
    val ownerType = parseOwner(owner)
    val referencedField = Field(ownerType, name, TypeDescriptor(descriptor))
    members += referencedField
  }

  private fun parseOwner(owner: String): TypeDescriptor {
    val ownerDescriptor = if (owner.startsWith('[')) {
      owner
    } else {
      "L$owner;"
    }
    return TypeDescriptor(ownerDescriptor)
  }
}

private fun parseMethod(
  owner: TypeDescriptor,
  name: String,
  descriptor: String
): Method {
  val parameterTypes = mutableListOf<TypeDescriptor>()
  var i = 1
  while (true) {
    if (descriptor[i] == ')') {
      break
    }
    var typeIndex = i
    while (descriptor[typeIndex] == '[') {
      typeIndex++
    }
    val end = if (descriptor[typeIndex] == 'L') {
      descriptor.indexOf(';', startIndex = typeIndex)
    } else {
      typeIndex
    }
    val parameterDescriptor = descriptor.substring(i, end + 1)
    parameterTypes += TypeDescriptor(parameterDescriptor)
    i += parameterDescriptor.length
  }
  val returnType = TypeDescriptor(descriptor.substring(i + 1))
  return Method(owner, name, parameterTypes, returnType)
}

private val lambdaMetaFactory = Handle(
    Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory",
    "metafactory",
    "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
    false
)