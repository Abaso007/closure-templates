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

package com.google.template.soy.sharedpasses.render;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyIterable;
import com.google.template.soy.data.SoyLegacyObjectMap;
import com.google.template.soy.data.SoyList;
import com.google.template.soy.data.SoyMap;
import com.google.template.soy.data.SoyProtoValue;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoySet;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.TemplateValue;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NumberData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.types.SoyProtoType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.UnionType;
import java.util.Optional;

/** Implements runtime type checks for tofu. */
public final class TofuTypeChecks {

  private static final CheckResult PASS = new CheckResult(true, Optional.empty());
  private static final CheckResult FAIL = new CheckResult(false, Optional.empty());

  private static final class CheckResult {
    static CheckResult fromBool(boolean result) {
      return result ? PASS : FAIL;
    }

    static CheckResult passWithWarning(Runnable onPass) {
      return new CheckResult(true, Optional.of(onPass));
    }

    final boolean result;
    final Optional<Runnable> onPass;

    CheckResult(boolean result, Optional<Runnable> onPass) {
      this.result = result;
      this.onPass = checkNotNull(onPass);
      if (onPass.isPresent() && !result) {
        throw new IllegalArgumentException("onPass values are only valid for successful results");
      }
    }

    CheckResult or(CheckResult other) {
      if (!result) {
        return other;
      }
      if (!other.result) {
        return this;
      }
      // they are both true, we need to merge the runnables
      // if either runnable is absent, we can drop the other runnable, if both runnables are present
      // we preserve both
      if (!onPass.isPresent()) {
        return this;
      }
      if (!other.onPass.isPresent()) {
        return other;
      }
      // they are both true and both have runnables, concatenate the runnables
      return passWithWarning(
          () -> {
            this.onPass.get().run();
            other.onPass.get().run();
          });
    }
  }

  /**
   * Returns true if the given {@linkplain SoyValue value} is an instance of the {@linkplain SoyType
   * type}. For generic types, this only checks the overall shape of the type (list, map, etc) since
   * Java type erasure does not allow the type parameters to be checked. Also, in some cases the
   * "instanceof" test may be defined somewhat loosely - for example, sanitized types may be
   * considered instances of type string, since they are usable in any context where a string is
   * usable, even though internally they are not implemented as subclasses of string. This test does
   * not take into account automatic coercions, such as converting to string or boolean.
   *
   * @param type The type to test.
   * @param value The value to check against the type.
   * @return True if the value is an instance of the type.
   */
  public static boolean isInstance(SoyType type, SoyValue value) {
    CheckResult result = doIsInstance(type, value);
    if (result.result) {
      result.onPass.ifPresent(Runnable::run);
      return true;
    }
    return false;
  }

  private static CheckResult doIsInstance(SoyType type, SoyValue value) {
    switch (type.getKind()) {
      case ANY:
      case UNKNOWN:
        return PASS;
      case ATTRIBUTES:
        return isSanitizedofKind(value, ContentKind.ATTRIBUTES);
      case CSS:
        return isSanitizedofKind(value, ContentKind.CSS);
      case BOOL:
        return CheckResult.fromBool(value instanceof BooleanData);
      case FLOAT:
      case NUMBER:
      case INT:
        return CheckResult.fromBool(value instanceof NumberData);
      case HTML:
      case ELEMENT:
        return isSanitizedofKind(value, ContentKind.HTML);
      case JS:
        return isSanitizedofKind(value, ContentKind.JS);
      case ITERABLE:
        return CheckResult.fromBool(value instanceof SoyIterable);
      case LIST:
        return CheckResult.fromBool(value instanceof SoyList);
      case SET:
        return CheckResult.fromBool(value instanceof SoySet);
      case MAP:
        return CheckResult.fromBool(value instanceof SoyMap);
      case LEGACY_OBJECT_MAP:
        return CheckResult.fromBool(value instanceof SoyLegacyObjectMap);
      case NULL:
        return CheckResult.fromBool(value.isNull());
      case UNDEFINED:
        return CheckResult.fromBool(value.isUndefined());
      case MESSAGE:
        return CheckResult.fromBool(value instanceof SoyProtoValue);
      case PROTO:
        // proto descriptors use instance equality.
        return CheckResult.fromBool(
            value instanceof SoyProtoValue
                && value.getProto().getDescriptorForType()
                    == ((SoyProtoType) type).getDescriptor());
      case PROTO_ENUM:
        // TODO(lukes): this should also assert that the value is in range
        return CheckResult.fromBool(value instanceof IntegerData);
      case RECORD:
        // This allows casting a map or proto to a record.
        return CheckResult.fromBool(value instanceof SoyRecord);
      case STRING:
        return CheckResult.fromBool(value instanceof StringData);
      case TEMPLATE:
        return CheckResult.fromBool(value instanceof TemplateValue);
      case TRUSTED_RESOURCE_URI:
        return isSanitizedofKind(value, ContentKind.TRUSTED_RESOURCE_URI);
      case UNION:
        CheckResult unionResult = FAIL;
        for (SoyType memberType : ((UnionType) type).getMembers()) {
          unionResult = unionResult.or(doIsInstance(memberType, value));
        }
        return unionResult;
      case URI:
        return CheckResult.fromBool(
            value instanceof SanitizedContent
                && (((SanitizedContent) value).getContentKind() == ContentKind.URI
                    || ((SanitizedContent) value).getContentKind()
                        == ContentKind.TRUSTED_RESOURCE_URI));
      case VE:
        // Dynamic VE support is minimally implemented in Tofu: ve and ve_data objects are always
        // UndefinedVe.
        return CheckResult.fromBool(value == EvalVisitor.UNDEFINED_VE);
      case VE_DATA:
        return CheckResult.fromBool(value == EvalVisitor.UNDEFINED_VE_DATA);
      case FUNCTION:
        return CheckResult.fromBool(value instanceof TofuFunctionValue);
      case COMPUTED:
        return doIsInstance(type.getEffectiveType(), value);
      case LITERAL:
      case NAMESPACE:
      case PROTO_TYPE:
      case PROTO_ENUM_TYPE:
      case PROTO_EXTENSION:
      case TEMPLATE_TYPE:
      case NEVER:
        throw new UnsupportedOperationException();
    }
    throw new AssertionError("invalid type: " + type);
  }

  private static CheckResult isSanitizedofKind(SoyValue value, ContentKind kind) {
    return CheckResult.fromBool(
        value instanceof SanitizedContent && ((SanitizedContent) value).getContentKind() == kind);
  }
}
