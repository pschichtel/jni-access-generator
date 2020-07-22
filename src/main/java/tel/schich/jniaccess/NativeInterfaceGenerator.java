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

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class NativeInterfaceGenerator {
    public static List<ClassWithNatives> searchNativeMethods(RoundEnvironment roundEnv) {
        final List<ClassWithNatives> methods = new ArrayList<>();
        for (Element rootElement : roundEnv.getRootElements()) {
            methods.addAll(searchNativeMethods(rootElement));
        }
        return methods;
    }

    public static ArrayList<ClassWithNatives> searchNativeMethods(Element element) {
        ArrayList<ClassWithNatives> accumulator = new ArrayList<>();
        searchNativeMethods(element, accumulator);
        return accumulator;
    }

    private static void searchNativeMethods(Element element, List<ClassWithNatives> accumulator) {
        switch (element.getKind()) {
            case CLASS:
            case ENUM:
                final List<ExecutableElement> methods = new ArrayList<>();
                final List<VariableElement> constants = new ArrayList<>();
                for (Element enclosedElement : element.getEnclosedElements()) {
                    if (enclosedElement.getKind() == ElementKind.METHOD) {
                        final ExecutableElement executable = (ExecutableElement) enclosedElement;
                        if (executable.getModifiers().contains(Modifier.NATIVE)) {
                            methods.add(executable);
                        }
                    } else if (enclosedElement.getKind() == ElementKind.FIELD) {
                        VariableElement variable = (VariableElement) enclosedElement;
                        Set<Modifier> modifiers = variable.getModifiers();
                        if (modifiers.contains(Modifier.FINAL) && modifiers.contains(Modifier.STATIC) && variable.getConstantValue() != null) {
                            constants.add(variable);
                        }
                    } else {
                        searchNativeMethods(enclosedElement, accumulator);
                    }
                }
                if ((methods.size() + constants.size()) > 0) {
                    accumulator.add(new ClassWithNatives(element, methods, constants));
                }
                break;
        }
    }

    public static String buildFullyQualifiedElementName(Element element) {
        if (element instanceof QualifiedNameable) {
            return ((QualifiedNameable) element).getQualifiedName().toString();
        } else {
            final Element enclosing = element.getEnclosingElement();
            final String prefix;
            if (enclosing != null) {
                prefix = buildFullyQualifiedElementName(enclosing) + ".";
            } else {
                prefix = "";
            }
            return prefix + element.getSimpleName().toString();
        }
    }

    public static class ClassWithNatives {
        private final Element theClass;
        private final List<ExecutableElement> methods;
        private final List<VariableElement> constants;

        public ClassWithNatives(Element theClass, List<ExecutableElement> methods, List<VariableElement> constants) {
            this.theClass = theClass;
            this.methods = methods;
            this.constants = constants;
        }

        public Element getTheClass() {
            return theClass;
        }

        public List<ExecutableElement> getMethods() {
            return methods;
        }

        public List<VariableElement> getConstants() {
            return constants;
        }
    }
}
