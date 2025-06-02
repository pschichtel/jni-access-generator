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

public class NewInstanceWrapper extends MethodBackedWrapper {
    private final ConstructorCall constructor;

    public NewInstanceWrapper(Types types, CacheMode cacheMode, ConstructorCall constructor) {
        super(types, cacheMode, constructor.getMethod());
        this.constructor = constructor;
    }

    @Override
    public AccessedClass getHostClass() {
        return constructor.getClazz();
    }

    @Override
    protected String generateFunctionName() {
        return GeneratorHelper.functionName("create", constructor.getClazz());
    }

    @Override
    protected void generateSig(StringBuilder out, boolean cStrings) {
        generateFunctionSignature(getTypes(), out, constructor.getMethod(), constructor.getClazz().getType(), generateFunctionName(), cStrings);
    }

    @Override
    protected void generateImpl(StringBuilder out) {
        generateInstantiatingMethod(out, this, constructor, (clazz, instance) -> {
            out.append("    return ");
            generateNewObjectCreation(out, clazz, instance, constructor.getMethod());
            out.append('\n');
        });
    }
}
