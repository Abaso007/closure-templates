/*
 * Copyright 2017 Google Inc.
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

package com.google.template.soy.idomsrc;

import static com.google.template.soy.jssrc.dsl.Expressions.id;
import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_SOY_DATA;
import static com.google.template.soy.jssrc.internal.JsRuntime.JS_TO_PROTO_PACK_FN_BASE;

import com.google.common.collect.ImmutableMap;
import com.google.common.html.types.SafeHtmlProto;
import com.google.template.soy.jssrc.dsl.Expression;
import com.google.template.soy.jssrc.dsl.GoogRequire;

/**
 * Common runtime symbols for incrementaldom.
 *
 * <p>Unlike jssrc, incrementaldom declares {@code goog.module}s and therefore uses aliased {@code
 * goog.require} statements.
 */
final class IdomRuntime {

  static final GoogRequire INCREMENTAL_DOM_LIB_TYPE =
      GoogRequire.createTypeRequireWithAlias(
          "google3.javascript.template.soy.api_idom", "incrementaldomlib");

  static final GoogRequire INCREMENTAL_DOM_LIB =
      GoogRequire.createWithAlias("google3.javascript.template.soy.api_idom", "incrementaldomlib");

  public static final Expression BUFFERING_IDOM_RENDERER =
      INCREMENTAL_DOM_LIB.dotAccess("BufferingIncrementalDomRenderer");

  private static final Expression SANITIZED_CONTENT_KIND =
      GOOG_SOY_DATA.dotAccess("SanitizedContentKind");

  static final GoogRequire SOY_IDOM =
      GoogRequire.createWithAlias("google3.javascript.template.soy.soyutils_idom", "soyIdom");

  public static final String INCREMENTAL_DOM_PARAM_NAME = "idomRenderer";
  public static final Expression INCREMENTAL_DOM = id(INCREMENTAL_DOM_PARAM_NAME);

  public static final Expression INCREMENTAL_DOM_OPEN = INCREMENTAL_DOM.dotAccess("open");
  public static final Expression INCREMENTAL_DOM_OPEN_SIMPLE =
      INCREMENTAL_DOM.dotAccess("openSimple");

  public static final Expression INCREMENTAL_DOM_KEEP_GOING =
      INCREMENTAL_DOM.dotAccess("keepGoing");

  public static final Expression NODE_PART = SOY_IDOM.dotAccess("NODE_PART");

  public static final Expression INCREMENTAL_DOM_ELEMENT_CLOSE =
      INCREMENTAL_DOM.dotAccess("elementClose");

  public static final Expression INCREMENTAL_DOM_CLOSE = INCREMENTAL_DOM.dotAccess("close");

  public static final Expression INCREMENTAL_DOM_APPLY_STATICS =
      INCREMENTAL_DOM.dotAccess("applyStatics");

  public static final Expression INCREMENTAL_DOM_APPLY_ATTRS =
      INCREMENTAL_DOM.dotAccess("applyAttrs");

  public static final Expression INCREMENTAL_DOM_ENTER_VELOG =
      INCREMENTAL_DOM.dotAccess("enterVeLog");

  public static final Expression INCREMENTAL_DOM_EXIT_VELOG =
      INCREMENTAL_DOM.dotAccess("exitVeLog");

  public static final Expression INCREMENTAL_DOM_TEXT = INCREMENTAL_DOM.dotAccess("text");
  public static final Expression INCREMENTAL_DOM_PRINT = INCREMENTAL_DOM.dotAccess("print");
  public static final Expression INCREMENTAL_DOM_VISIT_HTML_COMMENT =
      INCREMENTAL_DOM.dotAccess("visitHtmlCommentNode");

  public static final Expression INCREMENTAL_DOM_ATTR = INCREMENTAL_DOM.dotAccess("attr");

