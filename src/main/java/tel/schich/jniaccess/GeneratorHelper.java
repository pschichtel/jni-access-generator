/*
 * The MIT License
 * Copyright © 2020 Phillip Schichtel
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package tel.schich.jniaccess;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import java.util.List;

public abstract class GeneratorHelper {
    public static final String C_STRING_PARAMETER_PREFIX = "c_";
    public static final String C_STRING_FUNCTION_SUFFIX = "_cstr";

    private GeneratorHelper() {

    }

    public static String jniClassNameOf(AccessedClass clazz) {
        return jniClassNameOf(clazz.getElement());
    }
    public static String jniClassNameOf(TypeElement clazz) {
        return clazz.getQualifiedName().toString().replace('.', '/');
    }

    public static void generateFunctionSignature(Types types, StringBuilder out, AccessedMethod method, String functionName, boolean cStrings) {
        generateFunctionSignature(types, out, method, method.getElement().getReturnType(), functionName, cStrings);
    }

    public static void generateFunctionSignature(Types types, StringBuilder out, AccessedMethod method, TypeMirror returnType, String functionName, boolean cStrings) {
        generateFunctionSignature(types, out, functionName, returnType, !method.isStatic() && !method.isConstructor(), method.getParams(), cStrings);
    }

    public static void generateFunctionSignature(Types types, StringBuilder out, String functionName, TypeMirror returnType, boolean instance, List<MethodParam> params, boolean cStrings) {
        out.append(TypeHelper.getCType(types, returnType)).append(" ");
        out.append(functionName);
        if (cStrings) {
            out.append(C_STRING_FUNCTION_SUFFIX);
        }
        out.append("(JNIEnv *env");
        if (instance) {
            out.append(", jobject instance");
        }
        generateFunctionSignatureParameters(types, out, params, cStrings);
        out.append(")");
    }

    public static void generateExternFunctionSignature(Types types, StringBuilder out, String functionName, TypeMirror returnType, boolean instance, List<MethodParam> params) {
        out.append("JNIEXPORT ");
        out.append(TypeHelper.getCType(types, returnType)).append(" ");
        out.append("JNICALL ");
        out.append(functionName);
        out.append("(JNIEnv *env");
        out.append(", ").append(instance ? "jobject instance" : "jclass clazz");
        generateFunctionSignatureParameters(types, out, params, false);
        out.append(");");
    }

    private static void generateFunctionSignatureParameters(Types types, StringBuilder out, List<MethodParam> params, boolean cStrings) {
        for (MethodParam param : params) {
            final TypeMirror type = param.getType();
            final String cType;
            final String name;
            if (cStrings && TypeHelper.isInstanceOf(types, type, String.class)) {
                cType = "char*";
                name = "c_" + param.getName();
            } else {
                cType = TypeHelper.getCType(types, type);
                name = param.getName();
            }
            out.append(", ").append(cType).append(' ').append(name);
        }
    }

    public static void generateJniMethodSignature(StringBuilder out, Types types, AccessedMethod method) {
        out.append('(');
        generateJniMethodParametersSignature(out, types, method.getParams());
        out.append(')');
        out.append(TypeHelper.getJNIType(types, method.getElement().getReturnType()));
    }

    public static void generateJniMethodParametersSignature(StringBuilder out, Types types, List<MethodParam> params) {
        for (MethodParam param : params) {
            out.append(TypeHelper.getJNIType(types, param.getType()));
        }
    }

    public static boolean hasStringParameter(Types types, AccessedMethod method) {
        for (MethodParam param : method.getParams()) {
            if (TypeHelper.isInstanceOf(types, param.getType(), String.class)) {
                return true;
            }
        }
        return false;
    }

    public static void generateJStringConversion(StringBuilder out, MethodParam param) {
        out.append("jstring ")
                .append(param.getName())
                .append(" = (*env)->NewStringUTF(env, ")
                .append(C_STRING_PARAMETER_PREFIX)
                .append(param.getName())
                .append(");");
    }

    public static void generateJStringConversions(Types types, StringBuilder out, String indention, List<MethodParam> params) {
        for (MethodParam param : params) {
            if (TypeHelper.isString(types, param.getType())) {
                out.append(indention);
                GeneratorHelper.generateJStringConversion(out, param);
                out.append('\n');
            }
        }
    }

    public static void generateJStringFree(StringBuilder out, MethodParam param) {
        out.append("(*env)->DeleteLocalRef(env, ").append(param.getName()).append(");");
    }

    public static void generateJStringFrees(Types types, StringBuilder out, String indention, List<MethodParam> params) {
        for (MethodParam param : params) {
            if (TypeHelper.isString(types, param.getType())) {
                out.append(indention);
                GeneratorHelper.generateJStringFree(out, param);
                out.append('\n');
            }
        }
    }

    public static void generateJStringFunctionOverloadCall(Types types, StringBuilder out, String indention, String functionName, TypeMirror returnType, boolean instance, List<MethodParam> params) {
        generateJStringConversions(types, out, indention, params);
        out.append(indention);
        if (returnType.getKind() != TypeKind.VOID) {
            out.append("return ");
        }
        out.append(functionName).append("(env");
        if (instance) {
            out.append(", instance");
        }
        for (MethodParam param : params) {
            out.append(", ").append(param.getName());
        }
        out.append(");\n");
        generateJStringFrees(types, out, indention, params);
    }

    public static void generateJStringFunctionOverload(Types types, StringBuilder out, String functionName, AccessedMethod method) {
        generateJStringFunctionOverload(types, out, functionName, !method.isStatic() && !method.isConstructor(), method.getElement().getReturnType(), method.getParams());
    }

    public static void generateJStringFunctionOverload(Types types, StringBuilder out, String functionName, boolean instance, TypeMirror returnType, List<MethodParam> params) {
        generateFunctionSignature(types, out, functionName, returnType, instance, params, true);
        out.append(" {\n");
        generateJStringFunctionOverloadCall(types, out, "    ", functionName, returnType, instance, params);
        out.append("}\n");
    }

    public static String functionName(String prefix, AccessedClass clazz) {
        return prefix + "_" + clazz.getElement().getQualifiedName().toString().replace('.', '_');
    }

    public static String functionName(String prefix, AccessedClass clazz, String name) {
        return functionName(prefix, clazz) + "_" + name;
    }

    public static void generateClassLookup(StringBuilder out, String var, AccessedClass clazz, String indention) {
        out.append(indention).append("jclass ").append(var).append(" = (*env)->FindClass(env, \"").append(jniClassNameOf(clazz)).append("\");");
    }

    public static void generateMethodLookup(Types types, StringBuilder out, String var, String classVar, AccessedMethod method, String indention) {
        out.append(indention)
                .append("jmethodID ")
                .append(var).append(" = (*env)->Get");
        if (method.isStatic()) {
            out.append("Static");
        }
        out.append("MethodID(env, ")
                .append(classVar).append(", \"")
                .append(method.getName())
                .append("\", \"");
        GeneratorHelper.generateJniMethodSignature(out, types, method);
        out.append("\");");
    }

    public static void generateFieldLookup(Types types, StringBuilder out, String var, String classVar, AccessedField field, String indention) {
        out.append(indention)
                .append("jfieldID ")
                .append(var).append(" = (*env)->Get");
        if (field.isStatic()) {
            out.append("Static");
        }
        out.append("FieldID(env, ")
                .append(classVar).append(", \"")
                .append(field.getName())
                .append("\", \"")
                .append(TypeHelper.getJNIType(types, field.getType()))
                .append("\");");
    }

    public static void generateNewObjectCreation(StringBuilder out, String classVar, String ctorVar, AccessedMethod method) {
        out.append("(*env)->NewObject(env, ").append(classVar).append(", ").append(ctorVar);
        for (MethodParam param : method.getParams()) {
            out.append(", ").append(param.getName());
        }
        out.append(");");
    }
}
