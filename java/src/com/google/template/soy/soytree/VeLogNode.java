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

package com.google.template.soy.soytree;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.ExprEquivalence;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.soytree.CommandTagAttribute.CommandTagAttributesHolder;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.MsgBlockNode;
import com.google.template.soy.soytree.SoyNode.StatementNode;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Node for a <code {@literal {}velog...}</code> statement.
 */
public final class VeLogNode extends AbstractBlockCommandNode
    implements ExprHolderNode, StatementNode, MsgBlockNode, CommandTagAttributesHolder {

  /**
   * An equivalence key for comparing {@link VeLogNode} instances.
   *
   * <p>This ignores things like {@link SoyNode#getId()} and {@link SoyNode#getSourceLocation()} and
   * is useful for deciding placeholder equivalence for velog nodes in messages.
   */
  static final class SamenessKey {
    private VeLogNode delegate;

    private SamenessKey(VeLogNode delegate) {
      this.delegate = delegate;
    }

    private SamenessKey(SamenessKey orig, CopyState copyState) {
      // store the original, this may still be valid if we are only copying a subtree
      this.delegate = orig.delegate;
      copyState.registerRefListener(orig.delegate, newDelegate -> this.delegate = newDelegate);
    }

    SamenessKey copy(CopyState copyState) {
      return new SamenessKey(this, copyState);
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof SamenessKey)) {
        return false;
      }
      ExprEquivalence exprEquivalence = new ExprEquivalence();

      SamenessKey otherKey = (SamenessKey) other;
      return exprEquivalence.equivalent(delegate.veDataExpr, otherKey.delegate.veDataExpr)
          && exprEquivalence.equivalent(delegate.logonlyExpr, otherKey.delegate.logonlyExpr);
    }

    @Override
    public int hashCode() {
      return new ExprEquivalence().hash(Arrays.asList(delegate.veDataExpr, delegate.logonlyExpr));
    }
  }

  private final ExprRootNode veDataExpr;
  private boolean needsSyntheticVelogNode = false;
  private final List<CommandTagAttribute> attributes;
  @Nullable private final ExprRootNode logonlyExpr;

  public VeLogNode(
      int id,
      SourceLocation location,
      SourceLocation openTagLocation,
      ExprNode veDataExpr,
      List<CommandTagAttribute> attributes,
      ErrorReporter errorReporter) {
    super(id, location, openTagLocation, "velog");
    this.veDataExpr = new ExprRootNode(checkNotNull(veDataExpr));
    ExprRootNode logonlyExpr = null;
    this.attributes = attributes;
    this.logonlyExpr = getLogonlyExpr(attributes, errorReporter);
  }

  private VeLogNode(VeLogNode orig, CopyState copyState) {
    super(orig, copyState);
    this.veDataExpr = orig.veDataExpr.copy(copyState);
    this.attributes =
        orig.attributes.stream().map(c -> c.copy(copyState)).collect(toImmutableList());
    this.logonlyExpr = getLogonlyExpr(this.attributes, ErrorReporter.exploding());
    this.needsSyntheticVelogNode = orig.needsSyntheticVelogNode;
    // See SamenessKey copy constructor
    copyState.updateRefs(orig, this);
  }

  @Nullable
  private static final ExprRootNode getLogonlyExpr(
      List<CommandTagAttribute> attributes, ErrorReporter errorReporter) {
    for (CommandTagAttribute attr : attributes) {
      switch (attr.getName().identifier()) {
        case "logonly":
          return attr.valueAsExpr(errorReporter);
        default:
          errorReporter.report(
              attr.getName().location(),
              CommandTagAttribute.UNSUPPORTED_ATTRIBUTE_KEY,
              attr.getName().identifier(),
              "velog",
              ImmutableList.of("logonly"));
      }
    }
    return null;
  }

  @Override
  public List<CommandTagAttribute> getAttributes() {
    return this.attributes;
  }

  SamenessKey getSamenessKey() {
    return new SamenessKey(this);
  }

  public void setNeedsSyntheticVelogNode(boolean needsSyntheticVelogNode) {
    this.needsSyntheticVelogNode = needsSyntheticVelogNode;
  }

  public boolean needsSyntheticVelogNode() {
    return needsSyntheticVelogNode;
  }

  /** Returns a reference to the VE expression. */
  public ExprRootNode getVeDataExpression() {
    return veDataExpr;
  }

  /** Returns a reference to the logonly expression, if there is one. */
  @Nullable
  public ExprRootNode getLogonlyExpression() {
    return logonlyExpr;
  }

  @Override
  public Kind getKind() {
    return Kind.VE_LOG_NODE;
  }

  /** Returns the open tag node if it exists. */
  @Nullable
  public HtmlOpenTagNode getOpenTagNode() {
    if (numChildren() > 0) {
      return (HtmlOpenTagNode) getNodeAsHtmlTagNode(getChild(0), /* openTag= */ true);
    }
    return null;
  }

  /** Returns the close tag node if it exists. */
  @Nullable
  public HtmlCloseTagNode getCloseTagNode() {
    if (numChildren() > 1) {
      return (HtmlCloseTagNode)
          getNodeAsHtmlTagNode(getChild(numChildren() - 1), /* openTag= */ false);
    }
    return null;
  }

  @Override
  public String getCommandText() {
    return veDataExpr.toSourceString()
        + (logonlyExpr != null ? " logonly=\"" + logonlyExpr.toSourceString() + "\"" : "");
  }

  @Override
  @SuppressWarnings("unchecked")
  public ParentSoyNode<StandaloneNode> getParent() {
    return (ParentSoyNode<StandaloneNode>) super.getParent();
  }

  @Override
  public VeLogNode copy(CopyState copyState) {
    return new VeLogNode(this, copyState);
  }

  @Override
  public ImmutableList<ExprRootNode> getExprList() {
    ImmutableList.Builder<ExprRootNode> builder = ImmutableList.builder();
    builder.add(veDataExpr);
    if (logonlyExpr != null) {
      builder.add(logonlyExpr);
    }
    return builder.build();
  }

  @Override
  public String toSourceString() {
    StringBuilder sb = new StringBuilder();
    sb.append(getTagString());
    appendSourceStringForChildren(sb);
    sb.append("{/velog}");
    return sb.toString();
  }

  /**
   * Returns the node as an HTML tag node, if one can be extracted from it (e.g. wrapped in a
   * MsgPlaceholderNode). Otherwise, returns null.
   */
  @Nullable
  private static HtmlTagNode getNodeAsHtmlTagNode(StandaloneNode node, boolean openTag) {
    if (node == null) {
      return null;
    }
    if (!node.isRendered()) {
      return getNodeAsHtmlTagNode((StandaloneNode) SoyTreeUtils.nextSibling(node), openTag);
    }
    Kind tagKind = openTag ? Kind.HTML_OPEN_TAG_NODE : Kind.HTML_CLOSE_TAG_NODE;
    if (node.getKind() == tagKind) {
      return (HtmlTagNode) node;
    }
    // In a msg tag it will be a placeholder, wrapping a MsgHtmlTagNode wrapping the HtmlTagNode.
    if (node.getKind() == Kind.MSG_PLACEHOLDER_NODE) {
      MsgPlaceholderNode placeholderNode = (MsgPlaceholderNode) node;
      if (placeholderNode.numChildren() == 1
          && placeholderNode.getChild(0).getKind() == Kind.MSG_HTML_TAG_NODE) {
        MsgHtmlTagNode msgHtmlTagNode = (MsgHtmlTagNode) placeholderNode.getChild(0);
        if (msgHtmlTagNode.numChildren() == 1 && msgHtmlTagNode.getChild(0).getKind() == tagKind) {
          return (HtmlTagNode) msgHtmlTagNode.getChild(0);
        }
      }
    }
    return null;
  }
}
