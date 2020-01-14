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
import javax.lang.model.util.Types;

import static tel.schich.jniaccess.GeneratorHelper.generateFunctionSignature;
import static tel.schich.jniaccess.GeneratorHelper.jniClassNameOf;

public class MethodCallWrapper extends WrappedElement {
    private final AccessedClass clazz;
    private final AccessedMethod method;

    public MethodCallWrapper(Types types, boolean performanceCritical, AccessedClass clazz, AccessedMethod method) {
        super(types, performanceCritical);
        this.clazz = clazz;
        this.method = method;
    }

    public AccessedClass getClazz() {
        return clazz;
    }

    public AccessedMethod getMethod() {
        return method;
    }

    private String generateFunctionName() {
        return GeneratorHelper.functionName("call", clazz, method.getElement().getSimpleName());
    }

    private void generateSig(StringBuilder out, boolean cStrings) {
        generateFunctionSignature(getTypes(), out, method, generateFunctionName(), cStrings);
    }

    private void generateImpl(StringBuilder out) {
        String lookup = method.isStatic() ? "GetStaticMethodID" : "GetMethodID";
        Name methodName = method.getElement().getSimpleName();
        String accessorType = TypeHelper.getJNIHelperType(method.getElement().getReturnType());

        generateSig(out, false);
        out.append(" {\n");
        out.append("    jclass class = (*env)->FindClass(env, \"").append(jniClassNameOf(clazz)).append("\");\n");
        out.append("    jmethodID method = (*env)->").append(lookup).append("(env, class, \"").append(methodName).append("\", \"");
        GeneratorHelper.generateJniMethodSignature(out, getTypes(), method);
        out.append("\");\n");
        if (method.isStatic()) {
            out.append("    return (*env)->CallStatic").append(accessorType).append("Method(env, class, method");
        } else {
            out.append("    return (*env)->Call").append(accessorType).append("Method(env, instance, method");
        }
        for (MethodParam param : method.getParams()) {
            out.append(", ").append(param.getName());
        }
        out.append(");\n");
        out.append("}\n");
    }

    @Override
    public void generateDeclarations(StringBuilder out) {
        generateSig(out, false);
        out.append(";\n\n");
    }

    @Override
    public void generateImplementations(StringBuilder out) {
        generateImpl(out);
        out.append("\n\n");
    }
}
