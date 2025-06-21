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
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModuleLifecycle {

    private static void lifecycleFunctionSignature(StringBuilder out, String moduleNamespace, String name) {
        out.append("void ").append(moduleNamespace).append(name).append("(JNIEnv* env)");
    }

    static void generateModuleLifecycleHeaders(StringBuilder out, String moduleNamespace) {
        lifecycleFunctionSignature(out, moduleNamespace, "OnLoad");
        out.append(";\n");
        lifecycleFunctionSignature(out, moduleNamespace, "OnUnload");
        out.append(";\n");
        out.append("\n");
    }

    static void generateModuleLifecycleFunctions(StringBuilder out, String moduleNamespace, List<WrappedElement> wrappedElements) {
        List<WrappedElement> eagerPersistentElements = new ArrayList<>();
        for (WrappedElement wrappedElement : wrappedElements) {
            CacheMode cacheMode = wrappedElement.getCacheMode();
            if (cacheMode == CacheMode.EAGER_PERSISTENT) {
                eagerPersistentElements.add(wrappedElement);
            }
        }

        Map<String, AccessedClass> classes = new HashMap<>();
        for (WrappedElement element : eagerPersistentElements) {
            final AccessedClass clazz = element.getHostClass();
            classes.put(clazz.getTypeName(), clazz);
        }

        Map<String, List<WrappedElement>> elementsByClass = new HashMap<>();
        if (!classes.isEmpty()) {
            for (Map.Entry<String, AccessedClass> e : classes.entrySet()) {
                GeneratorHelper.generateDeclaration(out, "jclass", generateClassCacheSymbol(moduleNamespace, e.getValue()), "");
                out.append("\n");
            }
            out.append("\n");

            for (WrappedElement wrappedElement : wrappedElements) {
                if (wrappedElement.getCacheMode() == CacheMode.NONE) {
                    continue;
                }
                if (wrappedElement instanceof MethodBackedWrapper) {
                    MethodBackedWrapper methodBackedWrapper = (MethodBackedWrapper) wrappedElement;
                    GeneratorHelper.generateDeclaration(out, "jmethodID", generateMethodCacheSymbol(moduleNamespace, methodBackedWrapper.getMethod()), "");
                } else if (wrappedElement instanceof FieldWrapper) {
                    FieldWrapper fieldWrapper = (FieldWrapper) wrappedElement;
                    GeneratorHelper.generateDeclaration(out, "jfieldID", generateFieldCacheSymbol(moduleNamespace, fieldWrapper.getField()), "");
                } else {
                    throw new RuntimeException("Unsupported wrappedElement: " + wrappedElement);
                }
                elementsByClass
                        .computeIfAbsent(wrappedElement.getHostClass().getTypeName(), k -> new ArrayList<>())
                        .add(wrappedElement);
                out.append("\n");
            }

            out.append("\n\n");
        }

        lifecycleFunctionSignature(out, moduleNamespace, "OnLoad");
        out.append(" {\n");

        List<String> globalRefs = new ArrayList<>();
        for (Map.Entry<String, AccessedClass> e : classes.entrySet()) {
            String classSymbol = generateClassCacheSymbol(moduleNamespace, e.getValue());
            GeneratorHelper.generateClassLookup(out, classSymbol, false, e.getValue(), "    ");
            out.append("\n");
            GeneratorHelper.generateNewGlobalRef(out, classSymbol, classSymbol, "jclass", "    ");
            globalRefs.add(classSymbol);
            out.append("\n");
            final List<WrappedElement> relatedElements = elementsByClass.get(e.getKey());
            if (relatedElements != null) {
                for (WrappedElement element : relatedElements) {
                    final String symbol;
                    if (element instanceof MethodBackedWrapper) {
                        AccessedMethod method = ((MethodBackedWrapper) element).getMethod();
                        symbol = generateMethodCacheSymbol(moduleNamespace, method);
                        GeneratorHelper.generateMethodLookup(element.getTypes(), out, symbol, false, classSymbol, method, "    ");
                    } else if (element instanceof FieldWrapper) {
                        AccessedField field = ((FieldWrapper) element).getField();
                        symbol = generateFieldCacheSymbol(moduleNamespace, field);
                        GeneratorHelper.generateFieldLookup(element.getTypes(), out, symbol, false, classSymbol, field, "    ");
                    } else {
                        throw new RuntimeException("Unsupported wrappedElement: " + element);
                    }
                    out.append("\n");
                }
            }
        }
        out.append("}\n\n");
        lifecycleFunctionSignature(out, moduleNamespace, "OnUnload");
        out.append(" {\n");

        // delete the refs in reverse order
        Collections.reverse(globalRefs);
        for (String ref : globalRefs) {
            GeneratorHelper.generateDeleteGlobalRef(out, ref, "    ");
            out.append("\n");
            out.append("    ").append(ref).append(" = NULL;\n");
        }
        out.append("}\n\n");
    }

    static String generateClassCacheSymbol(String moduleNamespace, AccessedClass clazz) {
        return generateCacheSymbol(moduleNamespace, "cached_class_", clazz.getElement());
    }

    static String generateMethodCacheSymbol(String moduleNamespace, AccessedMethod method) {
        String name;
        if (method.isConstructor()) {
            name = "ctor";
        } else {
            name = method.getName();
        }

        final ExecutableElement methodElement = method.getElement();
        final Element clazz = methodElement.getEnclosingElement();
        final int i = GeneratorHelper.findIndexInParent(methodElement);

        return generateCacheSymbol(moduleNamespace, "cached_method_", clazz) + "__" + name + i;
    }

    static String generateFieldCacheSymbol(String moduleNamespace, AccessedField field) {
        String name = field.getName();
        final VariableElement varElement = field.getElement();
        final Element clazz = varElement.getEnclosingElement();
        final int i = GeneratorHelper.findIndexInParent(varElement);

        return generateCacheSymbol(moduleNamespace, "cached_field_", clazz) + "__" + name + i;
    }

    static String generateCacheSymbol(String moduleNamespace, String prefix, Element element) {
        return moduleNamespace + prefix + NativeInterfaceGenerator.buildFullyQualifiedElementName(element).replace('.', '_');
    }
}
