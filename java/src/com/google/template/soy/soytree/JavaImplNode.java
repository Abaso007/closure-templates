/*
 * Copyright 2021 Google Inc.
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

package com.google.template.soy.soytree;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.SoyNode.Kind;
import java.util.Arrays;
import java.util.List;

/** Java implementation for an extern. */
public final class JavaImplNode extends ExternImplNode {
  private static final SoyErrorKind INVALID_IMPL_ATTRIBUTE =
      SoyErrorKind.of("''{0}'' is not a valid attribute.");
  private static final String CLASS = "class";
  private static final String METHOD = "method";
  private static final String PARAMS = "params";
  private static final String RETURN = "return";
  public static final String FIELDS = String.format("%s,%s,%s,%s", CLASS, METHOD, PARAMS, RETURN);
  private static final SoyErrorKind UNEXPECTED_ARGS =
      SoyErrorKind.of("Java implementations require attributes " + FIELDS + " .");

  private CommandTagAttribute module;
  private CommandTagAttribute function;
  private CommandTagAttribute args;
  private CommandTagAttribute returnType;

  public JavaImplNode(
      int id,
      SourceLocation sourceLocation,
      List<CommandTagAttribute> attributes,
      ErrorReporter errorReporter) {
    super(id, sourceLocation, "javaimpl");

    if (attributes.size() != 4) {
      errorReporter.report(sourceLocation, UNEXPECTED_ARGS);
    }

    for (CommandTagAttribute attr : attributes) {
      if (attr.hasName(CLASS)) {
        this.module = attr;
      } else if (attr.hasName(METHOD)) {
        this.function = attr;
      } else if (attr.hasName(PARAMS)) {
        this.args = attr;
      } else if (attr.hasName(RETURN)) {
        this.returnType = attr;
      } else {
        errorReporter.report(attr.getSourceLocation(), INVALID_IMPL_ATTRIBUTE, attr.getName());
      }
    }
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private JavaImplNode(JavaImplNode orig, CopyState copyState) {
    super(orig, copyState);
    this.module = orig.module;
    this.function = orig.function;
    this.args = orig.args;
    this.returnType = orig.returnType;
  }

  @Override
  public Kind getKind() {
    return Kind.JAVA_IMPL_NODE;
  }

  @Override
  public JavaImplNode copy(CopyState copyState) {
    return new JavaImplNode(this, copyState);
  }

  public String module() {
    return module.getValue();
  }

  public String function() {
    return function.getValue();
  }

  public ImmutableList<String> args() {
    return ImmutableList.copyOf(Arrays.asList(args.getValue().split(",")));
  }

  public String returnType() {
    return returnType.getValue();
  }
}