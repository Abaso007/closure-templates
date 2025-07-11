/*
 * Copyright 2016 Google Inc.
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

package com.google.template.soy.types.ast;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;
import static com.google.template.soy.types.TemplateType.ParameterKind.ATTRIBUTE;
import static com.google.template.soy.types.TemplateType.ParameterKind.PARAM;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.NullNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.exprtree.UndefinedNode;
import com.google.template.soy.soyparse.SoyFileParser;
import com.google.template.soy.types.ast.RecordTypeNode.Property;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class TypeNodeTest {
  private static final SourceLocation SOURCE_LOCATION = SourceLocation.UNKNOWN;

  private static final NamedTypeNode TYPE_ABC = NamedTypeNode.create(SOURCE_LOCATION, "abc");
  private static final NamedTypeNode TYPE_DEF = NamedTypeNode.create(SOURCE_LOCATION, "def");
  private static final NamedTypeNode TYPE_GHI = NamedTypeNode.create(SOURCE_LOCATION, "ghi");
  private static final NamedTypeNode TYPE_JKL = NamedTypeNode.create(SOURCE_LOCATION, "jkl");

  @Test
  public void testNamedTypeToString() throws Exception {
    assertThat(TYPE_ABC.toString()).isEqualTo("abc");
  }

  @Test
  public void testGenericTypeToString() throws Exception {
    assertThat(
            GenericTypeNode.create(
                    SOURCE_LOCATION, Identifier.create("foo", SOURCE_LOCATION), ImmutableList.of())
                .toString())
        .isEqualTo("foo<>");
    assertThat(
            GenericTypeNode.create(
                    SOURCE_LOCATION,
                    Identifier.create("list", SOURCE_LOCATION),
                    ImmutableList.of(TYPE_ABC))
                .toString())
        .isEqualTo("list<abc>");
    assertThat(
            GenericTypeNode.create(
                    SOURCE_LOCATION,
                    Identifier.create("map", SOURCE_LOCATION),
                    ImmutableList.of(TYPE_ABC, TYPE_DEF))
                .toString())
        .isEqualTo("map<abc, def>");
  }

  @Test
  public void testRecordTypeToString() throws Exception {
    assertThat(RecordTypeNode.create(SOURCE_LOCATION, ImmutableList.of()).toString())
        .isEqualTo("[]");
    assertThat(
            RecordTypeNode.create(
                    SOURCE_LOCATION,
                    ImmutableList.of(Property.create(SOURCE_LOCATION, "x", false, TYPE_ABC)))
                .toString())
        .isEqualTo("[x: abc]");

    assertThat(
            RecordTypeNode.create(
                    SOURCE_LOCATION,
                    ImmutableList.of(
                        Property.create(SOURCE_LOCATION, "x", false, TYPE_ABC),
                        Property.create(SOURCE_LOCATION, "y", false, TYPE_DEF)))
                .toString())
        .isEqualTo("[x: abc, y: def]");

    assertThat(
            RecordTypeNode.create(
                    SOURCE_LOCATION,
                    ImmutableList.of(
                        Property.create(SOURCE_LOCATION, "x", false, TYPE_ABC),
                        Property.create(SOURCE_LOCATION, "y", false, TYPE_DEF),
                        Property.create(SOURCE_LOCATION, "z", false, TYPE_GHI),
                        Property.create(SOURCE_LOCATION, "w", true, TYPE_JKL)))
                .toString())
        .isEqualTo("[x: abc, y: def, z: ghi, w?: jkl]");
  }

  @Test
  public void testTemplateTypeToString() throws Exception {
    assertThat(
            TemplateTypeNode.create(
                    SOURCE_LOCATION,
                    ImmutableList.of(),
                    NamedTypeNode.create(SOURCE_LOCATION, "html"))
                .toString())
        .isEqualTo("template () => html");

    assertThat(
            TemplateTypeNode.create(
                    SOURCE_LOCATION,
                    ImmutableList.of(
                        TemplateTypeNode.Parameter.create(
                            SOURCE_LOCATION, "x", "x", PARAM, TYPE_ABC, false)),
                    NamedTypeNode.create(SOURCE_LOCATION, "attributes"))
                .toString())
        .isEqualTo("template (x?: abc) => attributes");

    assertThat(
            TemplateTypeNode.create(
                    SOURCE_LOCATION,
                    ImmutableList.of(
                        TemplateTypeNode.Parameter.create(
                            SOURCE_LOCATION, "x", "x", PARAM, TYPE_ABC, true),
                        TemplateTypeNode.Parameter.create(
                            SOURCE_LOCATION, "y", "y", PARAM, TYPE_DEF, true)),
                    NamedTypeNode.create(SOURCE_LOCATION, "css"))
                .toString())
        .isEqualTo("template (x: abc, y: def) => css");

    assertThat(
            TemplateTypeNode.create(
                    SOURCE_LOCATION,
                    ImmutableList.of(
                        TemplateTypeNode.Parameter.create(
                            SOURCE_LOCATION, "x", "x", PARAM, TYPE_ABC, true),
                        TemplateTypeNode.Parameter.create(
                            SOURCE_LOCATION, "y", "y", PARAM, TYPE_DEF, true),
                        TemplateTypeNode.Parameter.create(
                            SOURCE_LOCATION, "z", "z", PARAM, TYPE_GHI, true),
                        TemplateTypeNode.Parameter.create(
                            SOURCE_LOCATION, "w", "@w", ATTRIBUTE, TYPE_JKL, true)),
                    NamedTypeNode.create(SOURCE_LOCATION, "uri"))
                .toString())
        .isEqualTo("template (x: abc, y: def, z: ghi, @w: jkl) => uri");
  }

  @Test
  public void testUnionTypeToString() throws Exception {
    assertThat(UnionTypeNode.create(ImmutableList.of(TYPE_ABC, TYPE_DEF)).toString())
        .isEqualTo("abc|def");
  }

  @Test
  public void testRoundTripThroughParser() {
    assertRoundTrip("int");
    assertRoundTrip("?");
    assertRoundTrip("list<int>");
    assertRoundTrip("list<list<list<list<int>>>>");
    assertRoundTrip("map<int, any>");
    assertRoundTrip("[foo: string, bar: int, quux: [foo: string, bar: int, quux: list<any>]]");
    assertRoundTrip("template () => html");
    assertRoundTrip(
        "template (baz: int, tpl: template (foo: string, bar: int) => attributes) => html");
    assertRoundTrip("template (count: int) => html | template (count: int) => attributes");

    assertRoundTrip("A & (B | C) & D");
    assertRoundTrip("(A & B) | (C & D)");

    assertRoundTrip("() => string|null");
    assertRoundTrip("() => (string|null)");
    assertRoundTrip("(() => string)|null");
    assertRoundTrip("template () => string|null");
    assertRoundTrip("template () => (string|null)");
    assertRoundTrip("(template () => string)|null");

    assertRoundTrip("'prop1' | 'prop2'");
  }

  private void assertRoundTrip(String typeString) {
    TypeNode original = parse(typeString);
    TypeNode reparsed = parse(original.toString());
    // we can't assert on the equality of the type nodes because the source locations may be
    // different due to whitespace.
    assertThat(original.toString()).isEqualTo(reparsed.toString());
    assertEquals(original, reparsed);

    // Also assert equality after copying
    assertEquals(original, reparsed.copy(new CopyState()));
    CopyState state = new CopyState();
    assertEquals(original.copy(state), reparsed.copy(state));
    assertEquals(original.copy(new CopyState()), reparsed);
  }

  private static void assertEquals(
      ImmutableList<TypeNode> leftArgs, ImmutableList<TypeNode> rightArgs) {
    assertThat(leftArgs).hasSize(rightArgs.size());
    for (int i = 0; i < leftArgs.size(); i++) {
      assertEquals(leftArgs.get(i), rightArgs.get(i));
    }
  }

  static void assertEquals(TypeNode left, TypeNode right) {
    new TypeNodeVisitor<Void>() {

      @Override
      public Void visit(NamedTypeNode node) {
        assertThat(((NamedTypeNode) right).name().identifier()).isEqualTo(node.name().identifier());
        return null;
      }

      @Override
      public Void visit(IndexedTypeNode node) {
        assertEquals(node.type(), ((IndexedTypeNode) right).type());
        assertEquals(node.property(), ((IndexedTypeNode) right).property());
        return null;
      }

      @Override
      public Void visit(GenericTypeNode node) {
        assertThat(((GenericTypeNode) right).name()).isEqualTo(node.name());
        assertEquals(node.arguments(), ((GenericTypeNode) right).arguments());
        return null;
      }

      @Override
      public Void visit(UnionTypeNode node) {
        assertEquals(node.candidates(), ((UnionTypeNode) right).candidates());
        return null;
      }

      @Override
      public Void visit(IntersectionTypeNode node) {
        assertEquals(node.candidates(), ((IntersectionTypeNode) right).candidates());
        return null;
      }

      @Override
      public Void visit(RecordTypeNode node) {
        assertThat(node.properties()).hasSize(((RecordTypeNode) right).properties().size());
        for (int i = 0; i < node.properties().size(); i++) {
          Property leftProp = node.properties().get(i);
          Property rightProp = ((RecordTypeNode) right).properties().get(i);
          assertThat(leftProp.name()).isEqualTo(rightProp.name());
          assertEquals(leftProp.type(), rightProp.type());
        }
        return null;
      }

      @Override
      public Void visit(TemplateTypeNode node) {
        assertThat(node.parameters()).hasSize(((TemplateTypeNode) right).parameters().size());
        ImmutableMap<String, TypeNode> leftArgumentMap =
            node.parameters().stream()
                .collect(
                    toImmutableMap(
                        TemplateTypeNode.Parameter::name, TemplateTypeNode.Parameter::type));
        ImmutableMap<String, TypeNode> rightArgumentMap =
            ((TemplateTypeNode) right)
                .parameters().stream()
                    .collect(
                        toImmutableMap(
                            TemplateTypeNode.Parameter::name, TemplateTypeNode.Parameter::type));
        assertThat(leftArgumentMap.keySet()).isEqualTo(rightArgumentMap.keySet());
        for (String key : leftArgumentMap.keySet()) {
          assertEquals(leftArgumentMap.get(key), rightArgumentMap.get(key));
        }
        assertEquals(node.returnType(), ((TemplateTypeNode) right).returnType());
        return null;
      }

      @Override
      public Void visit(FunctionTypeNode node) {
        return null;
      }

      @Override
      public Void visit(LiteralTypeNode node) {
        ExprNode lhs = node.literal();
        ExprNode rhs = ((LiteralTypeNode) right).literal();
        if (lhs instanceof NullNode && rhs instanceof NullNode) {
          return null;
        }
        if (lhs instanceof UndefinedNode && rhs instanceof UndefinedNode) {
          return null;
        }
        if (lhs instanceof StringNode && rhs instanceof StringNode) {
          assertThat(((StringNode) lhs).getValue()).isEqualTo(((StringNode) rhs).getValue());
          return null;
        }
        assert_().fail();
        return null;
      }
    }.exec(left);
  }

  private TypeNode parse(String typeString) {
    TypeNode typeNode =
        SoyFileParser.parseType(
            typeString, SourceFilePath.forTest("fake-file.soy"), ErrorReporter.exploding());
    // sanity, make sure copies work
    assertEquals(typeNode, typeNode.copy(new CopyState()));
    return typeNode;
  }
}
