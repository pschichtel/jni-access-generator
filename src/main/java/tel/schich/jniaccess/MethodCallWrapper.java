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

import javax.lang.model.type.TypeKind;
import javax.lang.model.util.Types;

import static tel.schich.jniaccess.GeneratorHelper.*;

public class MethodCallWrapper extends MethodBackedWrapper {
    private final AccessedClass clazz;
    private final AccessedMethod method;

    public MethodCallWrapper(Types types, boolean performanceCritical, AccessedClass clazz, AccessedMethod method) {
        super(types, performanceCritical, method);
        this.clazz = clazz;
        this.method = method;
    }

    @Override
    protected String generateFunctionName() {
        return GeneratorHelper.functionName("call", clazz, method.getName());
    }

    @Override
    protected void generateImpl(StringBuilder out) {
        generateSig(out, false);
        out.append(" {\n");
        generateClassLookup(out, "class", clazz, "    ");
        out.append('\n');
        out.append("    jmethodID method = (*env)->Get");
        if (method.isStatic()) {
            out.append("Static");
        }
        out.append("MethodID(env, class, \"");
        out.append(method.getName()).append("\", \"");
        generateJniMethodSignature(out, getTypes(), method);
        out.append("\");\n");
        out.append("    ");
        if (method.getElement().getReturnType().getKind() != TypeKind.VOID) {
            out.append("return ");
        }
        out.append("(*env)->Call");
        if (method.isStatic()) {
            out.append("Static");
        }
        out.append(TypeHelper.getJNIHelperType(method.getElement().getReturnType()));
        out.append("Method(env, ");
        out.append(method.isStatic() ? "class" : "instance");
        out.append(", method");
        for (MethodParam param : method.getParams()) {
            out.append(", ").append(param.getName());
        }
        out.append(");\n");
        out.append("}\n");
    }
}
