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

    public ThrowWrapper(Types types, CacheMode cacheMode, ConstructorCall constructor) {
        super(types, cacheMode, constructor.getMethod());
        this.constructor = constructor;
    }

    @Override
    public AccessedClass getHostClass() {
        return constructor.getClazz();
    }

    @Override
    protected String generateFunctionName() {
        return GeneratorHelper.functionName("throw", constructor.getClazz());
    }

    @Override
    protected void generateImpl(StringBuilder out) {
        generateInstantiatingMethod(out, this, constructor, (clazz, instance) -> {
            out.append("    jthrowable t = ");
            generateNewObjectCreation(out, clazz, instance, constructor.getMethod());
            out.append('\n');
            out.append("    (*env)->Throw(env, t);\n");
        });
    }

    @Override
    protected void generateCStringImplementation(StringBuilder out) {
        AccessedMethod method = constructor.getMethod();
        List<MethodParam> params = method.getParams();
        final boolean singleStringParam = params.size() == 1 && TypeHelper.isString(getTypes(), params.get(0).getType());
        if (singleStringParam) {
            generateSig(out, true);
            out.append(" {\n");
            generateClassLookup(out, "class", constructor.getClazz(), "    ");
            out.append('\n');
            out.append("    (*env)->ThrowNew(env, class, ").append(cStringName(params.get(0))).append(");\n");
            out.append("}\n");
        } else {
            generateJStringFunctionOverload(getTypes(), out, generateFunctionName(), method);
        }
    }
}
