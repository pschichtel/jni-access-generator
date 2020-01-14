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

import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

import static tel.schich.jniaccess.GeneratorHelper.*;

public class ThrowWrapper extends WrappedElement {
    private final ConstructorCall constructor;

    public ThrowWrapper(Types types, boolean performanceCritical, ConstructorCall constructor) {
        super(types, performanceCritical);
        this.constructor = constructor;
    }

    public ConstructorCall getConstructor() {
        return constructor;
    }

    private String generateFunctionName() {
        return GeneratorHelper.functionName("throw", constructor.getClazz());
    }

    private void generateSig(StringBuilder out, boolean cStrings) {
        generateFunctionSignature(getTypes(), out, constructor.getMethod(), generateFunctionName(), cStrings);
    }

    private void generateImpl(StringBuilder out) {
        generateSig(out, false);
        out.append(" {\n");
        out.append("    jclass class = (*env)->FindClass(env, \"").append(jniClassNameOf(constructor.getClazz())).append("\");\n");
        out.append("    (*env)->ThrowNew(env, class");
        for (MethodParam param : constructor.getMethod().getParams()) {
            out.append(", ").append(param.getName());
        }
        out.append(");\n");
        out.append("}\n");
    }

    private void generateImplOverload(StringBuilder out) {
        generateSig(out, true);
        generateJStringFunctionOverloadBody(getTypes(), out, generateFunctionName(), constructor.getMethod());
    }

    @Override
    public void generateDeclarations(StringBuilder out) {
        generateSig(out, false);
        out.append(";\n");
        if (GeneratorHelper.hasStringParameter(getTypes(), constructor.getMethod())) {
            generateSig(out, true);
            out.append(";\n");
        }
        out.append("\n");
    }

    @Override
    public void generateImplementations(StringBuilder out) {
        generateImpl(out);
        out.append("\n");
        if (GeneratorHelper.hasStringParameter(getTypes(), constructor.getMethod())) {
            generateImplOverload(out);
            out.append("\n");
        }
        out.append("\n");
    }
}