  public static final Expression INCREMENTAL_DOM_PUSH_KEY = INCREMENTAL_DOM.dotAccess("pushKey");
  public static final Expression INCREMENTAL_DOM_POP_KEY = INCREMENTAL_DOM.dotAccess("popKey");
  public static final Expression INCREMENTAL_DOM_PUSH_MANUAL_KEY =
      INCREMENTAL_DOM.dotAccess("pushManualKey");
  public static final Expression INCREMENTAL_DOM_POP_MANUAL_KEY =
      INCREMENTAL_DOM.dotAccess("popManualKey");

  public static final Expression SOY_IDOM_MAKE_HTML = SOY_IDOM.dotAccess("$$makeHtml");

  public static final Expression SOY_IDOM_TYPE_HTML = SANITIZED_CONTENT_KIND.dotAccess("HTML");
  public static final Expression SOY_IDOM_TYPE_ATTRIBUTE =
      SANITIZED_CONTENT_KIND.dotAccess("ATTRIBUTES");

  public static final Expression SOY_IDOM_MAKE_ATTRIBUTES = SOY_IDOM.dotAccess("$$makeAttributes");

  public static final Expression SOY_IDOM_CALL_DYNAMIC_ATTRIBUTES =
      SOY_IDOM.dotAccess("$$callDynamicAttributes");

  public static final Expression SOY_IDOM_CALL_DYNAMIC_CSS = SOY_IDOM.dotAccess("$$callDynamicCss");
  public static final Expression SOY_IDOM_CALL_DYNAMIC_JS = SOY_IDOM.dotAccess("$$callDynamicJs");
  public static final Expression SOY_IDOM_CALL_DYNAMIC_TEXT =
      SOY_IDOM.dotAccess("$$callDynamicText");

  public static final Expression SOY_IDOM_CALL_DYNAMIC_HTML =
      SOY_IDOM.dotAccess("$$callDynamicHTML");

  public static final Expression SOY_IDOM_PRINT_DYNAMIC_ATTR =
      SOY_IDOM.dotAccess("$$printDynamicAttr");

  public static final Expression SOY_IDOM_IS_TRUTHY = SOY_IDOM.dotAccess("$$isTruthy");
  public static final Expression SOY_IDOM_HAS_CONTENT = SOY_IDOM.dotAccess("$$hasContent");
  public static final Expression SOY_IDOM_IS_TRUTHY_NON_EMPTY =
      SOY_IDOM.dotAccess("$$isTruthyNonEmpty");
  public static final Expression SOY_IDOM_EMPTY_TO_UNDEFINED =
      SOY_IDOM.dotAccess("$$emptyToUndefined");

  public static final Expression SOY_IDOM_PRINT_WITH_NODE_PARTS =
      SOY_IDOM.dotAccess("$$printWithNodeParts");

  public static final Expression INCREMENTAL_DOM_EVAL_LOG_FN =
      INCREMENTAL_DOM.dotAccess("evalLoggingFunction");

  public static final Expression INCREMENTAL_DOM_LOGGING_FUNCTION_ATTR =
      INCREMENTAL_DOM.dotAccess("loggingFunctionAttr");

  /** Prefix for state vars of stateful template objects. */
  public static final String STATE_PREFIX = "state_";

  /**
   * Within a template, a variable needs to be allocated so that direct accesses of this.state_X
   * aren't used in closures. This defines a variable prefix for those variables.
   */
  public static final String STATE_VAR_PREFIX = "$$state$$";

  /** The JavaScript method to pack a sanitized object into a safe proto. */
  public static final ImmutableMap<String, Expression> IDOM_JS_TO_PROTO_PACK_FN =
      ImmutableMap.<String, Expression>builder()
          .putAll(JS_TO_PROTO_PACK_FN_BASE)
          .put(
              SafeHtmlProto.getDescriptor().getFullName(),
              GoogRequire.createWithAlias("soydata.converters.idom", "$soyDataConverters")
                  .reference()
                  .dotAccess("packSanitizedHtmlToProtoSoyRuntimeOnly"))
          .buildOrThrow();

  private IdomRuntime() {}
}
