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
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.function.Consumer;

import static java.lang.Boolean.parseBoolean;
import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableSet;
import static tel.schich.jniaccess.NativeInterfaceGenerator.buildFullyQualifiedElementName;

public class JNIAccessProcessor extends AbstractProcessor {

    private static final String OPTION_GENERATE_JNI_HEADERS = "generate.jni.headers";
    private static final String OPTION_OUTPUT_LOCATION = "output.location";

    private static final Set<String> SUPPORTED_ANNOTATIONS = Collections.singleton(JNIAccess.class.getCanonicalName());
    private static final Set<String> SUPPORTED_OPTIONS = unmodifiableSet(new HashSet<>(asList(
            OPTION_GENERATE_JNI_HEADERS,
            OPTION_OUTPUT_LOCATION
    )));

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return SUPPORTED_ANNOTATIONS;
    }

    @Override
    public Set<String> getSupportedOptions() {
        return SUPPORTED_OPTIONS;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        generateJavaToNativeInterface(roundEnv);

        return generateNativeToJavaInterface(roundEnv);
    }

    private boolean shouldGenerateJniHeaders() {
        return parseBoolean(processingEnv.getOptions().getOrDefault(OPTION_GENERATE_JNI_HEADERS, "false"));
    }

    private File getOutputLocation() {
        String outputLocation = processingEnv.getOptions().get(OPTION_OUTPUT_LOCATION);
        if (outputLocation == null) {
            return null;
        }
        File file = new File(outputLocation);
        if (!file.exists()) {
            if (!file.mkdirs()) {
                return null;
            }
        }
        if (!file.isDirectory()) {
            return null;
        }
        return file;
    }

    private void generateJavaToNativeInterface(RoundEnvironment roundEnv) {
        if (!shouldGenerateJniHeaders()) {
            return;
        }
        List<NativeInterfaceGenerator.ClassWithNatives> nativeMethods = NativeInterfaceGenerator.searchNativeMethods(roundEnv);

        if (nativeMethods.isEmpty()) {
            return;
        }

        final String fileName = "jni-java-to-c";
        final String headerGuard = "_JNI_JAVA_TO_C_INTERFACE";

        final CharSequence headerContent = generateHeader(headerGuard, out -> {
            ifCpp(out, o -> o.append("extern \"C\" {\n"));
            nativeMethods.forEach((clazz) -> {

                out.append("\n/* Begin Class: ").append(buildFullyQualifiedElementName(clazz.getTheClass())).append(" */\n\n");

                for (VariableElement constant : clazz.getConstants()) {
                    generateConstant(out, constant);
                    out.append('\n');
                    out.append('\n');
                }
                out.append('\n');
                for (ExecutableElement method : clazz.getMethods()) {
                    generateExternPrototype(out, method);
                    out.append('\n');
                    out.append('\n');
                }

                out.append("/* End Class: ").append(buildFullyQualifiedElementName(clazz.getTheClass())).append(" */\n\n");
            });
            ifCpp(out, o -> o.append("}\n"));
        });

        writeNativeContent(headerContent, fileName + ".h");
    }

    private void generateConstant(StringBuilder out, VariableElement constant) {
        final String name = buildFullyQualifiedElementName(constant).replace('.', '_');
        final Object value = constant.getConstantValue();
        final String cValue;
        if (value instanceof Long) {
            cValue = value + "L";
        } else if (value instanceof Float) {
            cValue = value + "f";
        } else if (value instanceof Character) {
            final char c = (Character) value;
            cValue = "L'" + escapeChar(c) + "'";
        } else if (value instanceof String) {
            cValue = "L\"" + escapeString((String) value) + "\"";
        } else {
            cValue = String.valueOf(value);
        }
        out.append("#define ").append(name).append(' ').append(cValue);
    }

    private static String escapeString(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); ++i) {
            out.append(escapeChar(s.charAt(i)));
        }
        return out.toString();
    }

    private static String escapeChar(char c) {
        if (c == '\0') {
            return "\\0";
        }
        if (c == '\'') {
            return "\\'";
        }
        if (c == '"') {
            return "\\\"";
        }
        if (Character.isISOControl(c)) {
            return "\\n" + ((int) c);
        } else {
            return String.valueOf(c);
        }
    }

    private void generateExternPrototype(StringBuilder out, ExecutableElement method) {
        final String name = "Java_" + buildFullyQualifiedElementName(method).replace('.', '_');
        final boolean instance = !method.getModifiers().contains(Modifier.STATIC);

        List<MethodParam> params = getParams(method);

        GeneratorHelper.generateExternFunctionSignature(processingEnv.getTypeUtils(), out, name, method.getReturnType(), instance, params);
    }

    private boolean generateNativeToJavaInterface(RoundEnvironment roundEnv) {

        Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(JNIAccess.class);
        List<WrappedElement> wrappedElements = new ArrayList<>();
        for (Element annotatedElement : annotatedElements) {
            boolean performanceCritical = annotatedElement.getAnnotation(PerformanceCritical.class) != null;
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

        final String fileName = "jni-c-to-java";
        final String headerGuard = "_JNI_C_TO_JAVA_INTERFACE";

        final CharSequence headerContent = generateHeader(headerGuard, headerOutput -> {
            for (WrappedElement e : wrappedElements) {
                e.generateDeclarations(headerOutput);
            }
        });
        final String generatedHeaderName = fileName + ".h";
        writeNativeContent(headerContent, generatedHeaderName);

        StringBuilder implementationOutput = new StringBuilder();
        implementationOutput.append("#include \"").append(generatedHeaderName).append("\"\n");
        implementationOutput.append("\n");
        for (WrappedElement e : wrappedElements) {
            e.generateImplementations(implementationOutput);
        }
        writeNativeContent(implementationOutput, fileName + ".c");

        return true;
    }

    private static CharSequence generateHeader(String headerGuard, Consumer<StringBuilder> builder) {
        final StringBuilder out = new StringBuilder();
        out.append("#ifndef ").append(headerGuard).append("\n");
        out.append("#define ").append(headerGuard).append("\n\n");
        out.append("#include <jni.h>\n");
        out.append("\n");
        builder.accept(out);
        out.append("\n");
        out.append("#endif\n");
        return out;
    }

    private static void ifCpp(StringBuilder out, Consumer<StringBuilder> builder) {
        out.append("#ifdef __cplusplus\n");
        builder.accept(out);
        out.append("#endif\n");
    }

    private Writer openFile(String file) throws IOException {
        FileObject resource = null;
        try {
            resource = processingEnv.getFiler().createResource(StandardLocation.NATIVE_HEADER_OUTPUT, "", file);
        } catch (NullPointerException ignored) {
        }

        if (resource != null) {
            return resource.openWriter();
        }

        File outputLocation = getOutputLocation();
        if (outputLocation != null) {
            return new FileWriter(new File(outputLocation, file), false);
        }

        return null;
    }

    private void writeNativeContent(CharSequence out, String file) {
        try (Writer writer = openFile(file)) {
            if (writer == null) {
                logError("No output location available! You can use the 'output.location' argument to set one or use the -h option of javac (1.8+).");
                return;
            }
            writer.write(out.toString());
        } catch (IOException e) {
            logError(e.getLocalizedMessage());
        }
    }

    private WrappedElement processConstructor(Element element, boolean performanceCritical) {
        TypeElement clazz = (TypeElement) element.getEnclosingElement();
        ExecutableElement ctor = (ExecutableElement) element;
        Types typeUtils = processingEnv.getTypeUtils();
        ConstructorCall call = new ConstructorCall(new AccessedClass(clazz, clazz.asType()), new AccessedMethod(ctor, getParams(ctor)));

        if (TypeHelper.isInstanceOf(typeUtils, clazz.asType(), Throwable.class)) {
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

    private void logError(String s) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, s);
    }

    private static List<MethodParam> getParams(ExecutableElement element) {
        List<MethodParam> params = new ArrayList<>();

        for (VariableElement parameter : element.getParameters()) {
            params.add(new MethodParam(parameter.getSimpleName().toString(), parameter, parameter.asType()));
        }

        return params;
    }
}
