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
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.*;

public class JNIAccessProcessor extends AbstractProcessor {
    private static final Set<String> SUPPORTED_ANNOTATIONS = Collections.singleton(JNIAccess.class.getCanonicalName());
    private static final String OUTPUT_FILE_NAME = "jni-c-to-java";

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
        List<WrappedElement> wrappedElements = new ArrayList<>();
        for (Element annotatedElement : annotatedElements) {
            boolean performanceCritical = annotatedElement.getAnnotation(JNIAccess.class).performanceCritical();
            switch (annotatedElement.getKind()) {
                case CONSTRUCTOR:
                    wrappedElements.add(processConstructor(annotatedElement, performanceCritical));
                    break;
                case METHOD:
                    wrappedElements.add(processMethod(annotatedElement, performanceCritical));
                    break;
                case FIELD:
                    wrappedElements.add(processField(annotatedElement, performanceCritical));
                    break;
                default:
            }
        }

        if (wrappedElements.isEmpty()) {
            return false;
        }

        StringBuilder headerOutput = new StringBuilder();
        for (WrappedElement e : wrappedElements) {
            e.generateDeclarations(headerOutput);
        }
        writeNativeContent(headerOutput, OUTPUT_FILE_NAME + ".h");

        StringBuilder implementationOutput = new StringBuilder();
        for (WrappedElement e : wrappedElements) {
            e.generateImplementations(implementationOutput);
        }
        writeNativeContent(implementationOutput, OUTPUT_FILE_NAME + ".c");

        return true;
    }

    private void writeNativeContent(StringBuilder out, String file) {
        try {
            FileObject resource = processingEnv.getFiler().createResource(StandardLocation.NATIVE_HEADER_OUTPUT, "", file);
            Writer writer = resource.openWriter();
            try {
                writer.write(out.toString());
            } finally {
                writer.close();
            }
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.getLocalizedMessage());
        }
    }

    private WrappedElement processConstructor(Element element, boolean performanceCritical) {
        TypeElement clazz = (TypeElement) element.getEnclosingElement();
        ExecutableElement ctor = (ExecutableElement) element;
        Types typeUtils = processingEnv.getTypeUtils();
        ConstructorCall call = new ConstructorCall(new AccessedClass(clazz, clazz.asType()), new AccessedMethod(ctor, getParams(ctor)));

        if (TypeHelper.isInstanceOf(typeUtils, clazz, Throwable.class)) {
            return new ThrowWrapper(typeUtils, performanceCritical, call);
        } else {
            return new NewInstanceWrapper(typeUtils, performanceCritical, call);
        }
    }

    private WrappedElement processMethod(Element element, boolean performanceCritical) {
        Types typeUtils = processingEnv.getTypeUtils();
        TypeElement clazz = (TypeElement) element.getEnclosingElement();
        ExecutableElement method = (ExecutableElement) element;
        return new MethodCallWrapper(typeUtils, performanceCritical, new AccessedClass(clazz, clazz.asType()), new AccessedMethod(method, getParams(method)));
    }

    private WrappedElement processField(Element element, boolean performanceCritical) {
        Types typeUtils = processingEnv.getTypeUtils();
        TypeElement clazz = (TypeElement) element.getEnclosingElement();
        VariableElement field = (VariableElement) element;
        return new FieldWrapper(typeUtils, performanceCritical, new AccessedClass(clazz, clazz.asType()), new AccessedField(field, field.asType()));
    }

    private static List<MethodParam> getParams(ExecutableElement element) {
        List<MethodParam> params = new ArrayList<>();

        for (VariableElement parameter : element.getParameters()) {
            params.add(new MethodParam(parameter.getSimpleName().toString(), parameter, parameter.asType()));
        }

        return params;
    }
}
