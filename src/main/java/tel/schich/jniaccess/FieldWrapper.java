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
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

import static tel.schich.jniaccess.GeneratorHelper.jniClassNameOf;

public class FieldWrapper extends WrappedElement {
    private final AccessedClass clazz;
    private final AccessedField field;

    public FieldWrapper(Types types, boolean performanceCritical, AccessedClass clazz, AccessedField field) {
        super(types, performanceCritical);
        this.clazz = clazz;
        this.field = field;
    }

    public AccessedClass getClazz() {
        return clazz;
    }

    public AccessedField getField() {
        return field;
    }

    private void generateReadSig(StringBuilder out) {
        out.append(TypeHelper.getCType(getTypes(), field.getType()))
            .append(" read_")
            .append(clazz.getElement().getQualifiedName().toString().replace('.', '_'))
            .append('_')
            .append(field.getElement().getSimpleName().toString())
            .append("(JNIEnv *env");
        if (!field.isStatic()) {
            out.append(", jobject instance");
        }
        out.append(")");
    }

    private void generateReadImpl(StringBuilder out) {
        String lookup = field.isStatic() ? "GetStaticFieldID" : "GetFieldID";
        Name fieldName = field.getElement().getSimpleName();
        String sig = TypeHelper.getJNIType(getTypes(), field.getType());
        String accessorType = TypeHelper.getJNIHelperType(field.getType());

        generateReadSig(out);
        out.append(" {\n");
        out.append("    jclass class = (*env)->FindClass(env, \"").append(jniClassNameOf(clazz)).append("\");\n");
        out.append("    jfieldID field = (*env)->").append(lookup).append("(env, class, \"").append(fieldName).append("\", \"").append(sig).append("\");\n");
        if (field.isStatic()) {
            out.append("    return (*env)->GetStatic").append(accessorType).append("Field(env, class, field);\n");
        } else {
            out.append("    return (*env)->Get").append(accessorType).append("Field(env, instance, field);\n");
        }
        out.append("}\n");
    }

    private void generateWriteSig(StringBuilder out) {
        out.append("void write_")
            .append(clazz.getElement().getQualifiedName().toString().replace('.', '_'))
            .append('_')
            .append(field.getElement().getSimpleName().toString())
            .append("(JNIEnv *env");
        if (!field.isStatic()) {
            out.append(", jobject instance");
        }
        out.append(", ")
            .append(TypeHelper.getCType(getTypes(), field.getType()))
            .append(" value)");
    }

    private void generateWriteImpl(StringBuilder out) {
        String lookup = field.isStatic() ? "GetStaticFieldID" : "GetFieldID";
        Name fieldName = field.getElement().getSimpleName();
        String sig = TypeHelper.getJNIType(getTypes(), field.getType());
        String accessorType = TypeHelper.getJNIHelperType(field.getType());

        generateWriteSig(out);
        out.append(" {\n");
        out.append("    jclass class = (*env)->FindClass(env, \"").append(jniClassNameOf(clazz)).append("\");\n");
        out.append("    jfieldID field = (*env)->").append(lookup).append("(env, class, \"").append(fieldName).append("\", \"").append(sig).append("\");\n");
        if (field.isStatic()) {
            out.append("    (*env)->SetStatic").append(accessorType).append("Field(env, class, field, value);\n");
        } else {
            out.append("    (*env)->Set").append(accessorType).append("Field(env, instance, field, value);\n");
        }
        out.append("}\n");
    }

    @Override
    public void generateDeclarations(StringBuilder out) {
        generateReadSig(out);
        out.append(";\n");
        generateWriteSig(out);
        out.append(";\n\n");
    }

    @Override
    public void generateImplementations(StringBuilder out) {
        generateReadImpl(out);
        out.append("\n");
        generateWriteImpl(out);
        out.append("\n\n");
    }
}
