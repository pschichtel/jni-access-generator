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

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

public class JNIAccessProcessor extends AbstractProcessor {
    private static final Set<String> SUPPORTED_ANNOTATIONS = Collections.singleton(JNIAccess.class.getCanonicalName());

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return SUPPORTED_ANNOTATIONS;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(JNIAccess.class);
        StringBuilder out = new StringBuilder();
        for (Element annotatedElement : annotatedElements) {
            boolean performanceCritical = annotatedElement.getAnnotation(JNIAccess.class).performanceCritical();
            switch (annotatedElement.getKind()) {
                case CONSTRUCTOR:
                    processConstructor(roundEnv, annotatedElement, performanceCritical, out);
                    break;
                case METHOD:
                    processMethod(roundEnv, annotatedElement, performanceCritical, out);
                    break;
                case FIELD:
                    processField(roundEnv, annotatedElement, performanceCritical, out);
                    break;
                default:
            }
        }

        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, out);

        return false;
    }

    private static boolean isInstanceOf(Types typeUtils, TypeElement element, Class<?> type) {
        if (element.getQualifiedName().toString().equals(type.getName())) {
            return true;
        }

        for (TypeMirror superType : typeUtils.directSupertypes(element.asType())) {
            Element superElement = typeUtils.asElement(superType);
            if (superElement instanceof TypeElement) {
                return isInstanceOf(typeUtils, (TypeElement) superElement, type);
            }
        }

        return false;
    }

    private static String getJNIType(Types typeUtils, TypeMirror type) {
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
                throw new IllegalArgumentException("Unsupported type!");
        }
    }

    private static String getCType(Types typeUtils, TypeMirror type) {
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
            case DECLARED:
                TypeElement elem = (TypeElement) typeUtils.asElement(type);
                if (isInstanceOf(typeUtils, elem, String.class)) {
                    return "jstring";
                } else if (isInstanceOf(typeUtils, elem, Throwable.class)) {
                    return "jthrowable";
                } else {
                    return "jobject";
                }
            case ARRAY:
                return getCType(typeUtils, type) + "*";
            default:
                throw new IllegalArgumentException("Unsupported type!");
        }
    }

    private static String generateJNIMethodSignature(Types typeUtils, ExecutableElement executable) {
        StringBuilder out = new StringBuilder("(");
        for (VariableElement parameter : executable.getParameters()) {
            out.append(getJNIType(typeUtils, parameter.asType()));
        }
        out.append(")");
        out.append(getJNIType(typeUtils, executable.getReturnType()));

        return out.toString();
    }

    private static String generateCFunctionSignature(Types typeUtils, TypeElement type, ExecutableElement executable) {
        String name = executable.getSimpleName().toString();
        boolean isConstructor = name.equals("<init>");
        boolean isException = isConstructor && isInstanceOf(typeUtils, type, Throwable.class);

        final String returnType;
        final String prefix;
        if (isConstructor) {
            if (isException) {
                prefix = "throw_";
                returnType = "void";
            } else {
                prefix = "create_";
                returnType = "jobject";
            }
        } else {
            prefix = "call_";
            returnType = getCType(typeUtils, executable.getReturnType());
        }
        final String classComponent = type.getQualifiedName().toString().replace('.', '_');
        final String methodComponent;
        if (isConstructor) {
            methodComponent = "";
        } else {
            methodComponent = "_" + executable.getSimpleName();
        }

        StringBuilder out = new StringBuilder();
        out.append(returnType).append(' ').append(prefix).append(classComponent).append(methodComponent).append(' ');
        out.append("(JNIEnv *env");
        TypeMirror receiverType = executable.getReceiverType();
        if (!isConstructor && receiverType != null) {
            out.append(", ").append(getCType(typeUtils, receiverType));
        }
        for (VariableElement parameter : executable.getParameters()) {
            out.append(", ").append(getCType(typeUtils, parameter.asType())).append(' ').append(parameter.getSimpleName());
        }
        out.append(')');


        return out.toString();
    }

    private String generateParameterList(ExecutableElement executable) {
        StringBuilder out = new StringBuilder();
        Iterator<? extends VariableElement> it = executable.getParameters().iterator();
        if (it.hasNext()) {
            out.append(it.next().getSimpleName());
            while (it.hasNext()) {
                out.append(", ").append(it.next().getSimpleName());
            }
        }

        return out.toString();
    }

    private void processConstructor(RoundEnvironment env, Element element, boolean performanceCritical, StringBuilder out) {
        ExecutableElement ctor = (ExecutableElement) element;
        TypeElement clazz = (TypeElement) element.getEnclosingElement();
        Types typeUtils = processingEnv.getTypeUtils();
        boolean isException = isInstanceOf(typeUtils, clazz, Throwable.class);
        String paramList = generateParameterList(ctor);

        String className = clazz.getQualifiedName().toString().replace('.', '/');
        out.append(generateCFunctionSignature(typeUtils, clazz, ctor)).append(" {\n");
        out.append("    jclass class = (*env)->FindClass(env, \"").append(className).append("\");\n");
        out.append("    if (class == NULL) {\n");
        out.append("        return").append(isException ? "" : " NULL").append(";\n");
        out.append("    }\n");
        if (isException) {
            out.append("    (*env)->ThrowNew(env, ctor, ").append(paramList).append(");\n");
        } else {
            out.append("    jmethod ctor = (*env)->GetMethodID(env, class, \"<init>\", \"").append(generateJNIMethodSignature(typeUtils, ctor)).append("\");\n");
            out.append("    if (ctor == NULL) {\n");
            out.append("        return NULL;\n");
            out.append("    }\n");
            out.append("    return (*env)->NewObject(env, class, ctor, ").append(paramList).append(");\n");
        }
        out.append("}\n");
    }

    private void processMethod(RoundEnvironment env, Element element, boolean performanceCritical, StringBuilder out) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "method " + element.getSimpleName());
    }

    private void processField(RoundEnvironment env, Element element, boolean performanceCritical, StringBuilder out) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "field " + element.getSimpleName());
    }
}
