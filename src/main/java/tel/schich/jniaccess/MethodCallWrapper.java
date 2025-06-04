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

    public MethodCallWrapper(Types types, CacheMode cacheMode, AccessedClass clazz, AccessedMethod method) {
        super(types, cacheMode, method);
        this.clazz = clazz;
    }

    @Override
    public AccessedClass getHostClass() {
        return clazz;
    }

    @Override
    protected String generateFunctionName() {
        return GeneratorHelper.functionName("call", clazz, getMethod().getName());
    }

    @Override
    protected void generateImpl(StringBuilder out) {
        generateSig(out, false);
        out.append(" {\n");
        final String classSymbol = "class";
        generateClassLookup(out, classSymbol, true, clazz, "    ");
        out.append('\n');
        final AccessedMethod method = getMethod();
        final String methodSymbol = "method";
        GeneratorHelper.generateMethodLookup(getTypes(), out, methodSymbol, true, classSymbol, method, "    ");
        out.append("\n");
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
        out.append(method.isStatic() ? classSymbol : "instance");
        out.append(", ").append(methodSymbol);
        for (MethodParam param : method.getParams()) {
            out.append(", ").append(param.getName());
        }
        out.append(");\n");
        out.append("}\n");
    }
}
