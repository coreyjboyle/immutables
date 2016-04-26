/*
   Copyright 2016 Immutables Authors and Contributors

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package org.immutables.value.processor.meta;

import com.google.common.collect.Iterators;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import javax.lang.model.element.Element;
import javax.lang.model.element.Parameterizable;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.TypeMirror;
import org.immutables.value.processor.meta.Proto.DeclaringType;
import org.immutables.value.processor.meta.Proto.Protoclass;

public final class Generics implements Iterable<String> {
  private static final String[] NO_BOUNDS = new String[0];
  private static final Parameter[] NO_PARAMETERS = new Parameter[0];
  public final String declaration;
  public final String arguments;
  public final Parameter[] paramerters;
  private final String[] vars;

  Generics(Protoclass protoclass, Element element) {
    this.paramerters = computeParameters(protoclass, element);
    this.vars = computeVars(paramerters);
    if (this.paramerters != NO_PARAMETERS) {
      this.declaration = formatParameters(paramerters, true);
      this.arguments = formatParameters(paramerters, false);
    } else {
      this.declaration = "";
      this.arguments = "";
    }
  }

  private static Parameter[] computeParameters(
      final Protoclass protoclass,
      final Element element) {
    if (!(element instanceof Parameterizable)) {
      return NO_PARAMETERS;
    }

    final List<? extends TypeParameterElement> typeParameters =
        ((Parameterizable) element).getTypeParameters();

    if (typeParameters.isEmpty()) {
      return NO_PARAMETERS;
    }

    class Creator {
      final String[] vars = collectVars(typeParameters);
      final DeclaringType declaringType = protoclass.environment().round().inferDeclaringTypeFor(element);

      Parameter[] create() {
        final Parameter[] parameters = new Parameter[typeParameters.size()];
        int c = 0;
        for (TypeParameterElement e : typeParameters) {
          parameters[c++] = toParameter(e);
        }
        return parameters;
      }

      Parameter toParameter(TypeParameterElement e) {
        return new Parameter(
            e.getSimpleName().toString(),
            boundsFrom(e));
      }

      String[] boundsFrom(TypeParameterElement e) {
        List<? extends TypeMirror> boundMirrors = e.getBounds();
        if (boundMirrors.isEmpty()) {
          return NO_BOUNDS;
        }
        String[] bounds = new String[boundMirrors.size()];
        int c = 0;
        for (TypeMirror m : boundMirrors) {
          TypeStringProvider provider = newProvider(m);
          provider.process();
          bounds[c++] = provider.returnTypeName();
        }
        if (bounds.length == 1 && bounds[0].equals(Object.class.getName())) {
          return NO_BOUNDS;
        }
        return bounds;
      }

      TypeStringProvider newProvider(TypeMirror type) {
        return new TypeStringProvider(
            protoclass.report(),
            element,
            type,
            declaringType,
            vars);
      }
    }
    return new Creator().create();
  }

  private static String[] collectVars(List<? extends TypeParameterElement> typeParameters) {
    String[] vars = new String[typeParameters.size()];
    int c = 0;
    for (TypeParameterElement p : typeParameters) {
      vars[c++] = p.getSimpleName().toString();
    }
    return vars;
  }

  private static String[] computeVars(Parameter[] paramerters) {
    String[] vars = new String[paramerters.length];
    for (int i = 0; i < paramerters.length; i++) {
      vars[i] = paramerters[i].var;
    }
    return vars;
  }

  private static String formatParameters(Parameter[] paramerters, boolean outputBounds) {
    StringBuilder builder = new StringBuilder("<");
    for (Parameter p : paramerters) {
      if (builder.length() > 1) {
        builder.append(", ");
      }
      builder.append(p.var);
      if (outputBounds) {
        formatBoundsIfPresent(builder, p);
      }
    }
    return builder.append(">").toString();
  }

  private static void formatBoundsIfPresent(StringBuilder builder, Parameter p) {
    if (p.bounds.length > 0) {
      builder.append(" extends ").append(p.bounds[0]);
      for (int i = 1; i < p.bounds.length; i++) {
        builder.append(" & ").append(p.bounds[i]);
      }
    }
  }

  public static final class Parameter {
    public final String var;
    public final String[] bounds;

    Parameter(String var, String[] bounds) {
      this.var = var;
      this.bounds = bounds;
    }

    @Override
    public String toString() {
      return var;
    }
  }

  public boolean hasParameter(String var) {
    for (Parameter p : paramerters) {
      if (p.var.equals(var)) {
        return true;
      }
    }
    return false;
  }

  public boolean isEmpty() {
    return this.paramerters == NO_PARAMETERS;
  }

  public String args() {
    return arguments;
  }

  public String spaceAfter() {
    return isEmpty() ? "" : (declaration + " ");
  }

  public String diamond() {
    return isEmpty() ? "" : "<>";
  }

  public String def() {
    return isEmpty() ? "" : (" " + declaration);
  }

  public String[] vars() {
    return vars.clone();
  }

  @Override
  public String toString() {
    return declaration;
  }

  @Override
  public Iterator<String> iterator() {
    return isEmpty()
        ? Collections.<String>emptyIterator()
        : Iterators.forArray(vars);
  }
}
