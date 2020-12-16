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

import java.util.List;

import static tel.schich.jniaccess.GeneratorHelper.*;


public class ThrowWrapper extends MethodBackedWrapper {
    private final ConstructorCall constructor;

    public ThrowWrapper(Types types, boolean performanceCritical, ConstructorCall constructor) {
        super(types, performanceCritical, constructor.getMethod());
        this.constructor = constructor;
    }

    @Override
    protected String generateFunctionName() {
        return GeneratorHelper.functionName("throw", constructor.getClazz());
    }

    @Override
    protected void generateImpl(StringBuilder out) {
        generateSig(out, false);
        out.append(" {\n");
        generateClassLookup(out, "class", constructor.getClazz(), "    ");
        out.append('\n');
        AccessedMethod method = constructor.getMethod();
        List<MethodParam> params = method.getParams();
        if (params.size() == 1 && TypeHelper.isString(getTypes(), params.get(0).getType())) {
            out.append("    (*env)->ThrowNew(env, class, ").append(params.get(0).getName()).append(");\n");
        } else {
            generateMethodLookup(getTypes(), out, "ctor", "class", method, "    ");
            out.append('\n');
            out.append("    jthrowable t = ");
            generateNewObjectCreation(out, "class", "ctor", method);
            out.append('\n');
            out.append("    (*env)->Throw(env, t);\n");
        }
        out.append("}\n");
    }
}
