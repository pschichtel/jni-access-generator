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

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

public abstract class TypeHelper {
    private TypeHelper() {
    }

    static boolean isInstanceOf(Types typeUtils, TypeMirror haystack, Class<?> needle) {
        Element element = typeUtils.asElement(haystack);
        if (!(element instanceof TypeElement)) {
            return false;
        }
        if (((TypeElement) element).getQualifiedName().toString().equals(needle.getName())) {
            return true;
        }

        for (TypeMirror superType : typeUtils.directSupertypes(haystack)) {
            if (superType instanceof DeclaredType) {
                return isInstanceOf(typeUtils, superType, needle);
            }
        }

        return false;
    }

    static boolean isString(Types typeUtils, TypeMirror type) {
        return isInstanceOf(typeUtils, type, String.class);
    }

    static String getJNIType(Types typeUtils, TypeMirror type) {
        switch (type.getKind()) {
            case BOOLEAN:
                return "Z";
            case CHAR:
                return "C";
            case BYTE:
                return "B";
            case SHORT:
                return "S";
            case INT:
                return "I";
            case LONG:
                return "J";
            case FLOAT:
                return "F";
            case DOUBLE:
                return "D";
            case VOID:
                return "V";
            case DECLARED:
                TypeElement elem = (TypeElement) typeUtils.asElement(type);
                return "L" + elem.getQualifiedName().toString().replace('.', '/') + ";";
            case ARRAY:
                return "[" + getJNIType(typeUtils, ((ArrayType) type).getComponentType());
            default:
                throw new IllegalArgumentException("Unsupported type: " + type.getKind());
        }
    }

    static String getJNIHelperType(TypeMirror type) {
        switch (type.getKind()) {
            case BOOLEAN:
                return "Boolean";
            case CHAR:
                return "Char";
            case BYTE:
                return "Byte";
            case SHORT:
                return "Short";
            case INT:
                return "Int";
            case LONG:
                return "Long";
            case FLOAT:
                return "Float";
            case DOUBLE:
                return "Double";
            case VOID:
                return "Void";
            default:
                return "Object";
        }
    }

    static String getCType(Types typeUtils, TypeMirror type) {
        switch (type.getKind()) {
            case BOOLEAN:
                return "jboolean";
            case CHAR:
                return "jchar";
            case BYTE:
                return "jbyte";
            case SHORT:
                return "jshort";
            case INT:
                return "jint";
            case LONG:
                return "jlong";
            case FLOAT:
                return "jfloat";
            case DOUBLE:
                return "jdouble";
            case VOID:
                return "void";
            case ARRAY:
                switch (((ArrayType) type).getComponentType().getKind()) {
                    case BOOLEAN:
                        return "jbooleanArray";
                    case CHAR:
                        return "jcharArray";
                    case BYTE:
                        return "jbyteArray";
                    case SHORT:
                        return "jshortArray";
                    case INT:
                        return "jintArray";
                    case LONG:
                        return "jlongArray";
                    case FLOAT:
                        return "jfloatArray";
                    case DOUBLE:
                        return "jdoubleArray";
                    default:
                        return "jobjectArray";
                }
            default:
                if (isInstanceOf(typeUtils, type, String.class)) {
                    return "jstring";
                } else if (isInstanceOf(typeUtils, type, Throwable.class)) {
                    return "jthrowable";
                } else {
                    return "jobject";
                }
        }
    }

    public static TypeMirror getVoid(Types types) {
        return types.getNoType(TypeKind.VOID);
    }
}
