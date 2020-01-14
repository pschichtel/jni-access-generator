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

import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

public abstract class GeneratorHelper {
    public static final String C_STRING_PREFIX = "c_";

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
        out.append(TypeHelper.getCType(types, returnType)).append(" ");
        out.append(functionName).append("(JNIEnv *env");
        if (!method.isStatic()) {
            out.append(", jobject instance");
        }
        for (MethodParam param : method.getParams()) {
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
        out.append(")");
    }

    public static void generateJniMethodSignature(StringBuilder out, Types types, AccessedMethod method) {
        out.append('(');
        for (MethodParam param : method.getParams()) {
            out.append(TypeHelper.getJNIType(types, param.getType()));
        }
        out.append(')');
        out.append(TypeHelper.getJNIType(types, method.getElement().getReturnType()));
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
                .append(C_STRING_PREFIX)
                .append(param.getName())
                .append(");");
    }

    public static void generateJStringConversions(Types types, StringBuilder out, String indention, AccessedMethod method) {
        for (MethodParam param : method.getParams()) {
            if (TypeHelper.isString(types, param.getType())) {
                out.append(indention);
                GeneratorHelper.generateJStringConversion(out, param);
                out.append('\n');
            }
        }
    }

    public static void generateJStringFunctionOverloadCall(Types types, StringBuilder out, String indention, String functionName, AccessedMethod method) {
        generateJStringConversions(types, out, indention, method);
        out.append(indention);
        if (method.getElement().getReturnType().getKind() != TypeKind.VOID) {
            out.append("return ");
        }
        out.append(functionName).append("(env");
        for (MethodParam param : method.getParams()) {
            out.append(", ").append(param.getName());
        }
        out.append(");\n");
    }

    public static void generateJStringFunctionOverloadBody(Types types, StringBuilder out, String functionName, AccessedMethod method) {
        out.append(" {\n");
        generateJStringFunctionOverloadCall(types, out, "    ", functionName, method);
        out.append("}\n");
    }

    public static String functionName(String prefix, AccessedClass clazz) {
        return prefix + "_" + clazz.getElement().getQualifiedName().toString().replace('.', '_');
    }

    public static String functionName(String prefix, AccessedClass clazz, Name name) {
        return functionName(prefix, clazz) + "_" + name;
    }
}
