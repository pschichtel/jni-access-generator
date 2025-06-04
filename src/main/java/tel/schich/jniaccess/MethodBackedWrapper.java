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

import javax.lang.model.util.Types;

import static tel.schich.jniaccess.GeneratorHelper.*;

public abstract class MethodBackedWrapper extends WrappedElement {

    private final AccessedMethod method;

    public MethodBackedWrapper(Types types, CacheMode cacheMode, AccessedMethod method) {
        super(types, cacheMode);
        this.method = method;
    }

    public AccessedMethod getMethod() {
        return method;
    }

    protected abstract String generateFunctionName();

    protected void generateSig(StringBuilder out, boolean cStrings) {
        generateFunctionSignature(getTypes(), out, method, generateFunctionName(), cStrings);
    }

    protected abstract void generateImpl(StringBuilder out, String moduleNamespace);

    @Override
    public final void generateDeclarations(StringBuilder out) {
        generateSig(out, false);
        out.append(";\n");
        if (hasStringParameter(getTypes(), method)) {
            generateSig(out, true);
            out.append(";\n");
        }
        out.append("\n");
    }

    protected void generateBaseImplementation(StringBuilder out, String moduleNamespace) {
        generateImpl(out, moduleNamespace);
        out.append("\n");
    }

    protected void generateCStringImplementation(StringBuilder out) {
        if (hasStringParameter(getTypes(), method)) {
            generateJStringFunctionOverload(getTypes(), out, generateFunctionName(), method);
            out.append("\n");
        }
    }

    @Override
    public void generateImplementations(StringBuilder out, String moduleNamespace) {
        generateBaseImplementation(out, moduleNamespace);
        generateCStringImplementation(out);
        out.append("\n");
    }
}
