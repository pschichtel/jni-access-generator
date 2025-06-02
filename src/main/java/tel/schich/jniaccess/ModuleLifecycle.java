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

import java.util.ArrayList;
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
            classes.put(clazz.getElement().getQualifiedName().toString(), clazz);
        }

        for (Map.Entry<String, AccessedClass> e : classes.entrySet()) {
            out.append("jclass cached_class_").append(e.getKey().replace('.', '_')).append(";\n");
        }

        for (WrappedElement wrappedElement : eagerPersistentElements) {
            out.append("// ").append(wrappedElement.getClass().getName()).append("\n");
        }

        lifecycleFunctionSignature(out, moduleNamespace, "OnLoad");
        out.append(" {\n");

        for (Map.Entry<String, AccessedClass> e : classes.entrySet()) {
            String sym = "cached_class_" + e.getKey().replace('.', '_');
            out.append("    ").append(sym).append(" = (*env)->FindClass(env, \"").append(e.getKey().replace('.', '/')).append("\");\n");
            out.append("    ").append(sym).append(" = (jclass) (*env)->NewGlobalRef(env, ").append(sym).append(");\n");
        }
        for (WrappedElement wrappedElement : eagerPersistentElements) {
            out.append("    // ").append(wrappedElement.getClass().getName()).append("\n");
        }
        out.append("}\n\n");
        lifecycleFunctionSignature(out, moduleNamespace, "OnUnload");
        out.append(" {\n");
        for (WrappedElement wrappedElement : eagerPersistentElements) {
            out.append("    // ").append(wrappedElement.getClass().getName()).append("\n");
        }

        for (Map.Entry<String, AccessedClass> e : classes.entrySet()) {
            String sym = "cached_class_" + e.getKey().replace('.', '_');
            out.append("    (*env)->DeleteGlobalRef(env, ").append(sym).append(");\n");
        }
        out.append("}\n\n");
    }
}
