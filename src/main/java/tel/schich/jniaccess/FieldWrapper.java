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

import java.util.Collections;
import java.util.List;

import static tel.schich.jniaccess.GeneratorHelper.*;

public class FieldWrapper extends WrappedElement {
    private final AccessedClass clazz;
    private final AccessedField field;
    private final List<MethodParam> writeParams;

    public FieldWrapper(Types types, CacheMode cacheMode, AccessedClass clazz, AccessedField field) {
        super(types, cacheMode);
        this.clazz = clazz;
        this.field = field;
        writeParams = Collections.singletonList(new MethodParam("value", field.getElement(), field.getType()));
    }

    public AccessedField getField() {
        return field;
    }

    @Override
    public AccessedClass getHostClass() {
        return clazz;
    }

    private void generateReadSig(StringBuilder out) {
        String name = GeneratorHelper.functionName("read", clazz, field.getName());

        generateFunctionSignature(getTypes(), out, name, field.getType(), !field.isStatic(), Collections.emptyList(), false);
    }

    private void generateReadImpl(StringBuilder out) {
        generateReadSig(out);
        generateImplBody(out, false);
    }

    private String generateWriteFunctionName() {
        return GeneratorHelper.functionName("write", clazz, field.getName());
    }

    private void generateWriteSig(StringBuilder out, boolean cStrings) {
        generateFunctionSignature(getTypes(), out, generateWriteFunctionName(), TypeHelper.getVoid(getTypes()), !field.isStatic(), writeParams, cStrings);
    }

    private void generateWriteImpl(StringBuilder out) {
        generateWriteSig(out, false);
        generateImplBody(out, true);
    }

    private void generateImplBody(StringBuilder out, boolean set) {
        out.append(" {\n");
        generateClassLookup(out, "class", true, clazz, "    ");
        out.append('\n');
        generateFieldLookup(getTypes(), out, "field", true, "class", field, "    ");
        out.append('\n');
        out.append("    (*env)->");
        out.append(set ? "Set" : "Get");
        if (field.isStatic()) {
            out.append("Static");
        }
        out.append(TypeHelper.getJNIHelperType(field.getType()));
        out.append("Field(env, ");
        out.append(field.isStatic() ? "class" : "instance");
        out.append(", field");
        if (set) {
            out.append(", value");
        }
        out.append(");\n");
        out.append("}\n");
    }

    @Override
    public void generateDeclarations(StringBuilder out) {
        generateReadSig(out);
        out.append(";\n");
        if (!field.isFinal()) {
            generateWriteSig(out, false);
            out.append(";\n");
            if (TypeHelper.isString(getTypes(), field.getType())) {
                generateWriteSig(out, true);
                out.append(";\n");
            }
        }
        out.append("\n");
    }

    @Override
    public void generateImplementations(StringBuilder out) {
        generateReadImpl(out);
        out.append("\n");
        if (!field.isFinal()) {
            generateWriteImpl(out);
            out.append("\n");
            if (TypeHelper.isString(getTypes(), field.getType())) {
                generateJStringFunctionOverload(getTypes(), out, generateWriteFunctionName(), !field.isStatic(), TypeHelper.getVoid(getTypes()), writeParams);
                out.append("\n");
            }
        }
        out.append("\n");
    }
}
