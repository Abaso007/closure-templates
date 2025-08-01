/*
 * Copyright 2025 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.template.soy.basicfunctions;

import com.google.template.soy.data.SoyValue;
import com.google.template.soy.plugin.java.internal.SoyJavaExternFunction;
import com.google.template.soy.plugin.java.restricted.JavaValueFactory;
import com.google.template.soy.plugin.javascript.restricted.JavaScriptPluginContext;
import com.google.template.soy.plugin.javascript.restricted.JavaScriptValue;
import com.google.template.soy.plugin.javascript.restricted.JavaScriptValueFactory;
import com.google.template.soy.plugin.javascript.restricted.SoyJavaScriptSourceFunction;
import com.google.template.soy.plugin.python.restricted.PythonPluginContext;
import com.google.template.soy.plugin.python.restricted.PythonValue;
import com.google.template.soy.plugin.python.restricted.PythonValueFactory;
import com.google.template.soy.plugin.python.restricted.SoyPythonSourceFunction;
import com.google.template.soy.shared.restricted.Signature;
import com.google.template.soy.shared.restricted.SoyFunctionSignature;
import com.google.template.soy.shared.restricted.SoyPureFunction;
import java.lang.reflect.Method;
import java.util.List;

/** Clone of JavaScript Number.isInteger() */
@SoyFunctionSignature(
    name = "Number_isInteger",
    value = {
      @Signature(returnType = "bool", parameterTypes = "?"),
    })
@SoyPureFunction
class IsIntegerFunction
    implements SoyJavaScriptSourceFunction, SoyPythonSourceFunction, SoyJavaExternFunction {

  private static final class Methods {
    static final Method UNBOXED =
        JavaValueFactory.createMethod(BasicFunctionsRuntime.class, "isInteger", double.class);
    static final Method BOXED =
        JavaValueFactory.createMethod(BasicFunctionsRuntime.class, "isInteger", SoyValue.class);
  }

  @Override
  public Method getExternJavaMethod(List<RuntimeType> argTypes) {
    return argTypes.get(0) == RuntimeType.DOUBLE ? Methods.UNBOXED : Methods.BOXED;
  }

  @Override
  public boolean adaptArgs() {
    return false;
  }

  @Override
  public JavaScriptValue applyForJavaScriptSource(
      JavaScriptValueFactory factory, List<JavaScriptValue> args, JavaScriptPluginContext context) {
    return factory.global("Number").invokeMethod("isInteger", args);
  }

  @Override
  public PythonValue applyForPythonSource(
      PythonValueFactory factory, List<PythonValue> args, PythonPluginContext context) {
    return factory.global("runtime").getProp("is_integer").call(args);
  }
}
